package org.thingsboard.server.queue.settings;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class TbQueueTransportApiSettings {

    /**
     * 传输层api接收消息的topic
     */
    @Value("${queue.transport_api.requests_topic}")
    private String requestsTopic;

    /**
     * 传输层api接收消息后，响应api返回的消息topic
     */
    @Value("${queue.transport_api.responses_topic}")
    private String responsesTopic;

    /**
     * api最大的pending请求数量
     */
    @Value("${queue.transport_api.max_pending_requests}")
    private int maxPendingRequests;

    /**
     * api接收消息超时
     */
    @Value("${queue.transport_api.max_requests_timeout}")
    private int maxRequestsTimeout;

    /**
     * 异步回调线程数
     */
    @Value("${queue.transport_api.max_callback_threads}")
    private int maxCallbackThreads;

    /**
     * 消息接收间隔
     */
    @Value("${queue.transport_api.request_poll_interval}")
    private long requestPollInterval;

    /**
     * 消息消费间隔
     */
    @Value("${queue.transport_api.response_poll_interval}")
    private long responsePollInterval;

}
