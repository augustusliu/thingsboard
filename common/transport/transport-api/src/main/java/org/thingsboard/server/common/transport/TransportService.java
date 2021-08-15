package org.thingsboard.server.common.transport;

import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.transport.auth.GetOrCreateDeviceFromGatewayResponse;
import org.thingsboard.server.common.transport.auth.ValidateDeviceCredentialsResponse;
import org.thingsboard.server.gen.transport.TransportProtos.ClaimDeviceMsg;
import org.thingsboard.server.gen.transport.TransportProtos.GetAttributeRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.GetEntityProfileRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.GetEntityProfileResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.GetOrCreateDeviceFromGatewayRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.PostAttributeMsg;
import org.thingsboard.server.gen.transport.TransportProtos.PostTelemetryMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ProvisionDeviceRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ProvisionDeviceResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.SessionEventMsg;
import org.thingsboard.server.gen.transport.TransportProtos.SessionInfoProto;
import org.thingsboard.server.gen.transport.TransportProtos.SubscribeToAttributeUpdatesMsg;
import org.thingsboard.server.gen.transport.TransportProtos.SubscribeToRPCMsg;
import org.thingsboard.server.gen.transport.TransportProtos.SubscriptionInfoProto;
import org.thingsboard.server.gen.transport.TransportProtos.ToDeviceRpcResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToServerRpcRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ValidateBasicMqttCredRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ValidateDeviceTokenRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ValidateDeviceX509CertRequestMsg;

/**
 * Created by ashvayka on 04.10.18.
 *
 * 传输层异步架构的交互方式
 * 具体的实现方式一定要十分清楚，这个类很重要
 */
public interface TransportService {

    /**
     * 根据一个实体消息的请求，返回对应的响应数据
     * @param msg  实体消息
     */
    GetEntityProfileResponseMsg getEntityProfile(GetEntityProfileRequestMsg msg);

    /**
     * 基于token,处理请求
     * @param transportType 传输协议
     * @param msg           消息类型  Token
     * @param callback      回调函数
     */
    void process(DeviceTransportType transportType, ValidateDeviceTokenRequestMsg msg,
                 TransportServiceCallback<ValidateDeviceCredentialsResponse> callback);

    /**
     * 处理设备Basic消息
     * @param transportType 传输协议
     * @param msg           消息类型  Basic
     * @param callback      回调函数
     */
    void process(DeviceTransportType transportType, ValidateBasicMqttCredRequestMsg msg,
                 TransportServiceCallback<ValidateDeviceCredentialsResponse> callback);

    /**
     * 处理设备X509消息
     * @param transportType 传输协议
     * @param msg           消息类型  x509
     * @param callback      回调函数
     */
    void process(DeviceTransportType transportType, ValidateDeviceX509CertRequestMsg msg,
                 TransportServiceCallback<ValidateDeviceCredentialsResponse> callback);

    /**
     * 终端网关设备添加获取或者添加子设备消息(通知服务端添加设备)
     * @param msg           消息类型  网关添加子设备后，通知服务端添加设备的消息
     * @param callback      回调函数
     */
    void process(GetOrCreateDeviceFromGatewayRequestMsg msg,
                 TransportServiceCallback<GetOrCreateDeviceFromGatewayResponse> callback);

    /**
     * 查询设备状态 认证信息等请求
     * @param msg       请求设备的消息
     * @param callback  回调函数
     */
    void process(ProvisionDeviceRequestMsg msg,
                 TransportServiceCallback<ProvisionDeviceResponseMsg> callback);


    /**
     * 设备与服务器的连接后，Session事件状态变更处理
     * @param sessionInfo   session
     * @param msg           session事件
     * @param callback      回调函数
     */
    void process(SessionInfoProto sessionInfo, SessionEventMsg msg, TransportServiceCallback<Void> callback);

    /**
     * 设备与服务器长连接后，设备遥测消息
     * @param sessionInfo   session
     * @param msg           遥测数据
     * @param callback      回调函数
     */
    void process(SessionInfoProto sessionInfo, PostTelemetryMsg msg, TransportServiceCallback<Void> callback);

    void process(SessionInfoProto sessionInfo, PostAttributeMsg msg, TransportServiceCallback<Void> callback);

    void process(SessionInfoProto sessionInfo, GetAttributeRequestMsg msg, TransportServiceCallback<Void> callback);

    void process(SessionInfoProto sessionInfo, SubscribeToAttributeUpdatesMsg msg, TransportServiceCallback<Void> callback);

    void process(SessionInfoProto sessionInfo, SubscribeToRPCMsg msg, TransportServiceCallback<Void> callback);

    void process(SessionInfoProto sessionInfo, ToDeviceRpcResponseMsg msg, TransportServiceCallback<Void> callback);

    void process(SessionInfoProto sessionInfo, ToServerRpcRequestMsg msg, TransportServiceCallback<Void> callback);

    void process(SessionInfoProto sessionInfo, SubscriptionInfoProto msg, TransportServiceCallback<Void> callback);

    void process(SessionInfoProto sessionInfo, ClaimDeviceMsg msg, TransportServiceCallback<Void> callback);

    /**
     * 注册异步session处理
     * @param sessionInfo   session
     * @param listener      注册的监听器
     */
    void registerAsyncSession(SessionInfoProto sessionInfo, SessionMsgListener listener);

    /**
     * 同步注册一个新的session
     */
    void registerSyncSession(SessionInfoProto sessionInfo, SessionMsgListener listener, long timeout);

    /**
     * 报告session当前状态,用于设备状态收集
     * @param sessionInfo   session
     */
    void reportActivity(SessionInfoProto sessionInfo);

    /**
     * session销毁
     * @param sessionInfo session
     */
    void deregisterSession(SessionInfoProto sessionInfo);
}
