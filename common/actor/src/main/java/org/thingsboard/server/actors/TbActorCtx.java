package org.thingsboard.server.actors;

import org.thingsboard.server.common.msg.TbActorMsg;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface TbActorCtx extends TbActorRef {

    TbActorId getSelf();

    /**
     * 邮箱抽象接口
     */
    TbActorRef getParentRef();

    void tell(TbActorId target, TbActorMsg msg);

    void stop(TbActorId target);

    TbActorRef getOrCreateChildActor(TbActorId actorId, Supplier<String> dispatcher, Supplier<TbActorCreator> creator);

    void broadcastToChildren(TbActorMsg msg);

    void broadcastToChildren(TbActorMsg msg, Predicate<TbActorId> childFilter);

    List<TbActorId> filterChildren(Predicate<TbActorId> childFilter);
}
