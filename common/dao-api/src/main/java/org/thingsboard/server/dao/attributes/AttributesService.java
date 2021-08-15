package org.thingsboard.server.dao.attributes;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * <pre>
 *     物模型相关服务
 *
 *     关联的表为 attribute_kv
 * </pre>
 */
public interface AttributesService {

    /**
     *  根据属性的Key 查询属性的KV内容
     */
    ListenableFuture<Optional<AttributeKvEntry>> find(TenantId tenantId, EntityId entityId, String scope, String attributeKey);

    /**
     *  批量查询属性的KV内容
     */
    ListenableFuture<List<AttributeKvEntry>> find(TenantId tenantId, EntityId entityId, String scope, Collection<String> attributeKeys);

    /**
     *  查询某个实体的物模型
     */
    ListenableFuture<List<AttributeKvEntry>> findAll(TenantId tenantId, EntityId entityId, String scope);

    /**
     *  保存物模型
     */
    ListenableFuture<List<Void>> save(TenantId tenantId, EntityId entityId, String scope, List<AttributeKvEntry> attributes);

    ListenableFuture<List<Void>> removeAll(TenantId tenantId, EntityId entityId, String scope, List<String> attributeKeys);

    /**
     *  根据设备画像查询物模型中所有属性的key
     */
    List<String> findAllKeysByDeviceProfileId(TenantId tenantId, DeviceProfileId deviceProfileId);

    /**
     *  根据实体id，批量查询
     */
    List<String> findAllKeysByEntityIds(TenantId tenantId, EntityType entityType, List<EntityId> entityIds);

}
