package org.thingsboard.server.dao.device;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceInfo;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.device.DeviceSearchQuery;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.device.provision.ProvisionRequest;

import java.util.List;

public interface DeviceService {

    /**
     * <pre>
     *     根据设备ID及租户ID获取设备信息
     *     设备信息包含了设备全部内容
     * </pre>
     * @param tenantId  租户ID
     * @param deviceId  设备ID
     */
    DeviceInfo findDeviceInfoById(TenantId tenantId, DeviceId deviceId);

    /**
     * <pre>
     *     根据设备ID及租户ID 查询设备表基础信息
     * </pre>
     * @param tenantId  租户ID
     * @param deviceId  设备ID
     */
    Device findDeviceById(TenantId tenantId, DeviceId deviceId);

    /**
     * <pre>
     *     异步获取设备基本信息
     * </pre>
     * @param tenantId  租户ID
     * @param deviceId  设备ID
     */
    ListenableFuture<Device> findDeviceByIdAsync(TenantId tenantId, DeviceId deviceId);

    /**
     * <pre>
     *     根据租户id和设备名称，获取设备基本信息
     * </pre>
     * @param tenantId  租户ID
     * @param name      设备名称
     */
    Device findDeviceByTenantIdAndName(TenantId tenantId, String name);

    Device saveDevice(Device device);

    /**
     * <pre>
     *     保存设备信息及其授权码
     * </pre>
     * @param device        设备信息
     * @param accessToken   设备授权码
     */
    Device saveDeviceWithAccessToken(Device device, String accessToken);

    /**
     * <pre>
     *     将设备授权给某客户
     * </pre>
     * @param tenantId     租户id
     * @param deviceId     设备id
     * @param customerId   客户id
     */
    Device assignDeviceToCustomer(TenantId tenantId, DeviceId deviceId, CustomerId customerId);

    /**
     * <pre>
     *     取消某设备的对客户的授权
     * </pre>
     * @param tenantId     租户id
     * @param deviceId     设备id
     */
    Device unassignDeviceFromCustomer(TenantId tenantId, DeviceId deviceId);


    /**
     * <pre>
     *     删除设备
     * </pre>
     * @param tenantId     租户id
     * @param deviceId     设备id
     */
    void deleteDevice(TenantId tenantId, DeviceId deviceId);

    /**
     * <pre>
     *     分页查询租户下的所有设备
     * </pre>
     * @param tenantId     租户id
     */
    PageData<Device> findDevicesByTenantId(TenantId tenantId, PageLink pageLink);

    PageData<DeviceInfo> findDeviceInfosByTenantId(TenantId tenantId, PageLink pageLink);

    PageData<Device> findDevicesByTenantIdAndType(TenantId tenantId, String type, PageLink pageLink);

    PageData<DeviceInfo> findDeviceInfosByTenantIdAndType(TenantId tenantId, String type, PageLink pageLink);

    PageData<DeviceInfo> findDeviceInfosByTenantIdAndDeviceProfileId(TenantId tenantId, DeviceProfileId deviceProfileId, PageLink pageLink);

    /**
     * <pre>
     *     异步查询设备列表
     * </pre>
     * @param tenantId      租户id
     * @param deviceIds     设备id集合
     */
    ListenableFuture<List<Device>> findDevicesByTenantIdAndIdsAsync(TenantId tenantId, List<DeviceId> deviceIds);


    void deleteDevicesByTenantId(TenantId tenantId);

    PageData<Device> findDevicesByTenantIdAndCustomerId(TenantId tenantId, CustomerId customerId, PageLink pageLink);

    PageData<DeviceInfo> findDeviceInfosByTenantIdAndCustomerId(TenantId tenantId, CustomerId customerId, PageLink pageLink);

    PageData<Device> findDevicesByTenantIdAndCustomerIdAndType(TenantId tenantId, CustomerId customerId, String type, PageLink pageLink);

    PageData<DeviceInfo> findDeviceInfosByTenantIdAndCustomerIdAndType(TenantId tenantId, CustomerId customerId, String type, PageLink pageLink);

    PageData<DeviceInfo> findDeviceInfosByTenantIdAndCustomerIdAndDeviceProfileId(TenantId tenantId, CustomerId customerId, DeviceProfileId deviceProfileId, PageLink pageLink);

    ListenableFuture<List<Device>> findDevicesByTenantIdCustomerIdAndIdsAsync(TenantId tenantId, CustomerId customerId, List<DeviceId> deviceIds);

    void unassignCustomerDevices(TenantId tenantId, CustomerId customerId);

    ListenableFuture<List<Device>> findDevicesByQuery(TenantId tenantId, DeviceSearchQuery query);

    ListenableFuture<List<EntitySubtype>> findDeviceTypesByTenantId(TenantId tenantId);

    Device assignDeviceToTenant(TenantId tenantId, Device device);

    Device saveDevice(ProvisionRequest provisionRequest, DeviceProfile profile);

}
