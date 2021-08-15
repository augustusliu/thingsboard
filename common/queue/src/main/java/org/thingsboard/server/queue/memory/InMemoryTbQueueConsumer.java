package org.thingsboard.server.queue.memory;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.queue.TbQueueConsumer;
import org.thingsboard.server.queue.TbQueueMsg;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class InMemoryTbQueueConsumer<T extends TbQueueMsg> implements TbQueueConsumer<T> {
    // 消息存储引擎
    private final InMemoryStorage storage = InMemoryStorage.getInstance();
    // 消息分区
    private volatile Set<TopicPartitionInfo> partitions;
    // 是否在运行
    private volatile boolean stopped;
    // 是否已经被订阅
    private volatile boolean subscribed;

    public InMemoryTbQueueConsumer(String topic) {
        this.topic = topic;
        stopped = false;
    }

    private final String topic;

    @Override
    public String getTopic() {
        return topic;
    }

    // 开启订阅
    @Override
    public void subscribe() {
        log.info("InMemoryTbQueueConsumer#subscribe, 开启消费者订阅开关.topic={}", topic);
        partitions = Collections.singleton(new TopicPartitionInfo(topic, null, null, true));
        subscribed = true;
    }

    // 订阅全部分区
    @Override
    public void subscribe(Set<TopicPartitionInfo> partitions) {
        log.info("InMemoryTbQueueConsumer#subscribe, 订阅对列:{}", new Gson().toJson(partitions));
        this.partitions = partitions;
        subscribed = true;
    }

    @Override
    public void unsubscribe() {
        stopped = true;
    }

    @Override
    public List<T> poll(long durationInMillis) {
        if (subscribed) {
            List<T> messages = partitions
                    .stream()
                    .map(tpi -> {
                        try {
                            return storage.get(tpi.getFullTopicName());
                        } catch (InterruptedException e) {
                            if (!stopped) {
                                log.error("Queue was interrupted.", e);
                            }
                            return Collections.emptyList();
                        }
                    })
                    .flatMap(List::stream)
                    .map(msg -> (T) msg).collect(Collectors.toList());
            if (messages.size() > 0) {
                return messages;
            }
            try {
                Thread.sleep(durationInMillis);
            } catch (InterruptedException e) {
                if (!stopped) {
                    log.error("Failed to sleep.", e);
                }
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void commit() {
    }
}
