package org.alps.rocky.client.spring;

import org.alps.rocky.client.core.RockyClient;
import org.alps.rocky.client.core.RockyModules;
import org.alps.starter.AlpsProperties;
import org.alps.starter.config.AlpsServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RockyClientProperties.class, AlpsProperties.class, AlpsServerProperties.class})
public class RockyClientConfiguration {

    @Bean(destroyMethod = "close")
    RockyClient rockyClient(RockyClientProperties rockyClientProperties, AlpsProperties alpsProperties,
                            AlpsServerProperties alpsServerProperties) {
        Objects.requireNonNull(rockyClientProperties.getRouterRegisterUrl(), "routerRegisterURL");
        Objects.requireNonNull(rockyClientProperties.getNamespace(), "namespace");
        Objects.requireNonNull(rockyClientProperties.getAccessKey(), "accessKey");
        return new RockyClient(rockyClientProperties.getRouterRegisterUrl(), rockyClientProperties.getNamespace(),
                rockyClientProperties.getProfile(),
                alpsProperties.getModules(),
                alpsServerProperties.getPort(), rockyClientProperties.getAccessKey(), rockyClientProperties.getInstanceId());
    }

    @Bean
    RockyModules rockyModules(RockyClient rockyClient) {
        return rockyClient.getRockyModules();
    }
}
