package org.thingsboard.server.common.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.id.TenantProfileId;
import org.thingsboard.server.common.data.tenant.profile.DefaultTenantProfileConfiguration;
import org.thingsboard.server.common.data.tenant.profile.TenantProfileData;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.thingsboard.server.common.data.SearchTextBasedWithAdditionalInfo.mapper;

@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class TenantProfile extends SearchTextBased<TenantProfileId> implements HasName {

    /**
     * 租户名称
     */
    private String name;
    /**
     * 租户描述
     */
    private String description;

    /**
     * 是否采用默认的租户限流配置
     */
    private boolean isDefault;

    /**
     * 是否为独立的tbCore业务
     */
    private boolean isolatedTbCore;

    /**
     * 是否为独立的规则引擎
     */
    private boolean isolatedTbRuleEngine;
    /**
     * 租户相关限流配置信息
     */
    private transient TenantProfileData profileData;
    @JsonIgnore
    private byte[] profileDataBytes;

    public TenantProfile() {
        super();
    }

    public TenantProfile(TenantProfileId tenantProfileId) {
        super(tenantProfileId);
    }

    public TenantProfile(TenantProfile tenantProfile) {
        super(tenantProfile);
        this.name = tenantProfile.getName();
        this.description = tenantProfile.getDescription();
        this.isDefault = tenantProfile.isDefault();
        this.isolatedTbCore = tenantProfile.isIsolatedTbCore();
        this.isolatedTbRuleEngine = tenantProfile.isIsolatedTbRuleEngine();
        this.setProfileData(tenantProfile.getProfileData());
    }

    @Override
    public String getSearchText() {
        return getName();
    }

    @Override
    public String getName() {
        return name;
    }

    public TenantProfileData getProfileData() {
        if (profileData != null) {
            return profileData;
        } else {
            if (profileDataBytes != null) {
                try {
                    profileData = mapper.readValue(new ByteArrayInputStream(profileDataBytes), TenantProfileData.class);
                } catch (IOException e) {
                    log.warn("Can't deserialize tenant profile data: ", e);
                    return createDefaultTenantProfileData();
                }
                return profileData;
            } else {
                return createDefaultTenantProfileData();
            }
        }
    }

    public TenantProfileData createDefaultTenantProfileData() {
        TenantProfileData tpd = new TenantProfileData();
        tpd.setConfiguration(new DefaultTenantProfileConfiguration());
        return tpd;
    }

    public void setProfileData(TenantProfileData data) {
        this.profileData = data;
        try {
            this.profileDataBytes = data != null ? mapper.writeValueAsBytes(data) : null;
        } catch (JsonProcessingException e) {
            log.warn("Can't serialize tenant profile data: ", e);
        }
    }

}
