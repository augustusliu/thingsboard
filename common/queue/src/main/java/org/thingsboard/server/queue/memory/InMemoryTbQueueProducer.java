package org.thingsboard.server.queue.memory;

import com.google.gson.Gson;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.TbQueueMsg;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;

@Slf4j
@Data
public class InMemoryTbQueueProducer<T extends TbQueueMsg> implements TbQueueProducer<T> {

    private final InMemoryStorage storage = InMemoryStorage.getInstance();

    private final String defaultTopic;
    private Gson gson = new Gson();

    public InMemoryTbQueueProducer(String defaultTopic) {
        this.defaultTopic = defaultTopic;
    }

    @Override
    public void init() {

    }

    @Override
    public void send(TopicPartitionInfo tpi, T msg, TbQueueCallback callback) {
        log.info("消息Send->Memory-Producer->topic ={} , message = {}, tpi = {}", tpi.getTopic(), gson.toJson(msg), gson.toJson(tpi));
        boolean result = storage.put(tpi.getFullTopicName(), msg);
        if (result) {
            if (callback != null) {
                callback.onSuccess(null);
            }
        } else {
            if (callback != null) {
                callback.onFailure(new RuntimeException("InMemoryTbQueueProducer: Failure add msg to InMemoryQueue"));
            }
        }
    }

    @Override
    public void stop() {

    }
}
