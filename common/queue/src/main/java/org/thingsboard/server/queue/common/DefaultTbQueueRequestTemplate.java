package org.thingsboard.server.queue.common;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.Gson;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.queue.TbQueueAdmin;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.TbQueueConsumer;
import org.thingsboard.server.queue.TbQueueMsg;
import org.thingsboard.server.queue.TbQueueMsgMetadata;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.queue.TbQueueRequestTemplate;
import org.thingsboard.server.common.stats.MessagesStats;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 消息对列模板，用于发送消息，接收消息
 * @param <Request>
 * @param <Response>
 *
 * <pre>
 *     api方式的转化为mq后，需要发送了消息就有响应的返回信息
 * </pre>
 */
@Slf4j
public class DefaultTbQueueRequestTemplate<Request extends TbQueueMsg, Response extends TbQueueMsg> extends AbstractTbQueueTemplate
        implements TbQueueRequestTemplate<Request, Response> {

    private final TbQueueAdmin queueAdmin;
    private final TbQueueProducer<Request> requestTemplate;
    private final TbQueueConsumer<Response> responseTemplate;
    /**
     * 保存已经从网关中监听到的请求，并将该请求发送到业务层消息对列中。未得到响应结果的请求都保存在该对列中
     */
    private final ConcurrentMap<UUID, DefaultTbQueueRequestTemplate.ResponseMetaData<Response>> pendingRequests;
    private final boolean internalExecutor;
    private final ExecutorService executor;
    // 请求最大的超时时间
    private final long maxRequestTimeout;

    /**
     * 最大的等待请求数量
     */
    private final long maxPendingRequests;
    private final long pollInterval;
    private volatile long tickTs = 0L;
    private volatile long tickSize = 0L;
    private volatile boolean stopped = false;

    private MessagesStats messagesStats;

    @Builder
    public DefaultTbQueueRequestTemplate(TbQueueAdmin queueAdmin,
                                         TbQueueProducer<Request> requestTemplate,
                                         TbQueueConsumer<Response> responseTemplate,
                                         long maxRequestTimeout,
                                         long maxPendingRequests,
                                         long pollInterval,
                                         ExecutorService executor) {
        this.queueAdmin = queueAdmin;
        this.requestTemplate = requestTemplate;
        this.responseTemplate = responseTemplate;
        this.pendingRequests = new ConcurrentHashMap<>();
        this.maxRequestTimeout = maxRequestTimeout;
        this.maxPendingRequests = maxPendingRequests;
        this.pollInterval = pollInterval;
        if (executor != null) {
            internalExecutor = false;
            this.executor = executor;
        } else {
            internalExecutor = true;
            this.executor = Executors.newSingleThreadExecutor();
        }
    }

    @Override
    public void init() {
        queueAdmin.createTopicIfNotExists(responseTemplate.getTopic());
        this.requestTemplate.init();
        tickTs = System.currentTimeMillis();
        responseTemplate.subscribe();
        log.info("消费者轮询 topic = {}:DefaultTbQueueRequestTemplate#init, 开始从InMemoryTbQueueConsumer中获取数据....", responseTemplate.getTopic());
        executor.submit(() -> {
            long nextCleanupMs = 0L;
            while (!stopped) {
                try {
                    List<Response> responses = responseTemplate.poll(pollInterval);
                    if (responses.size() > 0) {
                        log.info("consume消息 -> DefaultTbQueueRequestTemplate, topic={}, 获取返回数据:{}", responseTemplate.getTopic(), new Gson().toJson(responses));
//                        log.trace("Polling responses completed, consumer records count [{}]", responses.size());
                    }
                    responses.forEach(response -> {
                        // 获取该响应对应的请求ID
                        byte[] requestIdHeader = response.getHeaders().get(REQUEST_ID_HEADER);
                        UUID requestId;
                        if (requestIdHeader == null) {
                            log.error("[{}] Missing requestId in header and body", response);
                        } else {
                            requestId = bytesToUuid(requestIdHeader);
                            log.trace("[{}] Response received: {}", requestId, response);
                            // 接收到请求后，删除对应的pending请求，如果请求存在，则设置其对应的响应结果
                            ResponseMetaData<Response> expectedResponse = pendingRequests.remove(requestId);
                            if (expectedResponse == null) {
                                log.trace("[{}] Invalid or stale request", requestId);
                            } else {
                                expectedResponse.future.set(response);
                            }
                        }
                    });
                    responseTemplate.commit();
                    // 获取当前时间
                    tickTs = System.currentTimeMillis();
                    // 获取当前pending的请求数量
                    tickSize = pendingRequests.size();
                    // 判断pending请求是否存在超时
                    if (nextCleanupMs < tickTs) {
                        //cleanup;
                        pendingRequests.forEach((key, value) -> {
                            if (value.expTime < tickTs) {
                                ResponseMetaData<Response> staleRequest = pendingRequests.remove(key);
                                if (staleRequest != null) {
                                    log.trace("[{}] Request timeout detected, expTime [{}], tickTs [{}]", key, staleRequest.expTime, tickTs);
                                    staleRequest.future.setException(new TimeoutException());
                                }
                            }
                        });
                        // 更新下次处理pending超时的 触发点
                        nextCleanupMs = tickTs + maxRequestTimeout;
                    }
                } catch (Throwable e) {
                    log.warn("Failed to obtain responses from queue.", e);
                    try {
                        Thread.sleep(pollInterval);
                    } catch (InterruptedException e2) {
                        log.trace("Failed to wait until the server has capacity to handle new responses", e2);
                    }
                }
            }
        });
    }

    @Override
    public void stop() {
        stopped = true;

        if (responseTemplate != null) {
            responseTemplate.unsubscribe();
        }

        if (requestTemplate != null) {
            requestTemplate.stop();
        }

        if (internalExecutor) {
            executor.shutdownNow();
        }
    }

    @Override
    public void setMessagesStats(MessagesStats messagesStats) {
        this.messagesStats = messagesStats;
    }

    // 向消息对列发送一条消息
    @Override
    public ListenableFuture<Response> send(Request request) {
        if (tickSize > maxPendingRequests) {
            return Futures.immediateFailedFuture(new RuntimeException("Pending request map is full!"));
        }
        // 1、创建一个请求ID
        UUID requestId = UUID.randomUUID();
        request.getHeaders().put(REQUEST_ID_HEADER, uuidToBytes(requestId));
        request.getHeaders().put(RESPONSE_TOPIC_HEADER, stringToBytes(responseTemplate.getTopic()));
        request.getHeaders().put(REQUEST_TIME, longToBytes(System.currentTimeMillis()));

        // 2、为请求创建一个对应的返回Future
        SettableFuture<Response> future = SettableFuture.create();
        ResponseMetaData<Response> responseMetaData = new ResponseMetaData<>(tickTs + maxRequestTimeout, future);

        // 3、将响应的Future放置到pending对列中(当接收到响应后，从pending中获取到对应的请求，并将结果设置到其对应的响应结果中)
        pendingRequests.putIfAbsent(requestId, responseMetaData);
        log.trace("[{}] Sending request, key [{}], expTime [{}]", requestId, request.getKey(), responseMetaData.expTime);
        if (messagesStats != null) {
            messagesStats.incrementTotal();
        }

        // 4、发送该请求
        requestTemplate.send(TopicPartitionInfo.builder().topic(requestTemplate.getDefaultTopic()).build(),
                request,
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        if (messagesStats != null) {
                            messagesStats.incrementSuccessful();
                        }
                        log.trace("[{}] Request sent: {}", requestId, metadata);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        if (messagesStats != null) {
                            messagesStats.incrementFailed();
                        }
                        pendingRequests.remove(requestId);
                        future.setException(t);
                    }
                });
        return future;
    }

    /**
     * 返回消息体
     */
    private static class ResponseMetaData<T> {
        private final long expTime;
        private final SettableFuture<T> future;

        ResponseMetaData(long ts, SettableFuture<T> future) {
            this.expTime = ts;
            this.future = future;
        }
    }

}
