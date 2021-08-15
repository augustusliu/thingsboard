package org.thingsboard.server.common.data.security;

/**
 * 角色
 */
public enum Authority {
    // 系统管理员
    SYS_ADMIN(0),
    // 租户管理员 ---- 公司
    TENANT_ADMIN(1),
    // 客户管理员 --- 公司下的客户公司或者子公司
    CUSTOMER_USER(2),
    // 刷新token
    REFRESH_TOKEN(10);

    private int code;

    Authority(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Authority parse(String value) {
        Authority authority = null;
        if (value != null && value.length() != 0) {
            for (Authority current : Authority.values()) {
                if (current.name().equalsIgnoreCase(value)) {
                    authority = current;
                    break;
                }
            }
        }
        return authority;
    }
}
