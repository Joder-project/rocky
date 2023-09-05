package org.alps.rocky.server.config;

import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import org.alps.core.*;
import org.alps.core.proto.AlpsProtocol;
import org.alps.core.socket.netty.server.AlpsTcpServer;
import org.alps.core.socket.netty.server.NettyServerConfig;
import org.alps.rocky.server.core.ModuleNotification;
import org.alps.rocky.server.core.Register;
import org.alps.rocky.server.core.RockyServer;
import org.alps.rocky.server.core.ZookeeperRegister;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(RockyServerProperties.class)
public class RockyServerConfiguration {

    @Bean(destroyMethod = "close", initMethod = "registerSelf")
    ZookeeperRegister zookeeperRegister(RockyServerProperties properties, ModuleNotification notification) throws Exception{
        return new ZookeeperRegister(properties, notification);
    }

    @Bean
    ModuleNotification moduleNotification() {
        return new ModuleNotification();
    }

    @Bean
    RouterDispatcher routerDispatcher() {
        return new RouterDispatcher();
    }

    @Bean
    SessionListeners sessionListeners(List<SessionListener> sessionListeners) {
        return new SessionListeners(sessionListeners);
    }

    @Bean
    FrameListeners frameListeners(RouterDispatcher routerDispatcher) {
        return new FrameListeners(routerDispatcher);
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    RockyServer rockyServer(RockyServerProperties properties, FrameListeners frameListeners,
                            ModuleNotification moduleNotification,
                            SessionListeners sessionListeners, Register register) {
        var alpsConfig = new AlpsConfig();
        alpsConfig.setSocketType(AlpsProtocol.AlpsPacket.ConnectType.ROUTEING_VALUE);
        var sessionFactory = new DefaultEnhancedSessionFactory(new FrameCoders(), new AlpsDataCoderFactory(), frameListeners,
                sessionListeners, alpsConfig);
        var nettyServerConfig = new NettyServerConfig();
        nettyServerConfig.setPort(properties.getPort());
        nettyServerConfig.setChildOptionSettings(Map.of(
                ChannelOption.SO_KEEPALIVE, true
        ));
        nettyServerConfig.setOptionSettings(Map.of(
                ChannelOption.SO_BACKLOG, 128
        ));
        nettyServerConfig.setTimeout(new NettyServerConfig.Timeout(10000, 10000, 15000));
        var alpsTcpServer = new AlpsTcpServer(
                new NioEventLoopGroup(1),
                new NioEventLoopGroup(Runtime.getRuntime().availableProcessors()),
                new NioEventLoopGroup(Runtime.getRuntime().availableProcessors()),
                nettyServerConfig, sessionFactory, Collections.emptyList(), new AlpsDataCoderFactory()
        );
        return new RockyServer(alpsTcpServer, frameListeners, moduleNotification, register);
    }
}
