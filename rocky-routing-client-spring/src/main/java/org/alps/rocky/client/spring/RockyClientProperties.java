package org.alps.rocky.client.spring;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(RockyClientProperties.PATH)
@Data
public class RockyClientProperties {

    static final String PATH = "rocky.client";

    /**
     * 路由注册地址
     */
    private String routerRegisterUrl;
    /**
     * 命名空间
     */
    private String namespace;
    /**
     * 实例ID(可选)
     */
    private String instanceId;
    /**
     * 模式
     */
    private String profile = "prod";
    /**
     * 访问密钥
     */
    private String accessKey;
}
