package org.thingsboard.server.actors.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.actors.ActorSystemContext;
import org.thingsboard.server.actors.DefaultTbActorSystem;
import org.thingsboard.server.actors.TbActorId;
import org.thingsboard.server.actors.TbActorRef;
import org.thingsboard.server.actors.TbActorSystem;
import org.thingsboard.server.actors.TbActorSystemSettings;
import org.thingsboard.server.actors.app.AppActor;
import org.thingsboard.server.actors.app.AppInitMsg;
import org.thingsboard.server.actors.stats.StatsActor;
import org.thingsboard.server.common.msg.queue.PartitionChangeMsg;
import org.thingsboard.server.queue.discovery.PartitionChangeEvent;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Service
@Slf4j
public class DefaultActorService implements ActorService {

    /**
     * 当前Actor系统所有的 分发系统类型
     */
    public static final String APP_DISPATCHER_NAME = "app-dispatcher";
    public static final String TENANT_DISPATCHER_NAME = "tenant-dispatcher";
    public static final String DEVICE_DISPATCHER_NAME = "device-dispatcher";
    public static final String RULE_DISPATCHER_NAME = "rule-dispatcher";

    @Autowired
    private ActorSystemContext actorContext;

    private TbActorSystem system;

    private TbActorRef appActor;

    /**
     * actor吞吐量
     */
    @Value("${actors.system.throughput:5}")
    private int actorThroughput;

    @Value("${actors.system.max_actor_init_attempts:10}")
    private int maxActorInitAttempts;

    /**
     * actor系统调度线程池大小
     */
    @Value("${actors.system.scheduler_pool_size:1}")
    private int schedulerPoolSize;

    /**
     * app分发器个数
     */
    @Value("${actors.system.app_dispatcher_pool_size:1}")
    private int appDispatcherSize;

    /**
     * 租户分发器个数
     */
    @Value("${actors.system.tenant_dispatcher_pool_size:2}")
    private int tenantDispatcherSize;

    /**
     * 设备分发器个数
     */
    @Value("${actors.system.device_dispatcher_pool_size:4}")
    private int deviceDispatcherSize;

    /**
     * 规则分发器其个数
     */
    @Value("${actors.system.rule_dispatcher_pool_size:4}")
    private int ruleDispatcherSize;

    @PostConstruct
    public void initActorSystem() {
        log.info("ActorService#initActorSystem  Actor模型启动......");
        actorContext.setActorService(this);
        TbActorSystemSettings settings = new TbActorSystemSettings(actorThroughput, schedulerPoolSize, maxActorInitAttempts);
        log.info("ActorService#initActorSystem  创建ActorSystem......");
        system = new DefaultTbActorSystem(settings);

        log.info("ActorService#initActorSystem  创建Actor Dispatchers {} ,{}, {}, {}", appDispatcherSize, tenantDispatcherSize, deviceDispatcherSize, ruleDispatcherSize);
        system.createDispatcher(APP_DISPATCHER_NAME, initDispatcherExecutor(APP_DISPATCHER_NAME, appDispatcherSize));
        system.createDispatcher(TENANT_DISPATCHER_NAME, initDispatcherExecutor(TENANT_DISPATCHER_NAME, tenantDispatcherSize));
        system.createDispatcher(DEVICE_DISPATCHER_NAME, initDispatcherExecutor(DEVICE_DISPATCHER_NAME, deviceDispatcherSize));
        system.createDispatcher(RULE_DISPATCHER_NAME, initDispatcherExecutor(RULE_DISPATCHER_NAME, ruleDispatcherSize));

        actorContext.setActorSystem(system);
        log.info("ActorService#initActorSystem  创建 APPActor, Dispatcher = {}", APP_DISPATCHER_NAME);
        appActor = system.createRootActor(APP_DISPATCHER_NAME, new AppActor.ActorCreator(actorContext));
        actorContext.setAppActor(appActor);
        log.info("ActorService#initActorSystem  创建 StatsActor, Dispatcher = {}", TENANT_DISPATCHER_NAME);
        TbActorRef statsActor = system.createRootActor(TENANT_DISPATCHER_NAME, new StatsActor.ActorCreator(actorContext, "StatsActor"));
        actorContext.setStatsActor(statsActor);

        log.info("ActorService#initActorSystem Actor模型启动成功......");
    }

    private ExecutorService initDispatcherExecutor(String dispatcherName, int poolSize) {
        if (poolSize == 0) {
            int cores = Runtime.getRuntime().availableProcessors();
            poolSize = Math.max(1, cores / 2);
        }
        if (poolSize == 1) {
            return Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName(dispatcherName));
        } else {
            return Executors.newWorkStealingPool(poolSize);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(value = 2)
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        log.info("ActorService#onApplicationEvent 监听到系统准备完成事件, 向actor模型发送ApplicationReadyEvent消息.....");
        appActor.tellWithHighPriority(new AppInitMsg());
    }

    @EventListener(PartitionChangeEvent.class)
    public void onApplicationEvent(PartitionChangeEvent partitionChangeEvent) {
        log.info("ActorService#onApplicationEvent 监听消息对列partition改变事件, 向actor模型发送PartitionChangeEvent消息.....");
        this.appActor.tellWithHighPriority(new PartitionChangeMsg(partitionChangeEvent.getServiceQueueKey(), partitionChangeEvent.getPartitions()));
    }

    @PreDestroy
    public void stopActorSystem() {
        if (system != null) {
            log.info("Stopping actor system.");
            system.stop();
            log.info("Actor system stopped.");
        }
    }

}
