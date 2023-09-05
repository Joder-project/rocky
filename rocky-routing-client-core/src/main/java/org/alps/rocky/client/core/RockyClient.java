package org.alps.rocky.client.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.alps.core.*;
import org.alps.core.datacoder.AlpsProtobufDataCoder;
import org.alps.core.frame.RoutingFrame;
import org.alps.core.proto.AlpsProtocol;
import org.alps.core.socket.netty.client.AlpsTcpClient;
import org.alps.core.socket.netty.client.NettyClientConfig;
import org.alps.core.support.AlpsDataBuilder;
import org.alps.core.support.AlpsMetadataBuilder;
import org.alps.rocky.core.proto.RoutingClient;
import org.alps.rocky.core.proto.RoutingCommon;
import reactor.core.publisher.Mono;

import java.net.Inet4Address;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RockyClient {

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final FrameListeners frameListeners;
    private final String namespace;
    private final String profile;
    private final String instanceId;
    private final int port;
    private final String accessKey;
    private final List<String> supportModules;
    private AlpsClient client;
    private AlpsSession session;
    private final ScheduledExecutorService healthThread = Executors.newSingleThreadScheduledExecutor();
    @Getter
    private final RockyModules rockyModules;
    private final AlpsProtobufDataCoder protobufDataCoder = new AlpsProtobufDataCoder();
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    /**
     * @param routersUrl 路由查询地址
     */
    public RockyClient(String routersUrl, String namespace, String profile,
                       List<String> supportModules, int port, String accessKey, String instanceId) {
        this.frameListeners = new FrameListeners(new RouterDispatcher());
        this.instanceId = instanceId == null ? UUID.randomUUID().toString() : instanceId;
        this.profile = profile;
        this.port = port;
        this.accessKey = accessKey;
        this.namespace = namespace;
        this.supportModules = supportModules;
        this.rockyModules = new RockyModules(this.instanceId);
        start(routersUrl);
        this.frameListeners.addFrameListener(new RockyClientFrameListener(rockyModules));
    }

    void start(String routerUrl) {
        queryRouters(routerUrl).doOnNext(routers -> {
            var router = selectRouter(routers);
            connectRouter(router);
            try {
                sendConnectMsg();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            active.set(true);
            // 定时心跳
            healthThread.scheduleAtFixedRate(this::health, 0L, 5L, TimeUnit.SECONDS);
        }).block();
    }

    public void close() {
        healthThread.shutdown();
        client.close();
        countDownLatch.countDown();
    }

    /**
     * 查询路由地址
     *
     * @return 所有可用注册地址
     */
    Mono<List<RegisterRouter>> queryRouters(String routerUrl) {
        String url = routerUrl + "/rocky/routers";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return Mono.fromFuture(HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .<List<RegisterRouter>>handle((response, sink) -> {
                    if (response.statusCode() / 200 != 1) {
                        sink.error(new IllegalStateException("查询路由地址错误"));
                        return;
                    }
                    var objectMapper = new ObjectMapper();
                    try {
                        var routers = objectMapper.readValue(response.body(), new TypeReference<ArrayList<RegisterRouter>>() {
                        });
                        sink.next(routers);
                    } catch (Exception e) {
                        sink.error(new RuntimeException(e));
                    }
                });

    }

    RegisterRouter selectRouter(List<RegisterRouter> routers) {
        if (routers.isEmpty()) {
            throw new IllegalStateException("没有路由可以使用");
        }
        var random = new Random();
        return routers.get(random.nextInt() % routers.size());
    }

    void connectRouter(RegisterRouter router) {
        var alpsConfig = new AlpsConfig();
        alpsConfig.setSocketType(AlpsProtocol.AlpsPacket.ConnectType.ROUTEING_VALUE);
        var sessionFactory = new DefaultEnhancedSessionFactory(new FrameCoders(), new AlpsDataCoderFactory(),
                frameListeners,
                new SessionListeners(Collections.emptyList()), alpsConfig);
        var nettyClientConfig = new NettyClientConfig();
        nettyClientConfig.setHost(router.ip());
        nettyClientConfig.setPort(router.port());
        nettyClientConfig.setTimeout(new NettyClientConfig.Timeout(10000, 10000, 15000));
        this.client = new AlpsTcpClient(new NioEventLoopGroup(1), nettyClientConfig, sessionFactory,
                Collections.emptyList(), new AlpsDataCoderFactory());
        this.client.start();
        this.session = this.client.session()
                .stream()
                .filter(e -> e.module().equals(AlpsPacket.ZERO_MODULE))
                .findAny()
                .orElseThrow();
    }

    /**
     * 发送连接信息
     */
    void sendConnectMsg() throws Exception {
        var serviceInfo = RoutingClient.ServiceInfo.newBuilder()
                .setNamespace(namespace)
                .setProfile(profile)
                .setInstanceId(instanceId)
                .setIp(Inet4Address.getLocalHost().getHostAddress())
                .setPort(port)
                .setAccessKey(accessKey)
                .addAllModules(supportModules)
                .build();
        sendMsg(RoutingCommon.FrameType.C_Connect, serviceInfo.toByteString());
    }

    private void sendMsg(RoutingCommon.FrameType type, ByteString bytes) {
        var routingFrame = RoutingCommon.RoutingFrame.newBuilder()
                .setType(type)
                .setFrame(bytes)
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
        session.send(alpsPacket);
    }

    Map<String, String> getExtra() {
        // TODO
        return Collections.emptyMap();
    }

    /**
     * 心跳
     */
    public void health() {
        if (!active.get()) {
            log.error("The client don't use now.");
            return;
        }
        var idleInfo = RoutingClient.HealthIdleInfo.newBuilder()
                .putAllMsg(getExtra())
                .build();
        sendMsg(RoutingCommon.FrameType.C_HealthIdle, idleInfo.toByteString());
    }

    /**
     * 激活实例
     */
    public void active() {
        if (!active.get()) {
            log.error("The client don't use now.");
            return;
        }
        var info = RoutingClient.UpServiceInfo.newBuilder()
                .putAllMsg(getExtra())
                .build();
        sendMsg(RoutingCommon.FrameType.C_Active, info.toByteString());
    }

    /**
     * 取消激活实例
     */
    public void inactive() {
        if (!active.get()) {
            log.error("The client don't use now.");
            return;
        }
        var info = RoutingClient.DownServiceInfo.newBuilder()
                .putAllMsg(getExtra())
                .build();
        sendMsg(RoutingCommon.FrameType.C_Disable, info.toByteString());
    }
}
