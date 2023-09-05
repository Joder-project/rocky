package org.alps.rocky.server.core;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import lombok.extern.slf4j.Slf4j;
import org.alps.core.*;
import org.alps.core.frame.RoutingFrame;
import org.alps.rocky.core.proto.RoutingClient;
import org.alps.rocky.core.proto.RoutingCommon;
import org.alps.rocky.core.proto.RoutingErrors;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
public class RockyRoutingFrameRouter implements FrameListener {

    private final Map<RoutingCommon.FrameType, RouterFrameHandler<? extends MessageLite>> handlers;

    RockyRoutingFrameRouter(ModuleNotification moduleNotification, Register register) {
        this.handlers = Map.of(
                RoutingCommon.FrameType.C_Connect, new ConnectRouterFrameHandler(moduleNotification, register),
                RoutingCommon.FrameType.C_HealthIdle, new HealthRouterFrameHandler(register),
                RoutingCommon.FrameType.C_Active, new ActiveRouterFrameHandler(register),
                RoutingCommon.FrameType.C_Disable, new InactiveRouterFrameHandler(register)
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public void listen(AlpsSession session, Frame frame) {
        var rFrame = (RoutingFrame) frame;
        var data = rFrame.frameData();
        if (data == null) {
            return;
        }
        try {
            var routingFrame = RoutingCommon.RoutingFrame.parseFrom(data);
            if (!handlers.containsKey(routingFrame.getType())) {
                log.error("router receive an unknown frame type. {}", routingFrame.getType());
                return;
            }
            var routerFrameHandler = (RouterFrameHandler<MessageLite>) handlers.get(routingFrame.getType());
            routerFrameHandler.handle(session, routerFrameHandler.decode(routingFrame.getFrame()));
        } catch (Exception ex) {
            log.error("router receive error", ex);
        }
    }
}

interface RouterFrameHandler<T extends MessageLite> {
    String INSTANCE_KEY = "InstanceKey";
    String NAMESPACE_KEY = "NamespaceKey";
    String MODULE_KEY = "ModuleKey";

    T decode(ByteString data) throws Exception;

    void handle(AlpsSession session, T frame) throws Exception;
}

class ConnectRouterFrameHandler implements RouterFrameHandler<RoutingClient.ServiceInfo> {

    private final ModuleNotification moduleNotification;
    private final Register register;

    ConnectRouterFrameHandler(ModuleNotification moduleNotification, Register register) {
        this.moduleNotification = moduleNotification;
        this.register = register;
    }

    @Override
    public RoutingClient.ServiceInfo decode(ByteString data) throws Exception {
        return RoutingClient.ServiceInfo.parseFrom(data);
    }

    @Override
    public void handle(AlpsSession session, RoutingClient.ServiceInfo frame) throws Exception {
        var accessKey = frame.getAccessKey();
        if (!isValid(accessKey)) {
            ((AlpsEnhancedSession) session).error()
                    .code(RoutingErrors.Code.ACCESS_KEY_INVALID_VALUE)
                    .send().subscribe();
            return;
        }
        var instanceId = frame.getInstanceId();
        var namespace = frame.getNamespace();
        var instance = new InstanceInfo(frame.getProfile(), instanceId, frame.getIp(), frame.getPort(), false);
        var modules = frame.getModulesList().stream().toList();
        session.attr(INSTANCE_KEY, instanceId);
        session.attr(NAMESPACE_KEY, namespace);
        session.attr(MODULE_KEY, modules);
        moduleNotification.register(namespace, instanceId, session);
        register.registerInstance(new RegisterInstanceInfo(namespace, instance, modules, frame.getMsgMap()));
    }

    /**
     * 验证是否可以连接
     *
     * @param accessKey 密钥
     */
    private boolean isValid(String accessKey) {
        // TODO
        return true;
    }

}

class HealthRouterFrameHandler implements RouterFrameHandler<RoutingClient.HealthIdleInfo> {

    private final Register register;

    HealthRouterFrameHandler(Register register) {
        this.register = register;
    }

    @Override
    public RoutingClient.HealthIdleInfo decode(ByteString data) throws Exception {
        return RoutingClient.HealthIdleInfo.parseFrom(data);
    }

    @Override
    public void handle(AlpsSession session, RoutingClient.HealthIdleInfo frame) throws Exception {
        String instanceId = session.attr(INSTANCE_KEY);
        if (instanceId == null) {
            return;
        }
        String namespace = session.attr(NAMESPACE_KEY);
        register.updateInstance(namespace, instanceId, frame.getMsgMap());
    }
}

class ActiveRouterFrameHandler implements RouterFrameHandler<RoutingClient.UpServiceInfo> {

    private final Register register;

    ActiveRouterFrameHandler(Register register) {
        this.register = register;
    }

    @Override
    public RoutingClient.UpServiceInfo decode(ByteString data) throws Exception {
        return RoutingClient.UpServiceInfo.parseFrom(data);
    }

    @Override
    public void handle(AlpsSession session, RoutingClient.UpServiceInfo frame) throws Exception {
        String instanceId = session.attr(INSTANCE_KEY);
        if (instanceId == null) {
            return;
        }
        String namespace = session.attr(NAMESPACE_KEY);
        register.updateInstance(namespace, instanceId, frame.getMsgMap(), true);
    }
}

class InactiveRouterFrameHandler implements RouterFrameHandler<RoutingClient.DownServiceInfo> {

    private final Register register;

    InactiveRouterFrameHandler(Register register) {
        this.register = register;
    }

    @Override
    public RoutingClient.DownServiceInfo decode(ByteString data) throws Exception {
        return RoutingClient.DownServiceInfo.parseFrom(data);
    }

    @Override
    public void handle(AlpsSession session, RoutingClient.DownServiceInfo frame) throws Exception {
        String instanceId = session.attr(INSTANCE_KEY);
        if (instanceId == null) {
            return;
        }
        String namespace = session.attr(NAMESPACE_KEY);
        register.updateInstance(namespace, instanceId, frame.getMsgMap(), false);
    }
}

@Component
@Slf4j
class RockySessionListener implements SessionListener {

    static final String INSTANCE_KEY = "InstanceKey";
    static final String NAMESPACE_KEY = "NamespaceKey";
    private final Register register;
    private final ModuleNotification moduleNotification;

    RockySessionListener(Register register, ModuleNotification moduleNotification) {
        this.register = register;
        this.moduleNotification = moduleNotification;
    }

    @Override
    public void connect(AlpsSession session) {

    }

    @Override
    public void disconnect(AlpsSession session) {
        try {
            String instanceId = session.attr(INSTANCE_KEY);
            if (instanceId == null) {
                return;
            }
            String namespace = session.attr(NAMESPACE_KEY);
            register.removeInstance(namespace, instanceId);
            moduleNotification.unRegister(namespace, instanceId);
        } catch (Exception ex) {
            log.error("监听断开事件异常", ex);
        }
    }
}
