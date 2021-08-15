package org.thingsboard.server.queue.discovery;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.ServiceInfo;
import org.thingsboard.server.queue.settings.TbQueueRuleEngineSettings;
import org.thingsboard.server.queue.settings.TbRuleEngineQueueConfiguration;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 服务类型
 * 因为使用消息对列的类型有很多，不同的服务使用消息对列传递的消息都不同
 *
 * 所以这里专门提供了服务生产商，根据不同的服务生产商，可以针对性的定制不同的消息
 */
@Component
@Slf4j
public class DefaultTbServiceInfoProvider implements TbServiceInfoProvider {

    @Getter
    @Value("${service.id:#{null}}")
    private String serviceId;

    @Getter
    @Value("${service.type:monolith}")
    private String serviceType;

    // 当前服务所属于哪些租户
    @Getter
    @Value("${service.tenant_id:}")
    private String tenantIdStr;

    @Autowired(required = false)
    private TbQueueRuleEngineSettings ruleEngineSettings;

    private List<ServiceType> serviceTypes;
    private ServiceInfo serviceInfo;
    private TenantId isolatedTenant;

    @PostConstruct
    public void init() {
        if (StringUtils.isEmpty(serviceId)) {
            try {
                serviceId = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                serviceId = org.apache.commons.lang3.RandomStringUtils.randomAlphabetic(10);
            }
        }
        log.info("TbServiceInfoProvider#启动，Current Service ID: {}", serviceId);

        if (serviceType.equalsIgnoreCase("monolith")) {
            // monolith表示全部的服务
            log.info("启动集成服务[serviceType = monolith], 服务列表:{}", new Gson().toJson(ServiceType.values()));
            serviceTypes = Collections.unmodifiableList(Arrays.asList(ServiceType.values()));
        } else {
            log.info("启动独立服务[serviceType = {}]", serviceType);
            serviceTypes = Collections.singletonList(ServiceType.of(serviceType));
        }

        ServiceInfo.Builder builder = ServiceInfo.newBuilder().setServiceId(serviceId)
                .addAllServiceTypes(serviceTypes.stream().map(ServiceType::name).collect(Collectors.toList()));

        UUID tenantId;
        if (!StringUtils.isEmpty(tenantIdStr)) {
            tenantId = UUID.fromString(tenantIdStr);
            isolatedTenant = new TenantId(tenantId);
        } else {
            tenantId = TenantId.NULL_UUID;
        }
        //返回此 uuid 的 128 位值中的最高有效 64 位和最低64位
        builder.setTenantIdMSB(tenantId.getMostSignificantBits());
        builder.setTenantIdLSB(tenantId.getLeastSignificantBits());

        //ruleEngineSettings是一个TbQueueRuleEngineSettings的一个实例，读取queue.rule-engine下的值
        //ruleEngineSettings包含topic是tb_rule_engine，queue队列有三个分别是②:
        // 1. name: Main topic: tb_rule_engine.main partition: 10
        // 2. name: HighPriority topic: tb_rule_engine.hp partition: 10
        // 3. name: SequentialByOriginator topic: tb_rule_engine.sq partition: 10

        // 判断当前服务是否为规则引擎服务，如果是规则引擎服务，保存当前规则引擎所使用的所有的对列，可以是不同类型的消息对列
        if (serviceTypes.contains(ServiceType.TB_RULE_ENGINE) && ruleEngineSettings != null) {
            for (TbRuleEngineQueueConfiguration queue : ruleEngineSettings.getQueues()) {
                TransportProtos.QueueInfo queueInfo = TransportProtos.QueueInfo.newBuilder()
                        .setName(queue.getName())
                        .setTopic(queue.getTopic())
                        .setPartitions(queue.getPartitions()).build();
                builder.addRuleEngineQueues(queueInfo);
            }
        }

        serviceInfo = builder.build();
    }

    @Override
    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }

    @Override
    public boolean isService(ServiceType serviceType) {
        return serviceTypes.contains(serviceType);
    }

    @Override
    public Optional<TenantId> getIsolatedTenant() {
        return Optional.ofNullable(isolatedTenant);
    }
}
