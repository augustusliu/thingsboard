package org.thingsboard.server.queue.memory;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.queue.TbQueueMsg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public final class InMemoryStorage {
    private static InMemoryStorage instance;
    // 用于存储内存消息 key=topic
    private final ConcurrentHashMap<String, BlockingQueue<TbQueueMsg>> storage;

    private InMemoryStorage() {
        storage = new ConcurrentHashMap<>();
    }

    public void printStats() {
        storage.forEach((topic, queue) -> {
            if (queue.size() > 0) {
                log.debug("InMemoryStorage: [{}] Queue Size [{}]", topic, queue.size());
            }
        });
    }

    public static InMemoryStorage getInstance() {
        if (instance == null) {
            synchronized (InMemoryStorage.class) {
                if (instance == null) {
                    instance = new InMemoryStorage();
                }
            }
        }
        return instance;
    }

    public boolean put(String topic, TbQueueMsg msg) {
        log.info("MemoryStorage#put -> topic ={}, topics = {}", topic, new Gson().toJson(storage.keys()));
        return storage.computeIfAbsent(topic, (t) -> new LinkedBlockingQueue<>()).add(msg);
    }

    public <T extends TbQueueMsg> List<T> get(String topic) throws InterruptedException {
        if (storage.containsKey(topic)) {
            List<T> entities;
            T first = (T) storage.get(topic).poll();
            if (first != null) {
                log.info("MemoryStorage#get -> topic ={}, msg = {}", topic, new Gson().toJson(first));
                entities = new ArrayList<>();
                entities.add(first);
                List<TbQueueMsg> otherList = new ArrayList<>();
                storage.get(topic).drainTo(otherList, 999);
                for (TbQueueMsg other : otherList) {
                    entities.add((T) other);
                }
            } else {
                entities = Collections.emptyList();
            }
            return entities;
        }
        return Collections.emptyList();
    }

    /**
     * Used primarily for testing.
     */
    public void cleanup() {
        storage.clear();
    }

}
