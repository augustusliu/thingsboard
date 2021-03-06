package org.thingsboard.server.actors.app;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.actors.ActorSystemContext;
import org.thingsboard.server.actors.TbActor;
import org.thingsboard.server.actors.TbActorId;
import org.thingsboard.server.actors.TbActorRef;
import org.thingsboard.server.actors.TbEntityActorId;
import org.thingsboard.server.actors.service.ContextAwareActor;
import org.thingsboard.server.actors.service.ContextBasedCreator;
import org.thingsboard.server.actors.service.DefaultActorService;
import org.thingsboard.server.actors.tenant.TenantActor;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageDataIterable;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.msg.MsgType;
import org.thingsboard.server.common.msg.TbActorMsg;
import org.thingsboard.server.common.msg.aware.TenantAwareMsg;
import org.thingsboard.server.common.msg.plugin.ComponentLifecycleMsg;
import org.thingsboard.server.common.msg.queue.QueueToRuleEngineMsg;
import org.thingsboard.server.common.msg.queue.RuleEngineException;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.dao.tenant.TbTenantProfileCache;
import org.thingsboard.server.service.transport.msg.TransportToDeviceActorMsgWrapper;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 整个项目的actor，用于监控项目
 */
@Slf4j
public class AppActor extends ContextAwareActor {

    // 所有租户的配置画像
    private final TbTenantProfileCache tenantProfileCache;
    // 租户服务
    private final TenantService tenantService;
    // 已经删除的租户信息
    private final Set<TenantId> deletedTenants;
    // 规则引擎是否已经初始化
    private boolean ruleChainsInitialized;

    private AppActor(ActorSystemContext systemContext) {
        super(systemContext);
        this.tenantProfileCache = systemContext.getTenantProfileCache();
        this.tenantService = systemContext.getTenantService();
        this.deletedTenants = new HashSet<>();
    }

    @Override
    protected boolean doProcess(TbActorMsg msg) {
        if (!ruleChainsInitialized) {
            initTenantActors();
            ruleChainsInitialized = true;
            if (msg.getMsgType() != MsgType.APP_INIT_MSG) {
                log.warn("Rule Chains initialized by unexpected message: {}", msg);
            }
        }
        switch (msg.getMsgType()) {
            case APP_INIT_MSG:
                break;
            case PARTITION_CHANGE_MSG:
                ctx.broadcastToChildren(msg);
                break;
            case COMPONENT_LIFE_CYCLE_MSG:
                onComponentLifecycleMsg((ComponentLifecycleMsg) msg);
                break;
            case QUEUE_TO_RULE_ENGINE_MSG:
                onQueueToRuleEngineMsg((QueueToRuleEngineMsg) msg);
                break;
            case TRANSPORT_TO_DEVICE_ACTOR_MSG:
                onToDeviceActorMsg((TenantAwareMsg) msg, false);
                break;
            case DEVICE_ATTRIBUTES_UPDATE_TO_DEVICE_ACTOR_MSG:
            case DEVICE_CREDENTIALS_UPDATE_TO_DEVICE_ACTOR_MSG:
            case DEVICE_NAME_OR_TYPE_UPDATE_TO_DEVICE_ACTOR_MSG:
            case DEVICE_RPC_REQUEST_TO_DEVICE_ACTOR_MSG:
            case SERVER_RPC_RESPONSE_TO_DEVICE_ACTOR_MSG:
                onToDeviceActorMsg((TenantAwareMsg) msg, true);
                break;
            default:
                return false;
        }
        return true;
    }

    // 负责租户相关事项
    private void initTenantActors() {
        log.info("AppActor#initTenantActors初始化系统actor，Starting main system actor.");
        try {
            // This Service may be started for specific tenant only.
            // 判断该租户是否是独立占用该服务。如果是，则只加载当前租户的子actor信息；否则加载1024个租户的子actor信息
            Optional<TenantId> isolatedTenantId = systemContext.getServiceInfoProvider().getIsolatedTenant();
            if (isolatedTenantId.isPresent()) {
                Tenant tenant = systemContext.getTenantService().findTenantById(isolatedTenantId.get());
                if (tenant != null) {
                    log.debug("[{}] Creating tenant actor", tenant.getId());
                    getOrCreateTenantActor(tenant.getId());
                    log.debug("Tenant actor created.");
                } else {
                    log.error("[{}] Tenant with such ID does not exist", isolatedTenantId.get());
                }
            } else if (systemContext.isTenantComponentsInitEnabled()) {
                PageDataIterable<Tenant> tenantIterator = new PageDataIterable<>(tenantService::findTenants, ENTITY_PACK_LIMIT);
                boolean isRuleEngine = systemContext.getServiceInfoProvider().isService(ServiceType.TB_RULE_ENGINE);
                boolean isCore = systemContext.getServiceInfoProvider().isService(ServiceType.TB_CORE);
                for (Tenant tenant : tenantIterator) {
                    TenantProfile tenantProfile = tenantProfileCache.get(tenant.getTenantProfileId());
                    if (isCore || (isRuleEngine && !tenantProfile.isIsolatedTbRuleEngine())) {
                        log.debug("[{}] Creating tenant actor", tenant.getId());
                        getOrCreateTenantActor(tenant.getId());
                        log.debug("[{}] Tenant actor created.", tenant.getId());
                    }
                }
            }
            log.info("Main system actor started.");
        } catch (Exception e) {
            log.warn("Unknown failure", e);
        }
    }

    private void onQueueToRuleEngineMsg(QueueToRuleEngineMsg msg) {
        if (TenantId.SYS_TENANT_ID.equals(msg.getTenantId())) {
            msg.getTbMsg().getCallback().onFailure(new RuleEngineException("Message has system tenant id!"));
        } else {
            if (!deletedTenants.contains(msg.getTenantId())) {
                getOrCreateTenantActor(msg.getTenantId()).tell(msg);
            } else {
                msg.getTbMsg().getCallback().onSuccess();
            }
        }
    }

    private void onComponentLifecycleMsg(ComponentLifecycleMsg msg) {
        TbActorRef target = null;
        if (TenantId.SYS_TENANT_ID.equals(msg.getTenantId())) {
            if (!EntityType.TENANT_PROFILE.equals(msg.getEntityId().getEntityType())) {
                log.warn("Message has system tenant id: {}", msg);
            }
        } else {
            if (EntityType.TENANT.equals(msg.getEntityId().getEntityType())) {
                TenantId tenantId = new TenantId(msg.getEntityId().getId());
                if (msg.getEvent() == ComponentLifecycleEvent.DELETED) {
                    log.info("[{}] Handling tenant deleted notification: {}", msg.getTenantId(), msg);
                    deletedTenants.add(tenantId);
                    ctx.stop(new TbEntityActorId(tenantId));
                } else {
                    target = getOrCreateTenantActor(msg.getTenantId());
                }
            } else {
                target = getOrCreateTenantActor(msg.getTenantId());
            }
        }
        if (target != null) {
            target.tellWithHighPriority(msg);
        } else {
            log.debug("[{}] Invalid component lifecycle msg: {}", msg.getTenantId(), msg);
        }
    }

    private void onToDeviceActorMsg(TenantAwareMsg msg, boolean priority) {
        if (!deletedTenants.contains(msg.getTenantId())) {
            TbActorRef tenantActor = getOrCreateTenantActor(msg.getTenantId());
            if (priority) {
                tenantActor.tellWithHighPriority(msg);
            } else {
                tenantActor.tell(msg);
            }
        } else {
            if (msg instanceof TransportToDeviceActorMsgWrapper) {
                ((TransportToDeviceActorMsgWrapper) msg).getCallback().onSuccess();
            }
        }
    }

    private TbActorRef getOrCreateTenantActor(TenantId tenantId) {
        return ctx.getOrCreateChildActor(new TbEntityActorId(tenantId),
                () -> DefaultActorService.TENANT_DISPATCHER_NAME,
                () -> new TenantActor.ActorCreator(systemContext, tenantId));
    }

    public static class ActorCreator extends ContextBasedCreator {

        public ActorCreator(ActorSystemContext context) {
            super(context);
        }

        @Override
        public TbActorId createActorId() {
            return new TbEntityActorId(new TenantId(EntityId.NULL_UUID));
        }

        @Override
        public TbActor createActor() {
            return new AppActor(context);
        }
    }

}
