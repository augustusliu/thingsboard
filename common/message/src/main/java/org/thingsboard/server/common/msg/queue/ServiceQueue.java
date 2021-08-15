package org.thingsboard.server.common.msg.queue;

import lombok.ToString;

import java.util.Objects;


/**
 * 每种业务类型，不一定只有一个Queue，也可能有多个Queue
 */
@ToString
public class ServiceQueue {

    public static final String MAIN = "Main";

    /**
     * 业务类型
     */
    private final ServiceType type;
    /**
     * 对列的名称
     */
    private final String queue;

    public ServiceQueue(ServiceType type) {
        this.type = type;
        this.queue = MAIN;
    }

    public ServiceQueue(ServiceType type, String queue) {
        this.type = type;
        this.queue = queue != null ? queue : MAIN;
    }

    public ServiceType getType() {
        return type;
    }

    public String getQueue() {
        return queue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceQueue that = (ServiceQueue) o;
        return type == that.type &&
                queue.equals(that.queue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, queue);
    }

}
