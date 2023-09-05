package org.alps.rocky.server.core;

import java.util.Map;

/**
 * 信息注册
 */
public interface Register {

    void registerInstance(RegisterInstanceInfo info) throws Exception;

    void updateInstance(String namespace, String instanceId, Map<String, String> extra, boolean active) throws Exception;

    void updateInstance(String namespace, String instanceId, Map<String, String> extra) throws Exception;

    /**
     * 删除实例
     * @param namespace 命名空间
     * @param instanceId 实例ID
     * @throws Exception
     */
    void removeInstance(String namespace, String instanceId) throws Exception;

}
