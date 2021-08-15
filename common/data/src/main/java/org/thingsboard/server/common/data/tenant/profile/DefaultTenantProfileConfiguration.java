package org.thingsboard.server.common.data.tenant.profile;

import lombok.Data;
import org.thingsboard.server.common.data.ApiUsageRecordKey;
import org.thingsboard.server.common.data.TenantProfileType;

/**
 * 租户配置信息
 */
@Data
public class DefaultTenantProfileConfiguration implements TenantProfileConfiguration {

    /**
     * 租户最大的设备数
     */
    private long maxDevices;
    /**
     * 租户最大的资产数量
     */
    private long maxAssets;
    /**
     * 租户最大的客户数量
     */
    private long maxCustomers;
    /**
     * 租户最大的用户数量
     */
    private long maxUsers;
    /**
     * 租户最大的看板数量
     */
    private long maxDashboards;

    /**
     * 租户最大的规则数量
     */
    private long maxRuleChains;

    /**
     * 租户传输层限流
     */
    private String transportTenantMsgRateLimit;
    /**
     * 租户遥测频率限流
     */
    private String transportTenantTelemetryMsgRateLimit;
    /**
     * 租户遥测数据点限流
     */
    private String transportTenantTelemetryDataPointsRateLimit;
    /**
     * 租户传输层设备发送消息限流
     */
    private String transportDeviceMsgRateLimit;

    /**
     * 租户传输层遥测数据限流
     */
    private String transportDeviceTelemetryMsgRateLimit;
    private String transportDeviceTelemetryDataPointsRateLimit;

    /**
     * 传输层最大发送消息数据量
     */
    private long maxTransportMessages;
    private long maxTransportDataPoints;
    private long maxREExecutions;
    private long maxJSExecutions;
    private long maxDPStorageDays;
    private int maxRuleNodeExecutionsPerMessage;
    private long maxEmails;
    private long maxSms;

    private int defaultStorageTtlDays;

    private double warnThreshold;

    @Override
    public long getProfileThreshold(ApiUsageRecordKey key) {
        switch (key) {
            case TRANSPORT_MSG_COUNT:
                return maxTransportMessages;
            case TRANSPORT_DP_COUNT:
                return maxTransportDataPoints;
            case JS_EXEC_COUNT:
                return maxJSExecutions;
            case RE_EXEC_COUNT:
                return maxREExecutions;
            case STORAGE_DP_COUNT:
                return maxDPStorageDays;
            case EMAIL_EXEC_COUNT:
                return maxEmails;
            case SMS_EXEC_COUNT:
                return maxSms;
        }
        return 0L;
    }

    @Override
    public long getWarnThreshold(ApiUsageRecordKey key) {
        return (long) (getProfileThreshold(key) * (warnThreshold > 0.0 ? warnThreshold : 0.8));
    }

    @Override
    public TenantProfileType getType() {
        return TenantProfileType.DEFAULT;
    }

    @Override
    public int getMaxRuleNodeExecsPerMessage() {
        return maxRuleNodeExecutionsPerMessage;
    }
}
