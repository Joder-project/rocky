package org.alps.rocky.server.core;

import java.util.List;
import java.util.Map;

public record RegisterInstanceInfo(String namespace, InstanceInfo instanceInfo, List<String> modules,
                                   Map<String, String> extra) {

}

/**
 * 模块注册信息
 * @param namespace 命名空间
 * @param moduleName 模块信息
 * @param instanceInfo 实例信息
 */
record RegisterModuleInfo(String namespace, String moduleName, InstanceInfo instanceInfo, Map<String, String> extra,
                          boolean active) {

}

/**
 * 实例信息
 * @param profile 开发模式
 * @param instanceId 实例ID
 * @param ip IP
 * @param port 端口
 */
record InstanceInfo(String profile, String instanceId, String ip, int port, boolean active) {

}
