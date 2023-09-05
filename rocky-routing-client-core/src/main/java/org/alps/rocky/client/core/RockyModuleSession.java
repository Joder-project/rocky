package org.alps.rocky.client.core;

import io.netty.channel.nio.NioEventLoopGroup;
import org.alps.core.*;
import org.alps.core.proto.AlpsProtocol;
import org.alps.core.socket.netty.client.AlpsTcpClient;
import org.alps.core.socket.netty.client.NettyClientConfig;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;

/**
 * 封装的session
 */
public class RockyModuleSession {

    private final String moduleName;
    /**
     * 当前ID
     */
    private final String instanceId;
    private final Map<String, Client> clients = new HashMap<>();
    private final StampedLock stampedLock = new StampedLock();

    private final AtomicReference<Client> tempClient = new AtomicReference<>(null);

    RockyModuleSession(String moduleName, String instanceId, List<InstanceInfo> infos) {
        this.moduleName = moduleName;
        this.instanceId = instanceId;
        var writeLock = stampedLock.writeLock();
        try {
            for (InstanceInfo info : infos) {
                clients.put(info.instanceId(), new Client(info, moduleName));
            }
        } finally {
            stampedLock.unlockWrite(writeLock);
        }
    }

    void update(List<InstanceInfo> infos) {
        var writeLock = stampedLock.writeLock();
        try {
            var collect = new HashSet<>(clients.keySet());
            for (InstanceInfo info : infos) {
                if (clients.containsKey(info.instanceId())) {
                    var client = clients.get(info.instanceId());
                    client.update(info);
                    collect.remove(info.instanceId());
                } else {
                    clients.put(info.instanceId(), new Client(info, moduleName));
                }
            }
            if (!collect.isEmpty()) {
                for (var key : collect) {
                    var client = clients.get(key);
                    client.inactive();
                    // TODO close session and remove?
                }
            }
        } finally {
            stampedLock.unlockWrite(writeLock);
        }
    }

    /**
     * @return 返回一个客户端
     */
    Optional<Client> getClient() {
        if (tempClient.get() != null && tempClient.get().isActive()) {
            return Optional.of(tempClient.get());
        }
        // TODO 现根据ID确定唯一值
        var array = clients.values().stream().filter(Client::isActive).toArray(Client[]::new);
        if (array.length == 0) {
            return Optional.empty();
        }
        var client = array[instanceId.hashCode() % array.length];
        tempClient.set(client);
        return Optional.of(client);
    }

    public Client use() {
        return getClient().orElseThrow(() -> new IllegalStateException("找不到可用服务"));
    }

    public static class Client {
        private final StampedLock stampedLock = new StampedLock();
        private final String moduleName;
        private final AtomicReference<InstanceInfo> info;
        private volatile AlpsClient alpsClient;

        Client(InstanceInfo info, String moduleName) {
            this.info = new AtomicReference<>(info);
            this.moduleName = moduleName;
        }

        void update(InstanceInfo info) {
            this.info.set(info);
        }

        boolean isActive() {
            return info.get().active();
        }

        void connect() {
            var instanceInfo = info.get();
            var alpsConfig = new AlpsConfig();
            alpsConfig.setSocketType(AlpsProtocol.AlpsPacket.ConnectType.SERVER_VALUE);
            var sessionFactory = new DefaultEnhancedSessionFactory(new FrameCoders(), new AlpsDataCoderFactory(),
                    new FrameListeners(new RouterDispatcher()),
                    new SessionListeners(Collections.emptyList()), alpsConfig);
            var nettyClientConfig = new NettyClientConfig();
            nettyClientConfig.setHost(instanceInfo.ip());
            nettyClientConfig.setPort(instanceInfo.port());
            nettyClientConfig.setTimeout(new NettyClientConfig.Timeout(10000, 10000, 15000));
            this.alpsClient = new AlpsTcpClient(new NioEventLoopGroup(1), nettyClientConfig, sessionFactory,
                    Collections.emptyList(), new AlpsDataCoderFactory());
            this.alpsClient.start();
        }

        void inactive() {
            var instanceInfo = this.info.get();
            this.info.set(new InstanceInfo(instanceInfo.profile(), instanceInfo.instanceId(), instanceInfo.ip(),
                    instanceInfo.port(), false));
        }

        public AlpsEnhancedSession getSession() {
            if (alpsClient == null) {
                var writeLock = stampedLock.writeLock();
                try {
                    if (alpsClient == null) {
                        connect();
                    }
                } finally {
                    stampedLock.unlockWrite(writeLock);
                }
            }
            return alpsClient.session(moduleName)
                    .map(e -> ((AlpsEnhancedSession) e))
                    .orElseThrow();
        }

        /**
         * 发送协议
         */
        public AlpsEnhancedSession.ForgetCommand forget(int command) {
            if (!isActive()) {
                throw new IllegalStateException("服务不可用" + moduleName);
            }
            var session = getSession();
            return session.forget(command);
        }

        public AlpsEnhancedSession.RequestCommand request(int command) {
            if (!isActive()) {
                throw new IllegalStateException("服务不可用" + moduleName);
            }
            var session = getSession();
            return session.request(command);
        }

        public AlpsEnhancedSession.StreamRequestCommand stream(int command) {
            if (!isActive()) {
                throw new IllegalStateException("服务不可用" + moduleName);
            }
            var session = getSession();
            return session.streamRequest(command);
        }

        public AlpsEnhancedSession.ErrorCommand error() {
            if (!isActive()) {
                throw new IllegalStateException("服务不可用" + moduleName);
            }
            var session = getSession();
            return session.error();
        }
    }
}
