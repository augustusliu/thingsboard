package org.thingsboard.server.common.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.device.data.DeviceData;
import org.thingsboard.server.common.data.device.profile.DeviceProfileData;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.TenantId;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * <pre>
 *     HasName 表示设备是否存在设备名称
 *
 *     HasTenantId表示设备是否属于某个租户
 *
 *     HasCustomerId表示设备是否属于租户下的某个客户
 * </pre>
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class Device extends SearchTextBasedWithAdditionalInfo<DeviceId> implements HasName, HasTenantId, HasCustomerId {

    private static final long serialVersionUID = 2807343040519543363L;

    // 租户ID
    private TenantId tenantId;
    // 租户下的某个客户ID，拥有权
    private CustomerId customerId;
    // 设备名称
    private String name;
    // 设备类型
    private String type;
    // 设备标签
    private String label;
    // 设备画像
    private DeviceProfileId deviceProfileId;
    /**
     * 设备画像配置 及 设备传输层配置
     */
    private transient DeviceData deviceData;
    @JsonIgnore
    private byte[] deviceDataBytes;

    public Device() {
        super();
    }

    public Device(DeviceId id) {
        super(id);
    }

    public Device(Device device) {
        super(device);
        this.tenantId = device.getTenantId();
        this.customerId = device.getCustomerId();
        this.name = device.getName();
        this.type = device.getType();
        this.label = device.getLabel();
        this.deviceProfileId = device.getDeviceProfileId();
        this.setDeviceData(device.getDeviceData());
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public void setTenantId(TenantId tenantId) {
        this.tenantId = tenantId;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public void setCustomerId(CustomerId customerId) {
        this.customerId = customerId;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public DeviceProfileId getDeviceProfileId() {
        return deviceProfileId;
    }

    public void setDeviceProfileId(DeviceProfileId deviceProfileId) {
        this.deviceProfileId = deviceProfileId;
    }

    public DeviceData getDeviceData() {
        if (deviceData != null) {
            return deviceData;
        } else {
            if (deviceDataBytes != null) {
                try {
                    deviceData = mapper.readValue(new ByteArrayInputStream(deviceDataBytes), DeviceData.class);
                } catch (IOException e) {
                    log.warn("Can't deserialize device data: ", e);
                    return null;
                }
                return deviceData;
            } else {
                return null;
            }
        }
    }

    public void setDeviceData(DeviceData data) {
        this.deviceData = data;
        try {
            this.deviceDataBytes = data != null ? mapper.writeValueAsBytes(data) : null;
        } catch (JsonProcessingException e) {
            log.warn("Can't serialize device data: ", e);
        }
    }

    @Override
    public String getSearchText() {
        return getName();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Device [tenantId=");
        builder.append(tenantId);
        builder.append(", customerId=");
        builder.append(customerId);
        builder.append(", name=");
        builder.append(name);
        builder.append(", type=");
        builder.append(type);
        builder.append(", label=");
        builder.append(label);
        builder.append(", deviceProfileId=");
        builder.append(deviceProfileId);
        builder.append(", deviceData=");
        builder.append(deviceData);
        builder.append(", additionalInfo=");
        builder.append(getAdditionalInfo());
        builder.append(", createdTime=");
        builder.append(createdTime);
        builder.append(", id=");
        builder.append(id);
        builder.append("]");
        return builder.toString();
    }

}
