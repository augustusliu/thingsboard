package org.thingsboard.server.queue.provider;

import org.thingsboard.server.gen.transport.TransportProtos.ToCoreMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToCoreNotificationMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToRuleEngineMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToRuleEngineNotificationMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToTransportMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToUsageStatsServiceMsg;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;

/**
 * Responsible for providing various Producers to other services.
 * 根据不同的服务类型，创建不同的消息生产者。每个生产者的消息格式不同
 * 服务类型包括 传出层的消息(transport)，规则引擎的消息(ruleEngine)，其他核心消息(tbCore)，规则引擎通知消息、核心消息通知消息、状态消息
 */
public interface TbQueueProducerProvider {

    /**
     * Used to push messages to instances of TB Transport Service
     * 获取transport消息生产者
     */
    TbQueueProducer<TbProtoQueueMsg<ToTransportMsg>> getTransportNotificationsMsgProducer();

    /**
     * Used to push messages to instances of TB RuleEngine Service
     * 获取规则引擎消息生产者
     */
    TbQueueProducer<TbProtoQueueMsg<ToRuleEngineMsg>> getRuleEngineMsgProducer();

    /**
     * Used to push notifications to instances of TB RuleEngine Service
     * 获取规则引擎通知类型消息的生产者
     */
    TbQueueProducer<TbProtoQueueMsg<ToRuleEngineNotificationMsg>> getRuleEngineNotificationsMsgProducer();

    /**
     * Used to push messages to other instances of TB Core Service
     * 获取核心消息生产者
     */
    TbQueueProducer<TbProtoQueueMsg<ToCoreMsg>> getTbCoreMsgProducer();

    /**
     * Used to push messages to other instances of TB Core Service
     * 获取核心通知消息生产者
     */
    TbQueueProducer<TbProtoQueueMsg<ToCoreNotificationMsg>> getTbCoreNotificationsMsgProducer();

    /**
     * Used to push messages to other instances of TB Core Service
     * 获取状态类消息生产者
     */
    TbQueueProducer<TbProtoQueueMsg<ToUsageStatsServiceMsg>> getTbUsageStatsMsgProducer();
}
