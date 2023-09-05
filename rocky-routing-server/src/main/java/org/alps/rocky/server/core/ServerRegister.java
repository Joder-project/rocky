package org.alps.rocky.server.core;

import java.util.List;

public interface ServerRegister {

    /**
     * 注册到全局中
     */
    void registerSelf();

    /**
     * 所有可用的服务
     */
    List<RegisterRouter> all();
}
