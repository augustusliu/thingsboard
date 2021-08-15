package org.thingsboard.server.actors;

import org.thingsboard.server.common.msg.TbActorMsg;

/**
 *
 * -----> mailBox
 * actor的关联关系，每个actor组成的结构是一颗树
 *
 * 邮箱的抽象接口
 */
public interface TbActorRef {

    TbActorId getActorId();

    void tell(TbActorMsg actorMsg);

    void tellWithHighPriority(TbActorMsg actorMsg);

}
