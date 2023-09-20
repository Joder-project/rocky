package org.alps.rocky.client.core;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import lombok.extern.slf4j.Slf4j;
import org.alps.core.AlpsSession;
import org.alps.core.Frame;
import org.alps.core.FrameListener;
import org.alps.core.frame.RoutingFrame;
import org.alps.rocky.core.proto.RoutingCommon;
import org.alps.rocky.core.proto.RoutingServer;

import java.util.Map;

@Slf4j
public class RockyClientFrameListener implements FrameListener {
    private final Map<RoutingCommon.FrameType, RouterFrameHandler<? extends MessageLite>> handlers;

    RockyClientFrameListener(RockyModules rockyClients) {
        this.handlers = Map.of(
                RoutingCommon.FrameType.S_ChangeService, new UpdateInfoRouterFrameHandler(rockyClients)
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public void listen(AlpsSession session, Frame frame) {
        Thread.startVirtualThread(() -> {
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
        });
    }
}

interface RouterFrameHandler<T extends MessageLite> {

    T decode(ByteString data) throws Exception;

    void handle(AlpsSession session, T frame) throws Exception;
}

class UpdateInfoRouterFrameHandler implements RouterFrameHandler<RoutingServer.ModuleInfo> {

    private final RockyModules rockyClients;

    UpdateInfoRouterFrameHandler(RockyModules rockyClients) {
        this.rockyClients = rockyClients;
    }


    @Override
    public RoutingServer.ModuleInfo decode(ByteString data) throws Exception {
        return RoutingServer.ModuleInfo.parseFrom(data);
    }

    @Override
    public void handle(AlpsSession session, RoutingServer.ModuleInfo frame) throws Exception {
        if (frame.getType() == RoutingServer.OpsType.Delete) {
            rockyClients.unregisterModule(frame.getModuleName());
        } else {
            var list = frame.getInstancesList()
                    .stream()
                    .map(e -> new InstanceInfo(e.getProfile(), e.getInstanceId(), e.getIp(), e.getPort(), e.getActive()))
                    .toList();
            rockyClients.registerModule(frame.getModuleName(), list);
        }

    }
}
