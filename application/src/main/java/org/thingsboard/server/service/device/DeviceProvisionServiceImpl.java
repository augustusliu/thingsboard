package org.thingsboard.server.service.device;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.audit.AuditLogService;
import org.thingsboard.server.dao.device.DeviceCredentialsService;
import org.thingsboard.server.dao.device.DeviceDao;
import org.thingsboard.server.dao.device.DeviceProfileDao;
import org.thingsboard.server.dao.device.DeviceProvisionService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.device.provision.ProvisionFailedException;
import org.thingsboard.server.dao.device.provision.ProvisionRequest;
import org.thingsboard.server.dao.device.provision.ProvisionResponse;
import org.thingsboard.server.dao.device.provision.ProvisionResponseStatus;
import org.thingsboard.server.dao.util.mapping.JacksonUtil;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.ToRuleEngineMsg;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.queue.provider.TbQueueProducerProvider;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.state.DeviceStateService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
@TbCoreComponent
public class DeviceProvisionServiceImpl implements DeviceProvisionService {

    protected TbQueueProducer<TbProtoQueueMsg<ToRuleEngineMsg>> ruleEngineMsgProducer;

    private static final String DEVICE_PROVISION_STATE = "provisionState";
    private static final String PROVISIONED_STATE = "provisioned";

    private final ReentrantLock deviceCreationLock = new ReentrantLock();

    @Autowired
    DeviceDao deviceDao;

    @Autowired
    DeviceProfileDao deviceProfileDao;

    @Autowired
    DeviceService deviceService;

    @Autowired
    DeviceCredentialsService deviceCredentialsService;

    @Autowired
    AttributesService attributesService;

    @Autowired
    DeviceStateService deviceStateService;

    @Autowired
    AuditLogService auditLogService;

    @Autowired
    PartitionService partitionService;

    public DeviceProvisionServiceImpl(TbQueueProducerProvider producerProvider) {
        ruleEngineMsgProducer = producerProvider.getRuleEngineMsgProducer();
    }

    @Override
    public ProvisionResponse provisionDevice(ProvisionRequest provisionRequest) {
        String provisionRequestKey = provisionRequest.getCredentials().getProvisionDeviceKey();
        String provisionRequestSecret = provisionRequest.getCredentials().getProvisionDeviceSecret();
        if (!StringUtils.isEmpty(provisionRequest.getDeviceName())) {
            provisionRequest.setDeviceName(provisionRequest.getDeviceName().trim());
            if (StringUtils.isEmpty(provisionRequest.getDeviceName())) {
                log.warn("Provision request contains empty device name!");
                throw new ProvisionFailedException(ProvisionResponseStatus.FAILURE.name());
            }
        }

        if (StringUtils.isEmpty(provisionRequestKey) || StringUtils.isEmpty(provisionRequestSecret)) {
            throw new ProvisionFailedException(ProvisionResponseStatus.NOT_FOUND.name());
        }
        // 根据设备key获取设备画像
        DeviceProfile targetProfile = deviceProfileDao.findByProvisionDeviceKey(provisionRequestKey);


        if (targetProfile == null || targetProfile.getProfileData().getProvisionConfiguration() == null ||
                targetProfile.getProfileData().getProvisionConfiguration().getProvisionDeviceSecret() == null) {
            throw new ProvisionFailedException(ProvisionResponseStatus.NOT_FOUND.name());
        }

        // 根据租户和设备名称获取设备信息
        Device targetDevice = deviceDao.findDeviceByTenantIdAndName(targetProfile.getTenantId().getId(), provisionRequest.getDeviceName()).orElse(null);

        // 判断创建设备的类型
        switch (targetProfile.getProvisionType()) {
            case ALLOW_CREATE_NEW_DEVICES:  // 创建新设备
                // 设备画像中包含了设备的密码
                if (targetProfile.getProfileData().getProvisionConfiguration().getProvisionDeviceSecret().equals(provisionRequestSecret)) {
                    // 设备存在则触发通知异常;否则创建设备
                    if (targetDevice != null) {
                        log.warn("[{}] The device is present and could not be provisioned once more!", targetDevice.getName());

                        // 通知规则引擎
                        notify(targetDevice, provisionRequest, DataConstants.PROVISION_FAILURE, false);
                        throw new ProvisionFailedException(ProvisionResponseStatus.FAILURE.name());
                    } else {
                        return createDevice(provisionRequest, targetProfile);
                    }
                }
                break;
            case CHECK_PRE_PROVISIONED_DEVICES: // 检查之前所有设备
                if (targetProfile.getProfileData().getProvisionConfiguration().getProvisionDeviceSecret().equals(provisionRequestSecret)) {
                    if (targetDevice != null && targetDevice.getDeviceProfileId().equals(targetProfile.getId())) {
                        return processProvision(targetDevice, provisionRequest);
                    } else {
                        log.warn("[{}] Failed to find pre provisioned device!", provisionRequest.getDeviceName());
                        throw new ProvisionFailedException(ProvisionResponseStatus.FAILURE.name());
                    }
                }
                break;
        }
        throw new ProvisionFailedException(ProvisionResponseStatus.NOT_FOUND.name());
    }

    private ProvisionResponse processProvision(Device device, ProvisionRequest provisionRequest) {
        try {
            Optional<AttributeKvEntry> provisionState = attributesService.find(device.getTenantId(), device.getId(),
                    DataConstants.SERVER_SCOPE, DEVICE_PROVISION_STATE).get();
            if (provisionState != null && provisionState.isPresent() && !provisionState.get().getValueAsString().equals(PROVISIONED_STATE)) {
                notify(device, provisionRequest, DataConstants.PROVISION_FAILURE, false);
                throw new ProvisionFailedException(ProvisionResponseStatus.FAILURE.name());
            } else {
                saveProvisionStateAttribute(device).get();
                notify(device, provisionRequest, DataConstants.PROVISION_SUCCESS, true);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new ProvisionFailedException(ProvisionResponseStatus.FAILURE.name());
        }
        return new ProvisionResponse(deviceCredentialsService.findDeviceCredentialsByDeviceId(device.getTenantId(), device.getId()), ProvisionResponseStatus.SUCCESS);
    }

    private ProvisionResponse createDevice(ProvisionRequest provisionRequest, DeviceProfile profile) {
        deviceCreationLock.lock();
        try {
            return processCreateDevice(provisionRequest, profile);
        } finally {
            deviceCreationLock.unlock();
        }
    }

    private void notify(Device device, ProvisionRequest provisionRequest, String type, boolean success) {
        pushProvisionEventToRuleEngine(provisionRequest, device, type);
        logAction(device.getTenantId(), device.getCustomerId(), device, success, provisionRequest);
    }

    private ProvisionResponse processCreateDevice(ProvisionRequest provisionRequest, DeviceProfile profile) {
        Device device = deviceService.findDeviceByTenantIdAndName(profile.getTenantId(), provisionRequest.getDeviceName());
        try {
            if (device == null) {
                if (StringUtils.isEmpty(provisionRequest.getDeviceName())) {
                    // 如果设备名称为空-随机创建设备的名称
                    String newDeviceName = RandomStringUtils.randomAlphanumeric(20);
                    log.info("Device name not found in provision request. Generated name is: {}", newDeviceName);
                    provisionRequest.setDeviceName(newDeviceName);
                }
                // 保存设备信息和密码，如果设备的画像不存在则绑定到默认画像上
                Device savedDevice = deviceService.saveDevice(provisionRequest, profile);
                // 设备保存后发送集群消息通知
                deviceStateService.onDeviceAdded(savedDevice);
                // 保存物模型
                saveProvisionStateAttribute(savedDevice).get();
                // 发送消息到规则引擎
                pushDeviceCreatedEventToRuleEngine(savedDevice);

                notify(savedDevice, provisionRequest, DataConstants.PROVISION_SUCCESS, true);

                return new ProvisionResponse(getDeviceCredentials(savedDevice), ProvisionResponseStatus.SUCCESS);
            } else {
                log.warn("[{}] The device is already provisioned!", device.getName());
                notify(device, provisionRequest, DataConstants.PROVISION_FAILURE, false);
                throw new ProvisionFailedException(ProvisionResponseStatus.FAILURE.name());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new ProvisionFailedException(ProvisionResponseStatus.FAILURE.name());
        }
    }

    private ListenableFuture<List<Void>> saveProvisionStateAttribute(Device device) {
        return attributesService.save(device.getTenantId(), device.getId(), DataConstants.SERVER_SCOPE,
                Collections.singletonList(new BaseAttributeKvEntry(new StringDataEntry(DEVICE_PROVISION_STATE, PROVISIONED_STATE),
                        System.currentTimeMillis())));
    }

    private DeviceCredentials getDeviceCredentials(Device device) {
        return deviceCredentialsService.findDeviceCredentialsByDeviceId(device.getTenantId(), device.getId());
    }

    private void pushProvisionEventToRuleEngine(ProvisionRequest request, Device device, String type) {
        try {
            JsonNode entityNode = JacksonUtil.valueToTree(request);
            TbMsg msg = TbMsg.newMsg(type, device.getId(), createTbMsgMetaData(device), JacksonUtil.toString(entityNode));
            sendToRuleEngine(device.getTenantId(), msg, null);
        } catch (IllegalArgumentException e) {
            log.warn("[{}] Failed to push device action to rule engine: {}", device.getId(), type, e);
        }
    }

    private void pushDeviceCreatedEventToRuleEngine(Device device) {
        try {
            ObjectNode entityNode = JacksonUtil.OBJECT_MAPPER.valueToTree(device);
            TbMsg msg = TbMsg.newMsg(DataConstants.ENTITY_CREATED, device.getId(), createTbMsgMetaData(device), JacksonUtil.OBJECT_MAPPER.writeValueAsString(entityNode));
            sendToRuleEngine(device.getTenantId(), msg, null);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("[{}] Failed to push device action to rule engine: {}", device.getId(), DataConstants.ENTITY_CREATED, e);
        }
    }

    protected void sendToRuleEngine(TenantId tenantId, TbMsg tbMsg, TbQueueCallback callback) {
        TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_RULE_ENGINE, tenantId, tbMsg.getOriginator());
        TransportProtos.ToRuleEngineMsg msg = TransportProtos.ToRuleEngineMsg.newBuilder().setTbMsg(TbMsg.toByteString(tbMsg))
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits()).build();
        ruleEngineMsgProducer.send(tpi, new TbProtoQueueMsg<>(tbMsg.getId(), msg), callback);
    }

    private TbMsgMetaData createTbMsgMetaData(Device device) {
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("tenantId", device.getTenantId().toString());
        return metaData;
    }

    private void logAction(TenantId tenantId, CustomerId customerId, Device device, boolean success, ProvisionRequest provisionRequest) {
        ActionType actionType = success ? ActionType.PROVISION_SUCCESS : ActionType.PROVISION_FAILURE;
        auditLogService.logEntityAction(tenantId, customerId, new UserId(UserId.NULL_UUID), device.getName(), device.getId(), device, actionType, null, provisionRequest);
    }
}
