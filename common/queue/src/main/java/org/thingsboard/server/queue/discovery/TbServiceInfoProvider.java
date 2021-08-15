package org.thingsboard.server.queue.discovery;

import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.gen.transport.TransportProtos.ServiceInfo;

import java.util.Optional;

/**
 * ********************************很重要的一个类********************************
 * 业务提供商：
 * 目前包含如下四种业务：TB_CORE, TB_RULE_ENGINE, TB_TRANSPORT, JS_EXECUTOR， monolith
 * monolith 表示一个集成服务，包含以上所有服务
 *
 * <pre>
 *     1、tb中，一个实例可以提供多个以上的服务，也可以是单独的一个。
 *     2、一个服务或者一个实例也可以单独提供个某个租户使用。
 * </pre>
 *
 */
public interface TbServiceInfoProvider {

    /**
     *  1、获取该服务的ID, 启动时基于配置生成
     *  2、如果未配置，则使用当前节点的ip
     *  3、如果以上两者都没有，则随机生成一个10位码
     */
    String getServiceId();

    /**
     * 获取该服务的具体信息，用于服务发现
     * 包括服务ID、服务类型(可以多个)、租户ID、使用的消息对列列表
     */
    ServiceInfo getServiceInfo();

    /**
     * 判断当前服务类型是否包含某个服务类型
     */
    boolean isService(ServiceType serviceType);

    /**
     * 如果当前实例被某个租户独占，则返回当前租户的ID
     */
    Optional<TenantId> getIsolatedTenant();

}
