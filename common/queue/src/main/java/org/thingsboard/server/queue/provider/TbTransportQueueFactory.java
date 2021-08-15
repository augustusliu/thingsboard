package org.thingsboard.server.queue.provider;

import org.thingsboard.server.gen.transport.TransportProtos.ToCoreMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToRuleEngineMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToTransportMsg;
import org.thingsboard.server.gen.transport.TransportProtos.TransportApiRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.TransportApiResponseMsg;
import org.thingsboard.server.queue.TbQueueConsumer;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.queue.TbQueueRequestTemplate;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;

/**
 * 专门为不同消息对列、不同业务类型创建消息生产者和消息消费者的 工厂
 *
 */
public interface TbTransportQueueFactory extends TbUsageStatsClientQueueFactory {


    /**
     * 传输层api请求的 消息模板
     */
    TbQueueRequestTemplate<TbProtoQueueMsg<TransportApiRequestMsg>, TbProtoQueueMsg<TransportApiResponseMsg>> createTransportApiRequestTemplate();

    /**
     * 规则引擎消息的生产者
     */
    TbQueueProducer<TbProtoQueueMsg<ToRuleEngineMsg>> createRuleEngineMsgProducer();

    /**
     * 核心消息的生产者
     */
    TbQueueProducer<TbProtoQueueMsg<ToCoreMsg>> createTbCoreMsgProducer();

    /**
     * 传输层通知类型消息的 消费者
     */
    TbQueueConsumer<TbProtoQueueMsg<ToTransportMsg>> createTransportNotificationsConsumer();

}
