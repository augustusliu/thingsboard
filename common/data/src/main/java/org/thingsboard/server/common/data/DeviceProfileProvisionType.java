package org.thingsboard.server.common.data;

/**
 * 是否可以创建设备
 */
public enum DeviceProfileProvisionType {
    // 禁止
    DISABLED,
    // 允许设备创建新的设备
    ALLOW_CREATE_NEW_DEVICES,
    // 检查之前已经提供的设备
    CHECK_PRE_PROVISIONED_DEVICES
}
