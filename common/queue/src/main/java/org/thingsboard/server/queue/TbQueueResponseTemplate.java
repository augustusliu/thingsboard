package org.thingsboard.server.queue;

/**
 * <pre>
 *     消息响应处理
 * </pre>
 * @param <Request>
 * @param <Response>
 */
public interface TbQueueResponseTemplate<Request extends TbQueueMsg, Response extends TbQueueMsg> {

    /**
     * 启动   一般来讲指的是 开始监听某个topic
     */
    void init(TbQueueHandler<Request, Response> handler);

    /**
     * 停止
     */
    void stop();
}
