package org.thingsboard.server.common.transport.service;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.common.data.ApiUsageRecordKey;
import org.thingsboard.server.common.data.ApiUsageState;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.TenantProfileId;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.queue.ServiceQueue;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.common.msg.session.SessionMsgType;
import org.thingsboard.server.common.msg.tools.TbRateLimitsException;
import org.thingsboard.server.common.stats.MessagesStats;
import org.thingsboard.server.common.stats.StatsFactory;
import org.thingsboard.server.common.stats.StatsType;
import org.thingsboard.server.common.transport.SessionMsgListener;
import org.thingsboard.server.common.transport.TransportDeviceProfileCache;
import org.thingsboard.server.common.transport.TransportService;
import org.thingsboard.server.common.transport.TransportServiceCallback;
import org.thingsboard.server.common.transport.TransportTenantProfileCache;
import org.thingsboard.server.common.transport.auth.GetOrCreateDeviceFromGatewayResponse;
import org.thingsboard.server.common.transport.auth.TransportDeviceInfo;
import org.thingsboard.server.common.transport.auth.ValidateDeviceCredentialsResponse;
import org.thingsboard.server.common.transport.limits.TransportRateLimitService;
import org.thingsboard.server.common.transport.util.DataDecodingEncodingService;
import org.thingsboard.server.common.transport.util.JsonUtils;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.ProvisionDeviceRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ProvisionDeviceResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToCoreMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToRuleEngineMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToTransportMsg;
import org.thingsboard.server.gen.transport.TransportProtos.TransportApiRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.TransportApiResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.TransportToDeviceActorMsg;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.TbQueueConsumer;
import org.thingsboard.server.queue.TbQueueMsgMetadata;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.queue.TbQueueRequestTemplate;
import org.thingsboard.server.queue.common.AsyncCallbackTemplate;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.queue.discovery.TbServiceInfoProvider;
import org.thingsboard.server.queue.provider.TbQueueProducerProvider;
import org.thingsboard.server.queue.provider.TbTransportQueueFactory;
import org.thingsboard.server.queue.scheduler.SchedulerComponent;
import org.thingsboard.server.queue.usagestats.TbApiUsageClient;
import org.thingsboard.server.queue.util.TbTransportComponent;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ashvayka on 17.10.18.
 * <pre>
 *     传输层服务
 *     所有接收到的请求都在传输层，这里提供了单独的传输层服务器。
 * </pre>
 */
@Slf4j
@Service
@TbTransportComponent
public class DefaultTransportService implements TransportService {

    // session 有效时长
    @Value("${transport.sessions.inactivity_timeout}")
    private long sessionInactivityTimeout;
    // session 上报超时
    @Value("${transport.sessions.report_timeout}")
    private long sessionReportTimeout;
    // rpc 有效时长
    @Value("${transport.client_side_rpc.timeout:60000}")
    private long clientSideRpcTimeout;

    // 对列中数据的拉取周期
    @Value("${queue.transport.poll_interval}")
    private int notificationsPollDuration;

    private final Gson gson = new Gson();

    /**
     * <pre>
     *     工厂模式：
     *     获取规则引擎消息对列的生产者(根据之前实例化的消息对列服务器queueProvider，获取规则引擎业务的生产者。只是topic不同而已)
     *
     *     所有的消息对列服务器初始化后，都需要根据TbQueueProducerProvider服务，实例化每个服务对应的topic,并生成对应的 生产者。
     * </pre>
     */
    private final TbTransportQueueFactory queueProvider;

    // 消息生产者提供商： 表示以上的消息对列主要用于哪些业务类型(核心业务，规则引擎业务，传输层业务)
    private final TbQueueProducerProvider producerProvider;

    // 消息对列的分区服务
    private final PartitionService partitionService;
    // 服务类型提供商：表示当前的业务类型(TB_CORE, TB_RULE_ENGINE, TB_TRANSPORT, JS_EXECUTOR;)
    private final TbServiceInfoProvider serviceInfoProvider;

    // 状态统计工厂
    private final StatsFactory statsFactory;
    // 设备连接后的画像缓存--租户画像缓存
    private final TransportDeviceProfileCache deviceProfileCache;
    private final TransportTenantProfileCache tenantProfileCache;

    // api使用情况上报
    private final TbApiUsageClient apiUsageClient;

    // 传输层限流服务
    private final TransportRateLimitService rateLimitService;

    // 数据解码编码服务
    private final DataDecodingEncodingService dataDecodingEncodingService;

    // 消息调度服务
    private final SchedulerComponent scheduler;

    // api消息生产者模板 --- 对应的接收对象是TbQueueResponseTemplate(在业务端)
    protected TbQueueRequestTemplate<TbProtoQueueMsg<TransportApiRequestMsg>, TbProtoQueueMsg<TransportApiResponseMsg>> transportApiRequestTemplate;

    // 规则引擎生产者
    protected TbQueueProducer<TbProtoQueueMsg<ToRuleEngineMsg>> ruleEngineMsgProducer;

    // tocore消息生产者
    protected TbQueueProducer<TbProtoQueueMsg<ToCoreMsg>> tbCoreMsgProducer;

    // 传输层通知消息消费者
    protected TbQueueConsumer<TbProtoQueueMsg<ToTransportMsg>> transportNotificationsConsumer;

    // 消息状态
    protected MessagesStats ruleEngineProducerStats;
    protected MessagesStats tbCoreProducerStats;
    protected MessagesStats transportApiStats;

    // 传输层响应异步执行器(线程池)
    protected ExecutorService transportCallbackExecutor;

    // 主消息消费执行器(线程池)
    private ExecutorService mainConsumerExecutor;

    // session缓存
    private final ConcurrentMap<UUID, SessionMetaData> sessions = new ConcurrentHashMap<>();
    // rpc pending缓存
    private final Map<String, RpcRequestMetadata> toServerRpcPendingMap = new ConcurrentHashMap<>();

    private volatile boolean stopped = false;

    public DefaultTransportService(TbServiceInfoProvider serviceInfoProvider,
                                   TbTransportQueueFactory queueProvider,
                                   TbQueueProducerProvider producerProvider,
                                   PartitionService partitionService,
                                   StatsFactory statsFactory,
                                   TransportDeviceProfileCache deviceProfileCache,
                                   TransportTenantProfileCache tenantProfileCache,
                                   TbApiUsageClient apiUsageClient, TransportRateLimitService rateLimitService,
                                   DataDecodingEncodingService dataDecodingEncodingService, SchedulerComponent scheduler) {
        this.serviceInfoProvider = serviceInfoProvider;
        this.queueProvider = queueProvider;
        this.producerProvider = producerProvider;
        this.partitionService = partitionService;
        this.statsFactory = statsFactory;
        this.deviceProfileCache = deviceProfileCache;
        this.tenantProfileCache = tenantProfileCache;
        this.apiUsageClient = apiUsageClient;
        this.rateLimitService = rateLimitService;
        this.dataDecodingEncodingService = dataDecodingEncodingService;
        this.scheduler = scheduler;
    }

    @PostConstruct
    public void init() {
        // 统计状态 -- 采用stats组件
        this.ruleEngineProducerStats = statsFactory.createMessagesStats(StatsType.RULE_ENGINE.getName() + ".producer");
        this.tbCoreProducerStats = statsFactory.createMessagesStats(StatsType.CORE.getName() + ".producer");
        this.transportApiStats = statsFactory.createMessagesStats(StatsType.TRANSPORT.getName() + ".producer");

        // 抢占式传输层回调执行器 -- 用户回告传输层响应内容
        this.transportCallbackExecutor = Executors.newWorkStealingPool(20);

        // 消息调度器 -- 按照固定频率调度checkInactivityAndReportActivity方法，用于监测session的有效性
        this.scheduler.scheduleAtFixedRate(this::checkInactivityAndReportActivity, new Random().nextInt((int) sessionReportTimeout), sessionReportTimeout, TimeUnit.MILLISECONDS);

        // 基于配置文件中的queue进行具体的消息对列服务，可也是in-memory,kafka......等
        transportApiRequestTemplate = queueProvider.createTransportApiRequestTemplate();

        // 设置当前消息对列的统计信息
        transportApiRequestTemplate.setMessagesStats(transportApiStats);

        /**
         * 获取规则引擎消息对列的生产者：
         *    根据之前实例化的消息对列服务器queueProvider，获取规则引擎业务的生产者。只是topic不同而已
         *  这里很重要。
         */
        ruleEngineMsgProducer = producerProvider.getRuleEngineMsgProducer();

        /**
         * 获取核心业务的消息对列的生产者：
         * topic不同
         */
        tbCoreMsgProducer = producerProvider.getTbCoreMsgProducer();

        /**
         * 获取传输层通知消息对列的的消费者： 具有最高优先级
         * topic不同
         */
        transportNotificationsConsumer = queueProvider.createTransportNotificationsConsumer();

        // 接收到的任何消息，都以topic消息方式处理
        TopicPartitionInfo tpi = partitionService.getNotificationsTopic(ServiceType.TB_TRANSPORT, serviceInfoProvider.getServiceId());

        log.info("开启传输层通知的消费者监听..... DefaultTransportService->transportNotificationsConsumer.subscribe().");
        transportNotificationsConsumer.subscribe(Collections.singleton(tpi));

        log.info("开启传输层API请求的消息消费者监听......DefaultTransportService->transportApiRequestTemplate.init().");
        transportApiRequestTemplate.init();

        // 启动一个主线程，处理所有的transport
        mainConsumerExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("transport-consumer"));

        // 消费者执行器
        mainConsumerExecutor.execute(() -> {
            while (!stopped) {
                try {

                    // 获取具有最高优先级的 传输层通知消息对列内容
                    List<TbProtoQueueMsg<ToTransportMsg>> records = transportNotificationsConsumer.poll(notificationsPollDuration);
                    if (records.size() == 0) {
                        continue;
                    }
                    log.info("consume->TransportService-mainConsumerExecutor, topic={}, message ={}", transportNotificationsConsumer.getTopic(), gson.toJson(records));
                    // 遍历所有的消息--将返回的响应转化为不同的协议消息
                    records.forEach(record -> {
                        try {
                            processToTransportMsg(record.getValue());
                        } catch (Throwable e) {
                            log.warn("Failed to process the notification.", e);
                        }
                    });
                    transportNotificationsConsumer.commit();
                } catch (Exception e) {
                    if (!stopped) {
                        log.warn("Failed to obtain messages from queue.", e);
                        try {
                            Thread.sleep(notificationsPollDuration);
                        } catch (InterruptedException e2) {
                            log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                        }
                    }
                }
            }
        });
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        if (transportNotificationsConsumer != null) {
            transportNotificationsConsumer.unsubscribe();
        }
        if (transportCallbackExecutor != null) {
            transportCallbackExecutor.shutdownNow();
        }
        if (mainConsumerExecutor != null) {
            mainConsumerExecutor.shutdownNow();
        }
        if (transportApiRequestTemplate != null) {
            transportApiRequestTemplate.stop();
        }
    }

    /**
     * 注册异步session
     * @param sessionInfo   session
     * @param listener      注册的监听器
     */
    @Override
    public void registerAsyncSession(TransportProtos.SessionInfoProto sessionInfo, SessionMsgListener listener) {
        sessions.putIfAbsent(toSessionId(sessionInfo), new SessionMetaData(sessionInfo, TransportProtos.SessionType.ASYNC, listener));
    }

    /**
     * 基于API请求方式的 消息对列实现
     * @param msg  实体消息
     * 用法：
     *    当从租户缓存中获取不到信息时，采用这种异步获取租户信息并更新缓存 {@link DefaultTransportTenantProfileCache }
     */
    @Override
    public TransportProtos.GetEntityProfileResponseMsg getEntityProfile(TransportProtos.GetEntityProfileRequestMsg msg) {
        // 生成传输层----> 核心业务层 的 中间统一数据结构
        TbProtoQueueMsg<TransportProtos.TransportApiRequestMsg> protoMsg =
                new TbProtoQueueMsg<>(UUID.randomUUID(), TransportProtos.TransportApiRequestMsg.newBuilder().setEntityProfileRequestMsg(msg).build());

        // 调用核心业务层，并回去返回结果
        try {
            TbProtoQueueMsg<TransportApiResponseMsg> response = transportApiRequestTemplate.send(protoMsg).get();
            return response.getValue().getEntityProfileResponseMsg();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void process(DeviceTransportType transportType, TransportProtos.ValidateDeviceTokenRequestMsg msg,
                        TransportServiceCallback<ValidateDeviceCredentialsResponse> callback) {
        log.trace("DefaultTransportService#process, processing msg: {}", msg);

        // 生成传输层----> 核心业务层 的 中间统一数据结构
        TbProtoQueueMsg<TransportApiRequestMsg> protoMsg = new TbProtoQueueMsg<>(UUID.randomUUID(),
                TransportApiRequestMsg.newBuilder().setValidateTokenRequestMsg(msg).build());
        // 处理请求
        doProcess(transportType, protoMsg, callback);
    }

    @Override
    public void process(DeviceTransportType transportType, TransportProtos.ValidateBasicMqttCredRequestMsg msg,
                        TransportServiceCallback<ValidateDeviceCredentialsResponse> callback) {
        log.trace("Processing msg: {}", msg);
        TbProtoQueueMsg<TransportApiRequestMsg> protoMsg = new TbProtoQueueMsg<>(UUID.randomUUID(),
                TransportApiRequestMsg.newBuilder().setValidateBasicMqttCredRequestMsg(msg).build());
        doProcess(transportType, protoMsg, callback);
    }

    @Override
    public void process(DeviceTransportType transportType, TransportProtos.ValidateDeviceX509CertRequestMsg msg, TransportServiceCallback<ValidateDeviceCredentialsResponse> callback) {
        log.trace("Processing msg: {}", msg);
        TbProtoQueueMsg<TransportApiRequestMsg> protoMsg = new TbProtoQueueMsg<>(UUID.randomUUID(), TransportApiRequestMsg.newBuilder().setValidateX509CertRequestMsg(msg).build());
        doProcess(transportType, protoMsg, callback);
    }

    /**
     * 发送消息
     * @param transportType 协议类型
     * @param protoMsg      基于API的MQ消息
     * @param callback      传输层服务的回调函数
     */
    private void doProcess(DeviceTransportType transportType, TbProtoQueueMsg<TransportApiRequestMsg> protoMsg,
                           TransportServiceCallback<ValidateDeviceCredentialsResponse> callback) {

        /**
         * <pre>
         *  链式异步操作：Futures.transform(future, function, executor)
         *  第一个参数是个future， 该future.get()到的结果，作为function的输入参数，处理后返回一个最终的结果。同时fuction可以是同步的，也可以是异步的。
         *
         *  transportApiRequestTemplate.send(protoMsg) 返回一个异步处理结果future
         *  Function<F, T> 表示传入参数F，异步返回参数T。
         *  在以下列子中就是将transportApiRequestTemplate.send(protoMsg).get()结果作为参数，并返回ValidateDeviceCredentialsResponse的结果。
         *
         *  以上总体上是个ListenableFuture结果， 故称其为链式异步计算。
         *
         * ### 业务流程--分两步走：
         *  # 第一步： 根据权限获取设备信息
         *  # 第二步：
         *
         * </pre>
         */
        ListenableFuture<ValidateDeviceCredentialsResponse> response = Futures.transform(transportApiRequestTemplate.send(protoMsg),

                new Function<TbProtoQueueMsg<TransportApiResponseMsg>, ValidateDeviceCredentialsResponse>() {
                    @Nullable
                    @Override
                    public ValidateDeviceCredentialsResponse apply(@Nullable TbProtoQueueMsg<TransportApiResponseMsg> tmp) {
                        TransportProtos.ValidateDeviceCredentialsResponseMsg msg = tmp.getValue().getValidateCredResponseMsg();
                        ValidateDeviceCredentialsResponse.ValidateDeviceCredentialsResponseBuilder result = ValidateDeviceCredentialsResponse.builder();
                        if (msg.hasDeviceInfo()) {
                            result.credentials(msg.getCredentialsBody());
                            TransportDeviceInfo tdi = DefaultTransportService.this.getTransportDeviceInfo(msg.getDeviceInfo());
                            result.deviceInfo(tdi);
                            ByteString profileBody = msg.getProfileBody();
                            if (profileBody != null && !profileBody.isEmpty()) {
                                DeviceProfile profile = deviceProfileCache.getOrCreate(tdi.getDeviceProfileId(), profileBody);
                                if (transportType != DeviceTransportType.DEFAULT
                                        && profile != null && profile.getTransportType() != DeviceTransportType.DEFAULT && profile.getTransportType() != transportType) {
                                    log.debug("[{}] Device profile [{}] has different transport type: {}, expected: {}", tdi.getDeviceId(), tdi.getDeviceProfileId(), profile.getTransportType(), transportType);
                                    throw new IllegalStateException("Device profile has different transport type: " + profile.getTransportType() + ". Expected: " + transportType);
                                }
                                result.deviceProfile(profile);
                            }
                        }
                        return result.build();
                    }
                }, MoreExecutors.directExecutor());
        // 使用线程池 异步执行transportCallbackExecutor
        AsyncCallbackTemplate.withCallback(response, callback::onSuccess, callback::onError, transportCallbackExecutor);
    }

    @Override
    public void process(TransportProtos.GetOrCreateDeviceFromGatewayRequestMsg requestMsg, TransportServiceCallback<GetOrCreateDeviceFromGatewayResponse> callback) {
        TbProtoQueueMsg<TransportApiRequestMsg> protoMsg = new TbProtoQueueMsg<>(UUID.randomUUID(), TransportApiRequestMsg.newBuilder().setGetOrCreateDeviceRequestMsg(requestMsg).build());
        log.trace("Processing msg: {}", requestMsg);
        ListenableFuture<GetOrCreateDeviceFromGatewayResponse> response = Futures.transform(transportApiRequestTemplate.send(protoMsg), tmp -> {
            TransportProtos.GetOrCreateDeviceFromGatewayResponseMsg msg = tmp.getValue().getGetOrCreateDeviceResponseMsg();
            GetOrCreateDeviceFromGatewayResponse.GetOrCreateDeviceFromGatewayResponseBuilder result = GetOrCreateDeviceFromGatewayResponse.builder();
            if (msg.hasDeviceInfo()) {
                TransportDeviceInfo tdi = getTransportDeviceInfo(msg.getDeviceInfo());
                result.deviceInfo(tdi);
                ByteString profileBody = msg.getProfileBody();
                if (profileBody != null && !profileBody.isEmpty()) {
                    result.deviceProfile(deviceProfileCache.getOrCreate(tdi.getDeviceProfileId(), profileBody));
                }
            }
            return result.build();
        }, MoreExecutors.directExecutor());
        AsyncCallbackTemplate.withCallback(response, callback::onSuccess, callback::onError, transportCallbackExecutor);
    }

    private TransportDeviceInfo getTransportDeviceInfo(TransportProtos.DeviceInfoProto di) {
        TransportDeviceInfo tdi = new TransportDeviceInfo();
        tdi.setTenantId(new TenantId(new UUID(di.getTenantIdMSB(), di.getTenantIdLSB())));
        tdi.setDeviceId(new DeviceId(new UUID(di.getDeviceIdMSB(), di.getDeviceIdLSB())));
        tdi.setDeviceProfileId(new DeviceProfileId(new UUID(di.getDeviceProfileIdMSB(), di.getDeviceProfileIdLSB())));
        tdi.setAdditionalInfo(di.getAdditionalInfo());
        tdi.setDeviceName(di.getDeviceName());
        tdi.setDeviceType(di.getDeviceType());
        return tdi;
    }

    @Override
    public void process(ProvisionDeviceRequestMsg requestMsg, TransportServiceCallback<ProvisionDeviceResponseMsg> callback) {
        log.trace("Processing msg: {}", requestMsg);
        TbProtoQueueMsg<TransportApiRequestMsg> protoMsg = new TbProtoQueueMsg<>(UUID.randomUUID(), TransportApiRequestMsg.newBuilder().setProvisionDeviceRequestMsg(requestMsg).build());
        ListenableFuture<ProvisionDeviceResponseMsg> response = Futures.transform(transportApiRequestTemplate.send(protoMsg), tmp -> tmp.getValue().getProvisionDeviceResponseMsg(),
                MoreExecutors.directExecutor());
        AsyncCallbackTemplate.withCallback(response, callback::onSuccess, callback::onError, transportCallbackExecutor);
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.SubscriptionInfoProto msg, TransportServiceCallback<Void> callback) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Processing msg: {}", toSessionId(sessionInfo), msg);
        }
        sendToDeviceActor(sessionInfo, TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                .setSubscriptionInfo(msg).build(), callback);
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.SessionEventMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            reportActivityInternal(sessionInfo);
            sendToDeviceActor(sessionInfo, TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                    .setSessionEvent(msg).build(), callback);
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.PostTelemetryMsg msg, TransportServiceCallback<Void> callback) {
        int dataPoints = 0;
        for (TransportProtos.TsKvListProto tsKv : msg.getTsKvListList()) {
            dataPoints += tsKv.getKvCount();
        }
        if (checkLimits(sessionInfo, msg, callback, dataPoints)) {
            reportActivityInternal(sessionInfo);
            TenantId tenantId = new TenantId(new UUID(sessionInfo.getTenantIdMSB(), sessionInfo.getTenantIdLSB()));
            DeviceId deviceId = new DeviceId(new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB()));
            MsgPackCallback packCallback = new MsgPackCallback(msg.getTsKvListCount(), new ApiStatsProxyCallback<>(tenantId, dataPoints, callback));
            for (TransportProtos.TsKvListProto tsKv : msg.getTsKvListList()) {
                TbMsgMetaData metaData = new TbMsgMetaData();
                metaData.putValue("deviceName", sessionInfo.getDeviceName());
                metaData.putValue("deviceType", sessionInfo.getDeviceType());
                metaData.putValue("ts", tsKv.getTs() + "");
                JsonObject json = JsonUtils.getJsonObject(tsKv.getKvList());
                sendToRuleEngine(tenantId, deviceId, sessionInfo, json, metaData, SessionMsgType.POST_TELEMETRY_REQUEST, packCallback);

            }
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.PostAttributeMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback, msg.getKvCount())) {
            reportActivityInternal(sessionInfo);
            TenantId tenantId = new TenantId(new UUID(sessionInfo.getTenantIdMSB(), sessionInfo.getTenantIdLSB()));
            DeviceId deviceId = new DeviceId(new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB()));
            JsonObject json = JsonUtils.getJsonObject(msg.getKvList());
            TbMsgMetaData metaData = new TbMsgMetaData();
            metaData.putValue("deviceName", sessionInfo.getDeviceName());
            metaData.putValue("deviceType", sessionInfo.getDeviceType());
            metaData.putValue("notifyDevice", "false");
            sendToRuleEngine(tenantId, deviceId, sessionInfo, json, metaData, SessionMsgType.POST_ATTRIBUTES_REQUEST,
                    new TransportTbQueueCallback(new ApiStatsProxyCallback<>(tenantId, msg.getKvList().size(), callback)));
        }
    }


    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.GetAttributeRequestMsg msg, TransportServiceCallback<Void> callback) {
        log.info("处理获取属性的请求: msg = {}", gson.toJson(msg));
        // 限流
        if (checkLimits(sessionInfo, msg, callback)) {
            // 更新session时间
            reportActivityInternal(sessionInfo);
            // 发送设备actor事件
            sendToDeviceActor(sessionInfo,
                    TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo).setGetAttributes(msg).build(),
                    new ApiStatsProxyCallback<>(getTenantId(sessionInfo), 1, callback));
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.SubscribeToAttributeUpdatesMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            SessionMetaData sessionMetaData = reportActivityInternal(sessionInfo);
            sessionMetaData.setSubscribedToAttributes(!msg.getUnsubscribe());
            sendToDeviceActor(sessionInfo, TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                    .setSubscribeToAttributes(msg).build(), new ApiStatsProxyCallback<>(getTenantId(sessionInfo), 1, callback));
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.SubscribeToRPCMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            SessionMetaData sessionMetaData = reportActivityInternal(sessionInfo);
            sessionMetaData.setSubscribedToRPC(!msg.getUnsubscribe());
            sendToDeviceActor(sessionInfo, TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                    .setSubscribeToRPC(msg).build(), new ApiStatsProxyCallback<>(getTenantId(sessionInfo), 1, callback));
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.ToDeviceRpcResponseMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            reportActivityInternal(sessionInfo);
            sendToDeviceActor(sessionInfo, TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                    .setToDeviceRPCCallResponse(msg).build(), new ApiStatsProxyCallback<>(getTenantId(sessionInfo), 1, callback));
        }
    }

    private void processTimeout(String requestId) {
        RpcRequestMetadata data = toServerRpcPendingMap.remove(requestId);
        if (data != null) {
            SessionMetaData md = sessions.get(data.getSessionId());
            if (md != null) {
                SessionMsgListener listener = md.getListener();
                transportCallbackExecutor.submit(() -> {
                    TransportProtos.ToServerRpcResponseMsg responseMsg =
                            TransportProtos.ToServerRpcResponseMsg.newBuilder()
                                    .setRequestId(data.getRequestId())
                                    .setError("timeout").build();
                    listener.onToServerRpcResponse(responseMsg);
                });
                if (md.getSessionType() == TransportProtos.SessionType.SYNC) {
                    deregisterSession(md.getSessionInfo());
                }
            } else {
                log.debug("[{}] Missing session.", data.getSessionId());
            }
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.ToServerRpcRequestMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            reportActivityInternal(sessionInfo);
            UUID sessionId = toSessionId(sessionInfo);
            TenantId tenantId = getTenantId(sessionInfo);
            DeviceId deviceId = getDeviceId(sessionInfo);
            JsonObject json = new JsonObject();
            json.addProperty("method", msg.getMethodName());
            json.add("params", JsonUtils.parse(msg.getParams()));

            TbMsgMetaData metaData = new TbMsgMetaData();
            metaData.putValue("deviceName", sessionInfo.getDeviceName());
            metaData.putValue("deviceType", sessionInfo.getDeviceType());
            metaData.putValue("requestId", Integer.toString(msg.getRequestId()));
            metaData.putValue("serviceId", serviceInfoProvider.getServiceId());
            metaData.putValue("sessionId", sessionId.toString());
            sendToRuleEngine(tenantId, deviceId, sessionInfo, json, metaData,
                    SessionMsgType.TO_SERVER_RPC_REQUEST, new TransportTbQueueCallback(callback));
            String requestId = sessionId + "-" + msg.getRequestId();
            toServerRpcPendingMap.put(requestId, new RpcRequestMetadata(sessionId, msg.getRequestId()));
            scheduler.schedule(() -> processTimeout(requestId), clientSideRpcTimeout, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.ClaimDeviceMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            reportActivityInternal(sessionInfo);
            sendToDeviceActor(sessionInfo, TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                    .setClaimDevice(msg).build(), callback);
        }
    }

    @Override
    public void reportActivity(TransportProtos.SessionInfoProto sessionInfo) {
        reportActivityInternal(sessionInfo);
    }

    private SessionMetaData reportActivityInternal(TransportProtos.SessionInfoProto sessionInfo) {
        UUID sessionId = toSessionId(sessionInfo);
        SessionMetaData sessionMetaData = sessions.get(sessionId);
        if (sessionMetaData != null) {
            sessionMetaData.updateLastActivityTime();
        }
        return sessionMetaData;
    }

    private void checkInactivityAndReportActivity() {
        long expTime = System.currentTimeMillis() - sessionInactivityTimeout;
        sessions.forEach((uuid, sessionMD) -> {
            long lastActivityTime = sessionMD.getLastActivityTime();
            TransportProtos.SessionInfoProto sessionInfo = sessionMD.getSessionInfo();
            if (sessionInfo.getGwSessionIdMSB() != 0 &&
                    sessionInfo.getGwSessionIdLSB() != 0) {
                SessionMetaData gwMetaData = sessions.get(new UUID(sessionInfo.getGwSessionIdMSB(), sessionInfo.getGwSessionIdLSB()));
                if (gwMetaData != null) {
                    lastActivityTime = Math.max(gwMetaData.getLastActivityTime(), lastActivityTime);
                }
            }
            if (lastActivityTime < expTime) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Session has expired due to last activity time: {}", toSessionId(sessionInfo), lastActivityTime);
                }
                process(sessionInfo, getSessionEventMsg(TransportProtos.SessionEvent.CLOSED), null);
                sessions.remove(uuid);
                sessionMD.getListener().onRemoteSessionCloseCommand(TransportProtos.SessionCloseNotificationProto.getDefaultInstance());
            } else {
                if (lastActivityTime > sessionMD.getLastReportedActivityTime()) {
                    final long lastActivityTimeFinal = lastActivityTime;
                    process(sessionInfo, TransportProtos.SubscriptionInfoProto.newBuilder()
                            .setAttributeSubscription(sessionMD.isSubscribedToAttributes())
                            .setRpcSubscription(sessionMD.isSubscribedToRPC())
                            .setLastActivityTime(lastActivityTime).build(), new TransportServiceCallback<Void>() {
                        @Override
                        public void onSuccess(Void msg) {
                            sessionMD.setLastReportedActivityTime(lastActivityTimeFinal);
                        }

                        @Override
                        public void onError(Throwable e) {
                            log.warn("[{}] Failed to report last activity time", uuid, e);
                        }
                    });
                }
            }
        });
    }

    @Override
    public void registerSyncSession(TransportProtos.SessionInfoProto sessionInfo, SessionMsgListener listener, long timeout) {
        SessionMetaData currentSession = new SessionMetaData(sessionInfo, TransportProtos.SessionType.SYNC, listener);
        sessions.putIfAbsent(toSessionId(sessionInfo), currentSession);

        // session中注册一个删除回告事件
        ScheduledFuture executorFuture = scheduler.schedule(() -> {
            listener.onRemoteSessionCloseCommand(TransportProtos.SessionCloseNotificationProto.getDefaultInstance());
            deregisterSession(sessionInfo);
        }, timeout, TimeUnit.MILLISECONDS);

        currentSession.setScheduledFuture(executorFuture);
    }

    @Override
    public void deregisterSession(TransportProtos.SessionInfoProto sessionInfo) {
        SessionMetaData currentSession = sessions.get(toSessionId(sessionInfo));
        if (currentSession != null && currentSession.hasScheduledFuture()) {
            log.debug("Stopping scheduler to avoid resending response if request has been ack.");
            currentSession.getScheduledFuture().cancel(false);
        }
        sessions.remove(toSessionId(sessionInfo));
    }

    private boolean checkLimits(TransportProtos.SessionInfoProto sessionInfo, Object msg, TransportServiceCallback<Void> callback) {
        return checkLimits(sessionInfo, msg, callback, 0);
    }

    private boolean checkLimits(TransportProtos.SessionInfoProto sessionInfo, Object msg, TransportServiceCallback<Void> callback, int dataPoints) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Processing msg: {}", toSessionId(sessionInfo), msg);
        }
        TenantId tenantId = new TenantId(new UUID(sessionInfo.getTenantIdMSB(), sessionInfo.getTenantIdLSB()));
        DeviceId deviceId = new DeviceId(new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB()));

        //
        EntityType rateLimitedEntityType = rateLimitService.checkLimits(tenantId, deviceId, dataPoints);
        if (rateLimitedEntityType == null) {
            return true;
        } else {
            if (callback != null) {
                callback.onError(new TbRateLimitsException(rateLimitedEntityType));
            }
            return false;
        }
    }

    /**
     * 将响应消息转化为不同传输协议对应的响应内容
     */
    protected void processToTransportMsg(TransportProtos.ToTransportMsg toSessionMsg) {
        // 根据消息生成Session缓存
        UUID sessionId = new UUID(toSessionMsg.getSessionIdMSB(), toSessionMsg.getSessionIdLSB());

        // 判断缓存是否存在
        SessionMetaData md = sessions.get(sessionId);
        if (md != null) {
            SessionMsgListener listener = md.getListener();
            // 传输层回告响应执行器， 根据不同的消息，返回给不同协议传输层对应的消息内容
            transportCallbackExecutor.submit(() -> {
                if (toSessionMsg.hasGetAttributesResponse()) {
                    listener.onGetAttributesResponse(toSessionMsg.getGetAttributesResponse());
                }
                if (toSessionMsg.hasAttributeUpdateNotification()) {
                    listener.onAttributeUpdate(toSessionMsg.getAttributeUpdateNotification());
                }
                if (toSessionMsg.hasSessionCloseNotification()) {
                    listener.onRemoteSessionCloseCommand(toSessionMsg.getSessionCloseNotification());
                }
                if (toSessionMsg.hasToDeviceRequest()) {
                    listener.onToDeviceRpcRequest(toSessionMsg.getToDeviceRequest());
                }
                if (toSessionMsg.hasToServerResponse()) {
                    String requestId = sessionId + "-" + toSessionMsg.getToServerResponse().getRequestId();
                    toServerRpcPendingMap.remove(requestId);
                    listener.onToServerRpcResponse(toSessionMsg.getToServerResponse());
                }
            });

            // 如果是消息是同步而非异步，则删除session
            if (md.getSessionType() == TransportProtos.SessionType.SYNC) {
                deregisterSession(md.getSessionInfo());
            }
        } else {
            // session不存在 --- 消息是否有更新内容
            if (toSessionMsg.hasEntityUpdateMsg()) {
                TransportProtos.EntityUpdateMsg msg = toSessionMsg.getEntityUpdateMsg();
                EntityType entityType = EntityType.valueOf(msg.getEntityType());

                // 判断消息是什么实体，如果是设备
                if (EntityType.DEVICE_PROFILE.equals(entityType)) {
                    DeviceProfile deviceProfile = deviceProfileCache.put(msg.getData());
                    if (deviceProfile != null) {
                        onProfileUpdate(deviceProfile);
                    }
                } else if (EntityType.TENANT_PROFILE.equals(entityType)) {
                    rateLimitService.update(tenantProfileCache.put(msg.getData()));
                } else if (EntityType.TENANT.equals(entityType)) {
                    Optional<Tenant> profileOpt = dataDecodingEncodingService.decode(msg.getData().toByteArray());
                    if (profileOpt.isPresent()) {
                        Tenant tenant = profileOpt.get();
                        boolean updated = tenantProfileCache.put(tenant.getId(), tenant.getTenantProfileId());
                        if (updated) {
                            rateLimitService.update(tenant.getId());
                        }
                    }
                    // 如果是api使用，则更新api消息
                } else if (EntityType.API_USAGE_STATE.equals(entityType)) {
                    Optional<ApiUsageState> stateOpt = dataDecodingEncodingService.decode(msg.getData().toByteArray());
                    if (stateOpt.isPresent()) {
                        ApiUsageState apiUsageState = stateOpt.get();
                        rateLimitService.update(apiUsageState.getTenantId(), apiUsageState.isTransportEnabled());
                        //TODO: if transport is disabled, we should close all sessions and not to check credentials.
                    }
                } else if (EntityType.DEVICE.equals(entityType)) {
                    Optional<Device> deviceOpt = dataDecodingEncodingService.decode(msg.getData().toByteArray());
                    deviceOpt.ifPresent(this::onDeviceUpdate);
                }
                // session不存在 --- 消息是否有删除内容
            } else if (toSessionMsg.hasEntityDeleteMsg()) {
                TransportProtos.EntityDeleteMsg msg = toSessionMsg.getEntityDeleteMsg();
                EntityType entityType = EntityType.valueOf(msg.getEntityType());
                UUID entityUuid = new UUID(msg.getEntityIdMSB(), msg.getEntityIdLSB());
                if (EntityType.DEVICE_PROFILE.equals(entityType)) {
                    deviceProfileCache.evict(new DeviceProfileId(new UUID(msg.getEntityIdMSB(), msg.getEntityIdLSB())));
                } else if (EntityType.TENANT_PROFILE.equals(entityType)) {
                    tenantProfileCache.remove(new TenantProfileId(entityUuid));
                } else if (EntityType.TENANT.equals(entityType)) {
                    rateLimitService.remove(new TenantId(entityUuid));
                } else if (EntityType.DEVICE.equals(entityType)) {
                    rateLimitService.remove(new DeviceId(entityUuid));
                }
            } else {
                //TODO: should we notify the device actor about missed session?
                log.debug("[{}] Missing session.", sessionId);
            }
        }
    }

    // 设备画像更新
    private void onProfileUpdate(DeviceProfile deviceProfile) {
        // 将uuid分成两部分
        long deviceProfileIdMSB = deviceProfile.getId().getId().getMostSignificantBits();
        long deviceProfileIdLSB = deviceProfile.getId().getId().getLeastSignificantBits();

        sessions.forEach((id, md) -> {
            //TODO: if transport types are different - we should close the session.
            if (md.getSessionInfo().getDeviceProfileIdMSB() == deviceProfileIdMSB
                    && md.getSessionInfo().getDeviceProfileIdLSB() == deviceProfileIdLSB) {
                TransportProtos.SessionInfoProto newSessionInfo = TransportProtos.SessionInfoProto.newBuilder()
                        .mergeFrom(md.getSessionInfo())
                        .setDeviceProfileIdMSB(deviceProfileIdMSB)
                        .setDeviceProfileIdLSB(deviceProfileIdLSB)
                        .setDeviceType(deviceProfile.getName())
                        .build();
                md.setSessionInfo(newSessionInfo);
                transportCallbackExecutor.submit(() -> md.getListener().onDeviceProfileUpdate(newSessionInfo, deviceProfile));
            }
        });
    }

    // 设备更新
    private void onDeviceUpdate(Device device) {
        long deviceIdMSB = device.getId().getId().getMostSignificantBits();
        long deviceIdLSB = device.getId().getId().getLeastSignificantBits();
        long deviceProfileIdMSB = device.getDeviceProfileId().getId().getMostSignificantBits();
        long deviceProfileIdLSB = device.getDeviceProfileId().getId().getLeastSignificantBits();
        sessions.forEach((id, md) -> {
            if ((md.getSessionInfo().getDeviceIdMSB() == deviceIdMSB && md.getSessionInfo().getDeviceIdLSB() == deviceIdLSB)) {
                DeviceProfile newDeviceProfile;
                if (md.getSessionInfo().getDeviceProfileIdMSB() != deviceProfileIdMSB
                        && md.getSessionInfo().getDeviceProfileIdLSB() != deviceProfileIdLSB) {
                    //TODO: if transport types are different - we should close the session.
                    newDeviceProfile = deviceProfileCache.get(new DeviceProfileId(new UUID(deviceProfileIdMSB, deviceProfileIdLSB)));
                } else {
                    newDeviceProfile = null;
                }
                TransportProtos.SessionInfoProto newSessionInfo = TransportProtos.SessionInfoProto.newBuilder()
                        .mergeFrom(md.getSessionInfo())
                        .setDeviceProfileIdMSB(deviceProfileIdMSB)
                        .setDeviceProfileIdLSB(deviceProfileIdLSB)
                        .setDeviceName(device.getName())
                        .setDeviceType(device.getType())
                        .build();
                md.setSessionInfo(newSessionInfo);
                transportCallbackExecutor.submit(() -> md.getListener().onDeviceUpdate(newSessionInfo, device, Optional.ofNullable(newDeviceProfile)));
            }
        });
    }

    protected UUID toSessionId(TransportProtos.SessionInfoProto sessionInfo) {
        return new UUID(sessionInfo.getSessionIdMSB(), sessionInfo.getSessionIdLSB());
    }

    protected UUID getRoutingKey(TransportProtos.SessionInfoProto sessionInfo) {
        return new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB());
    }

    protected TenantId getTenantId(TransportProtos.SessionInfoProto sessionInfo) {
        return new TenantId(new UUID(sessionInfo.getTenantIdMSB(), sessionInfo.getTenantIdLSB()));
    }

    protected DeviceId getDeviceId(TransportProtos.SessionInfoProto sessionInfo) {
        return new DeviceId(new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB()));
    }

    public static TransportProtos.SessionEventMsg getSessionEventMsg(TransportProtos.SessionEvent event) {
        return TransportProtos.SessionEventMsg.newBuilder()
                .setSessionType(TransportProtos.SessionType.ASYNC)
                .setEvent(event).build();
    }

    /**
     * 发送设备actor事件
     * @param sessionInfo       session信息
     * @param toDeviceActorMsg  发送的消息体
     * @param callback          回调
     */
    protected void sendToDeviceActor(TransportProtos.SessionInfoProto sessionInfo, TransportToDeviceActorMsg toDeviceActorMsg, TransportServiceCallback<Void> callback) {
        log.info("给tb-core业务层发送消息-TransportService#sendToDeviceActor: {}", gson.toJson(sessionInfo));

        /**
         * 根据session, 算出对应的 业务层core应该将消息发送到哪个partition.
         * 默认是hash算法
         */
        TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_CORE, getTenantId(sessionInfo), getDeviceId(sessionInfo));


        if (log.isTraceEnabled()) {
            log.trace("[{}][{}] Pushing to topic {} message {}", getTenantId(sessionInfo), getDeviceId(sessionInfo), tpi.getFullTopicName(), toDeviceActorMsg);
        }

        TransportTbQueueCallback transportTbQueueCallback = callback != null ?
                new TransportTbQueueCallback(callback) : null;
        // 增加api请求次数统计
        tbCoreProducerStats.incrementTotal();
        StatsCallback wrappedCallback = new StatsCallback(transportTbQueueCallback, tbCoreProducerStats);

        // 将请求发送到tbCore核心层
        tbCoreMsgProducer.send(tpi,
                new TbProtoQueueMsg<>(getRoutingKey(sessionInfo),
                        ToCoreMsg.newBuilder().setToDeviceActorMsg(toDeviceActorMsg).build()),
                wrappedCallback);
    }

    protected void sendToRuleEngine(TenantId tenantId, TbMsg tbMsg, TbQueueCallback callback) {
        TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_RULE_ENGINE, tenantId, tbMsg.getOriginator());
        if (log.isTraceEnabled()) {
            log.trace("[{}][{}] Pushing to topic {} message {}", tenantId, tbMsg.getOriginator(), tpi.getFullTopicName(), tbMsg);
        }
        ToRuleEngineMsg msg = ToRuleEngineMsg.newBuilder().setTbMsg(TbMsg.toByteString(tbMsg))
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits()).build();
        ruleEngineProducerStats.incrementTotal();
        StatsCallback wrappedCallback = new StatsCallback(callback, ruleEngineProducerStats);
        ruleEngineMsgProducer.send(tpi, new TbProtoQueueMsg<>(tbMsg.getId(), msg), wrappedCallback);
    }

    protected void sendToRuleEngine(TenantId tenantId, DeviceId deviceId, TransportProtos.SessionInfoProto sessionInfo, JsonObject json,
                                    TbMsgMetaData metaData, SessionMsgType sessionMsgType, TbQueueCallback callback) {
        DeviceProfileId deviceProfileId = new DeviceProfileId(new UUID(sessionInfo.getDeviceProfileIdMSB(), sessionInfo.getDeviceProfileIdLSB()));
        DeviceProfile deviceProfile = deviceProfileCache.get(deviceProfileId);
        RuleChainId ruleChainId;
        String queueName;

        if (deviceProfile == null) {
            log.warn("[{}] Device profile is null!", deviceProfileId);
            ruleChainId = null;
            queueName = ServiceQueue.MAIN;
        } else {
            ruleChainId = deviceProfile.getDefaultRuleChainId();
            String defaultQueueName = deviceProfile.getDefaultQueueName();
            queueName = defaultQueueName != null ? defaultQueueName : ServiceQueue.MAIN;
        }

        TbMsg tbMsg = TbMsg.newMsg(queueName, sessionMsgType.name(), deviceId, metaData, gson.toJson(json), ruleChainId, null);
        sendToRuleEngine(tenantId, tbMsg, callback);
    }

    private class TransportTbQueueCallback implements TbQueueCallback {
        private final TransportServiceCallback<Void> callback;

        private TransportTbQueueCallback(TransportServiceCallback<Void> callback) {
            this.callback = callback;
        }

        @Override
        public void onSuccess(TbQueueMsgMetadata metadata) {
            DefaultTransportService.this.transportCallbackExecutor.submit(() -> callback.onSuccess(null));
        }

        @Override
        public void onFailure(Throwable t) {
            DefaultTransportService.this.transportCallbackExecutor.submit(() -> callback.onError(t));
        }
    }

    private static class StatsCallback implements TbQueueCallback {
        private final TbQueueCallback callback;
        private final MessagesStats stats;

        private StatsCallback(TbQueueCallback callback, MessagesStats stats) {
            this.callback = callback;
            this.stats = stats;
        }

        @Override
        public void onSuccess(TbQueueMsgMetadata metadata) {
            stats.incrementSuccessful();
            if (callback != null)
                callback.onSuccess(metadata);
        }

        @Override
        public void onFailure(Throwable t) {
            stats.incrementFailed();
            if (callback != null)
                callback.onFailure(t);
        }
    }

    private class MsgPackCallback implements TbQueueCallback {
        private final AtomicInteger msgCount;
        private final TransportServiceCallback<Void> callback;

        public MsgPackCallback(Integer msgCount, TransportServiceCallback<Void> callback) {
            this.msgCount = new AtomicInteger(msgCount);
            this.callback = callback;
        }

        @Override
        public void onSuccess(TbQueueMsgMetadata metadata) {
            if (msgCount.decrementAndGet() <= 0) {
                DefaultTransportService.this.transportCallbackExecutor.submit(() -> callback.onSuccess(null));
            }
        }

        @Override
        public void onFailure(Throwable t) {
            DefaultTransportService.this.transportCallbackExecutor.submit(() -> callback.onError(t));
        }
    }

    private class ApiStatsProxyCallback<T> implements TransportServiceCallback<T> {
        private final TenantId tenantId;
        private final int dataPoints;
        private final TransportServiceCallback<T> callback;

        public ApiStatsProxyCallback(TenantId tenantId, int dataPoints, TransportServiceCallback<T> callback) {
            this.tenantId = tenantId;
            this.dataPoints = dataPoints;
            this.callback = callback;
        }

        @Override
        public void onSuccess(T msg) {
            try {
                apiUsageClient.report(tenantId, ApiUsageRecordKey.TRANSPORT_MSG_COUNT, 1);
                apiUsageClient.report(tenantId, ApiUsageRecordKey.TRANSPORT_DP_COUNT, dataPoints);
            } finally {
                callback.onSuccess(msg);
            }
        }

        @Override
        public void onError(Throwable e) {
            callback.onError(e);
        }
    }
}
