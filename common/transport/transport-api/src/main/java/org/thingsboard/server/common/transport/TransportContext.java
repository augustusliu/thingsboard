package org.thingsboard.server.common.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.thingsboard.server.queue.discovery.TbServiceInfoProvider;
import org.thingsboard.server.queue.scheduler.SchedulerComponent;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by ashvayka on 15.10.18.
 * <pre>
 *     http传输层的上下文环境
 *
 *     只有当该实例启动时为tb-transport 传输层服务时，或者 该服务时集成服务，并且启用了api功能，才加载该类
 * </pre>
 */
@Slf4j
@Data
@Service
@ConditionalOnExpression("'${service.type:null}'=='tb-transport' || ('${service.type:null}'=='monolith' && '${transport.api_enabled:true}'=='true')")
public abstract class TransportContext {

    protected final ObjectMapper mapper = new ObjectMapper();

    // 传输层服务
    @Resource
    private TransportService transportService;
    // 服务提供者
    @Resource
    private TbServiceInfoProvider serviceInfoProvider;
    // 调度组件
    @Resource
    private SchedulerComponent scheduler;
    /**
     * 执行器---采用了抢占式执行器，50个线程
     */
    @Getter
    private ExecutorService executor;

    /**
     * 传输层上下文环境
     */
    @PostConstruct
    public void init() {
        executor = Executors.newWorkStealingPool(50);
    }

    @PreDestroy
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * 获取传输层节点id，传输层可以配置集群
     */
    public String getNodeId() {
        return serviceInfoProvider.getServiceId();
    }

}
