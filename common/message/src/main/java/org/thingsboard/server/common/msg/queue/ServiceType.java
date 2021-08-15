package org.thingsboard.server.common.msg.queue;

/**
 * 当前微服务的种类：
 *
 * 该项目可以单独启动如下服务
 */
public enum ServiceType {

    TB_CORE, TB_RULE_ENGINE, TB_TRANSPORT, JS_EXECUTOR;

    public static ServiceType of(String serviceType) {
        return ServiceType.valueOf(serviceType.replace("-", "_").toUpperCase());
    }
}
