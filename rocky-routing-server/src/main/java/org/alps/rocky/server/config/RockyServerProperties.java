package org.alps.rocky.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(RockyServerProperties.PATH)
@Data
public class RockyServerProperties {
    static final String PATH = "rocky.server";

    /**
     * 开启socket端口
     */
    private int port;

    private ZookeeperProperties zookeeper = new ZookeeperProperties();

    @Data
    public static class ZookeeperProperties {
        private String watchRoot;
        private String hosts;
        private int sessionTimeout;
    }
}
