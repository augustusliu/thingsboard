package org.thingsboard.server.common.transport.auth;

import lombok.Data;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.TenantId;

@Data
public class TransportDeviceInfo {

    // 租户ID
    private TenantId tenantId;
//    设备画像(配置)id,
    private DeviceProfileId deviceProfileId;
//    设备ID
    private DeviceId deviceId;
//    设备名称
    private String deviceName;
//    设备类型
    private String deviceType;
//    额外信息
    private String additionalInfo;

}
