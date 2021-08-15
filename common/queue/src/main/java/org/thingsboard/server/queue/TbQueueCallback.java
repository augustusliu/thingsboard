package org.thingsboard.server.queue;

/**
 * <pre>
 *     消息对列的回调函数
 * </pre>
 */
public interface TbQueueCallback {

    void onSuccess(TbQueueMsgMetadata metadata);

    void onFailure(Throwable t);
}
