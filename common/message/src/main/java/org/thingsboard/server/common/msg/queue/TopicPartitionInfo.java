package org.thingsboard.server.common.msg.queue;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.thingsboard.server.common.data.id.TenantId;

import java.util.Objects;
import java.util.Optional;

@ToString
public class TopicPartitionInfo {

    private final String topic;
    private final TenantId tenantId;
    private final Integer partition;

    /**
     * 全名，携带partition的名字
     * topic.tenantId.partition
     */
    @Getter
    private final String fullTopicName;
    @Getter
    private final boolean myPartition;

    @Builder
    public TopicPartitionInfo(String topic, TenantId tenantId, Integer partition, boolean myPartition) {
        this.topic = topic;
        this.tenantId = tenantId;
        this.partition = partition;
        this.myPartition = myPartition;
        String tmp = topic;
        if (tenantId != null) {
            tmp += "." + tenantId.getId().toString();
        }
        if (partition != null) {
            tmp += "." + partition;
        }
        this.fullTopicName = tmp;
    }

    // 根据消息创建分区
    public TopicPartitionInfo newByTopic(String topic) {
        return new TopicPartitionInfo(topic, this.tenantId, this.partition, this.myPartition);
    }

    public String getTopic() {
        return topic;
    }

    public Optional<TenantId> getTenantId() {
        return Optional.ofNullable(tenantId);
    }

    public Optional<Integer> getPartition() {
        return Optional.ofNullable(partition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TopicPartitionInfo that = (TopicPartitionInfo) o;
        return topic.equals(that.topic) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(partition, that.partition) &&
                fullTopicName.equals(that.fullTopicName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullTopicName);
    }
}
