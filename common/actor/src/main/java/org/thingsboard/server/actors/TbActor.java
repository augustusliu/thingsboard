package org.thingsboard.server.actors;

import org.thingsboard.server.common.msg.TbActorMsg;

/**
 * Actor由状态TbActor(state)、行为Dispatcher(Behavior)和邮箱TbActorRef(mailBox)三部分组成
 *
 * TbActor-->Actor
 */
public interface TbActor {

    /**
     * actor处理消息内部状态
     */
    boolean process(TbActorMsg msg);

    /**
     * TbActorRef --> mailBox
     * TbActorRef 指邮箱的概念
     * 获取当前actor的邮箱
     */
    TbActorRef getActorRef();


    default void init(TbActorCtx ctx) throws TbActorException {
    }

    default void destroy() throws TbActorException {
    }

    default InitFailureStrategy onInitFailure(int attempt, Throwable t) {
        return InitFailureStrategy.retryWithDelay(5000 * attempt);
    }

    default ProcessFailureStrategy onProcessFailure(Throwable t) {
        if (t instanceof Error) {
            return ProcessFailureStrategy.stop();
        } else {
            return ProcessFailureStrategy.resume();
        }
    }
}
