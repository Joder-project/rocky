package org.alps.rocky.server.core;

import org.alps.core.AlpsEnhancedSession;
import org.alps.core.AlpsPacket;
import org.alps.core.AlpsSession;
import org.alps.core.AlpsUtils;
import org.alps.core.datacoder.AlpsProtobufDataCoder;
import org.alps.core.frame.RoutingFrame;
import org.alps.core.proto.AlpsProtocol;
import org.alps.core.support.AlpsDataBuilder;
import org.alps.core.support.AlpsMetadataBuilder;
import org.alps.rocky.core.proto.RoutingCommon;
import org.alps.rocky.core.proto.RoutingServer;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

public class ModuleNotification {

    private final Map<String, Namespace> namespaces = new ConcurrentHashMap<>();
    private final StampedLock stampedLock = new StampedLock();

    void register(String namespace, String instanceId, AlpsSession session) {
        if (namespaces.containsKey(namespace)) {
            namespaces.get(namespace).register(instanceId, session);
            return;
        }
        var writeLock = stampedLock.writeLock();
        try {
            if (namespaces.containsKey(namespace)) {
                namespaces.get(namespace).register(instanceId, session);
            } else {
                var ns = new Namespace();
                ns.register(instanceId, session);
                namespaces.put(namespace, ns);
            }
        } finally {
            stampedLock.unlockWrite(writeLock);
        }
    }

    void unRegister(String namespace, String instanceId) {
        if (namespaces.containsKey(namespace)) {
            namespaces.get(namespace).unregister(instanceId);
        }
    }

    void notifyModule(ModuleNotifyInfo info) {
        if (!namespaces.containsKey(info.namespace())) {
            return;
        }
        var namespace = namespaces.get(info.namespace());
        namespace.notifyModule(info);
    }

    static class Namespace {
        private final Map<String, AlpsSession> instanceToSession = new ConcurrentHashMap<>();
        private final StampedLock stampedLock = new StampedLock();
        private final AlpsProtobufDataCoder protobufDataCoder = new AlpsProtobufDataCoder();

        void register(String instanceId, AlpsSession session) {
            var writeLock = stampedLock.writeLock();
            try {
                if (instanceToSession.containsKey(instanceId)) {
                    throw new IllegalStateException("已存在存在Session, " + instanceId);
                }
                instanceToSession.put(instanceId, session);
            } finally {
                stampedLock.unlockWrite(writeLock);
            }
        }

        void unregister(String instanceId) {
            instanceToSession.remove(instanceId);
        }

        void notifyModule(ModuleNotifyInfo info) {
            var sessions = instanceToSession.values().stream()
                    .filter(e -> {
                        String namespace = e.attr(RouterFrameHandler.NAMESPACE_KEY);
                        return Objects.equals(info.namespace(), namespace);
                    })
                    .map(e -> ((AlpsEnhancedSession) e))
                    .toList();
            var moduleInfo = RoutingServer.ModuleInfo.newBuilder().setNamespace(info.namespace())
                    .setModuleName(info.moduleName())
                    .setTypeValue(info.type().ordinal())
                    .addAllInstances(info.instances().stream().map(e -> RoutingServer.InstanceInfo
                            .newBuilder().setProfile(e.profile()).setInstanceId(e.instanceId())
                            .setIp(e.ip()).setPort(e.port()).setActive(e.active()).build()).toList())
                    .build();
            var routingFrame = RoutingCommon.RoutingFrame.newBuilder()
                    .setType(RoutingCommon.FrameType.S_ChangeService)
                    .setFrame(moduleInfo.toByteString())
                    .build();
            byte[] frameBytes = RoutingFrame.toBytes(routingFrame.toByteArray());
            var alpsMetadata = new AlpsMetadataBuilder()
                    .frameType((byte) AlpsProtocol.AlpsPacket.FrameType.ROUTING_VALUE)
                    .frame(frameBytes)
                    .coder(protobufDataCoder)
                    .build();
            var alpsData = new AlpsDataBuilder()
                    .coder(protobufDataCoder)
                    .build();
            var alpsPacket = new AlpsPacket(AlpsProtocol.AlpsPacket.ConnectType.ROUTEING_VALUE, AlpsPacket.ZERO_MODULE, alpsMetadata, alpsData, null);
            AlpsUtils.broadcast(sessions, alpsPacket);
        }
    }
}
