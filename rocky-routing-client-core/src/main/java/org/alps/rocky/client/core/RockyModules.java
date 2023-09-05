package org.alps.rocky.client.core;

import java.util.*;
import java.util.concurrent.locks.StampedLock;

/**
 * 所有可连接的服务器信息
 */
public class RockyModules {

    private final String instanceId;
    private final StampedLock stampedLock = new StampedLock();

    private Map<String, RockyModuleSession> modules = new HashMap<>();

    RockyModules(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * 注册模块
     *
     * @param moduleName 模块
     * @param instances  实例
     */
    void registerModule(String moduleName, List<InstanceInfo> instances) {
        var writeLock = stampedLock.writeLock();
        try {
            if (!modules.containsKey(moduleName)) {
                modules.put(moduleName, new RockyModuleSession(moduleName, instanceId, instances));
            } else {
                var moduleInfo = modules.get(moduleName);
                moduleInfo.update(instances);
            }
        } finally {
            stampedLock.unlockWrite(writeLock);
        }
    }

    void unregisterModule(String moduleName) {
        var writeLock = stampedLock.writeLock();
        try {
            var rockyModuleSession = modules.remove(moduleName);
            if (rockyModuleSession != null) {
                rockyModuleSession.update(Collections.emptyList());
            }
        } finally {
            stampedLock.unlockWrite(writeLock);
        }
    }

    /**
     * 请求其他模块
     */
    public RockyModuleSession module(String moduleName) {
        return Optional.ofNullable(modules.get(moduleName))
                .orElseThrow(() -> new IllegalArgumentException("不存在对应模块" + moduleName));
    }

}
