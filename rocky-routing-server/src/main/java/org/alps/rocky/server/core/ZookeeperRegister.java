package org.alps.rocky.server.core;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.alps.rocky.server.common.Jsons;
import org.alps.rocky.server.common.PathUtils;
import org.alps.rocky.server.config.RockyServerProperties;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.ACL;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class ZookeeperRegister implements Register, ServerRegister {

    /**
     * 模块注册根路径
     */
    private final String moduleRoot;
    /**
     * 实例化的根路径
     */
    private final String instanceRoot;
    @Getter
    private final ZooKeeper zooKeeper;
    private final ModuleNotification moduleNotification;
    private final RockyServerProperties properties;

    public ZookeeperRegister(RockyServerProperties properties,
                             ModuleNotification moduleNotification) throws Exception {
        this.properties = properties;
        var zookeeper = properties.getZookeeper();
        this.moduleRoot = PathUtils.of(zookeeper.getWatchRoot(), "infos/modules");
        this.instanceRoot = PathUtils.of(zookeeper.getWatchRoot(), "infos/instances");
        this.moduleNotification = moduleNotification;
        this.zooKeeper = new ZooKeeper(zookeeper.getHosts(), zookeeper.getSessionTimeout(), event -> {
        });
        if (this.zooKeeper.exists(this.moduleRoot, false) == null) {
            create(this.moduleRoot, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        if (this.zooKeeper.exists(this.instanceRoot, false) == null) {
            create(this.instanceRoot, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        this.zooKeeper.addWatch(moduleRoot, this::watchModuleChange, AddWatchMode.PERSISTENT_RECURSIVE);
    }

    void watchModuleChange(WatchedEvent event) {
        if (event == null || event.getState() == Watcher.Event.KeeperState.Closed) {
            return;
        }
        var path = event.getPath().substring(this.moduleRoot.length() + 1);
        var array = path.split("/");
        if (array.length < 2) {
            return;
        }
        var namespace = array[0];
        var module = array[1];
        try {
            if (event.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
                notifyModuleChange(namespace, module);
            } else if (event.getType() == Watcher.Event.EventType.NodeDeleted) {
                notifyModuleDelete(namespace, module);
            }
        } catch (Exception ex) {
            log.error("notify error", ex);
        }

    }

    public void close() throws Exception {
        zooKeeper.close();
    }

    @Override
    public void registerInstance(RegisterInstanceInfo info) throws Exception {
        for (String module : info.modules()) {
            registerModule(new RegisterModuleInfo(info.namespace(), module, info.instanceInfo(), Collections.emptyMap(), false));
        }
    }

    @Override
    public void updateInstance(String namespace, String instanceId, Map<String, String> extra, boolean active) throws Exception {
        updateInstance0(namespace, instanceId, extra, active, false);
    }

    @Override
    public void updateInstance(String namespace, String instanceId, Map<String, String> extra) throws Exception {
        updateInstance0(namespace, instanceId, extra, false, true);
    }

    public void updateInstance0(String namespace, String instanceId, Map<String, String> extra, boolean active, boolean ignoreActive) throws Exception {
        var instancePath = PathUtils.of(this.instanceRoot, namespace, instanceId);
        if (this.zooKeeper.exists(instancePath, false) == null) {
            return;
        }
        var children = this.zooKeeper.getChildren(instancePath, false);
        if (children == null || children.isEmpty()) {
            return;
        }
        for (String child : children) {
            var path = PathUtils.of(instancePath, child);
            var data = Jsons.MAPPER.readValue(this.zooKeeper.getData(path, false, null), RegisterModuleInfo.class);
            data = new RegisterModuleInfo(data.namespace(), data.moduleName(), data.instanceInfo(), extra, ignoreActive ? data.active() : active);
            var modulePath = PathUtils.of(this.moduleRoot, namespace, data.moduleName(), instanceId);
            var bytes = Jsons.MAPPER.writeValueAsBytes(data);
            this.zooKeeper.setData(modulePath, bytes, -1);
            this.zooKeeper.setData(path, bytes, -1);
        }
    }

    void registerModule(RegisterModuleInfo info) throws Exception {
        // todo 方法原子化？
        var modulePath = PathUtils.of(this.moduleRoot, info.namespace(), info.moduleName(), info.instanceInfo().instanceId());
        var instancePath = PathUtils.of(this.instanceRoot, info.namespace(), info.instanceInfo().instanceId(), info.moduleName());
        if (this.zooKeeper.exists(modulePath, false) != null) {
            // module exist
            throw new IllegalStateException("节点已存在");
        }

        var data = Jsons.MAPPER.writeValueAsBytes(info);
        create(modulePath, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        create(instancePath, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
    }


    @Override
    public void removeInstance(String namespace, String instanceId) throws Exception {
        // todo 方法原子化？
        var instancePath = PathUtils.of(this.instanceRoot, namespace, instanceId);
        if (this.zooKeeper.exists(instancePath, false) == null) {
            return;
        }
        var children = this.zooKeeper.getChildren(instancePath, false);
        if (children == null || children.isEmpty()) {
            this.zooKeeper.delete(instancePath, -1);
            return;
        }

        for (String module : children) {
            removeModule(namespace, module, instanceId);
            var modulePath = PathUtils.of(instancePath, module);
            this.zooKeeper.delete(modulePath, -1);
        }
        this.zooKeeper.delete(instancePath, -1);
    }

    void removeModule(String namespace, String module, String instanceId) throws Exception {
        var modulePath = PathUtils.of(this.moduleRoot, namespace, module, instanceId);
        if (this.zooKeeper.exists(modulePath, false) == null) {
            return;
        }
        this.zooKeeper.delete(modulePath, -1);
        // 如果服务没有实例，则删除节点
        var children = this.zooKeeper.getChildren(PathUtils.of(this.moduleRoot, namespace, module), false);
        if (children == null || children.isEmpty()) {
            this.zooKeeper.delete(PathUtils.of(this.moduleRoot, namespace, module), -1);
            return;
        }
    }

    /**
     * 模块更新
     */
    void notifyModuleChange(String namespace, String module) throws Exception {
        var modulePath = PathUtils.of(this.moduleRoot, namespace, module);
        if (this.zooKeeper.exists(modulePath, false) == null) {
            return;
        }
        var children = this.zooKeeper.getChildren(modulePath, false);
        if (children == null || children.isEmpty()) {
            notifyModuleDelete(namespace, module);
            return;
        }

        List<InstanceInfo> instances = new ArrayList<>();
        for (String child : children) {
            String path = PathUtils.of(modulePath, child);
            var data = this.zooKeeper.getData(path, false, null);
            if (data == null) {
                continue;
            }
            var moduleInfo = Jsons.MAPPER.readValue(data, RegisterModuleInfo.class);
            instances.add(moduleInfo.instanceInfo());
        }
        moduleNotification.notifyModule(new ModuleNotifyInfo(namespace, module, Collections.unmodifiableList(instances), OpsType.Modify));
    }

    /**
     * 模块删除通知
     */
    private void notifyModuleDelete(String namespace, String module) {
        moduleNotification.notifyModule(new ModuleNotifyInfo(namespace, module, Collections.emptyList(), OpsType.Delete));
    }

    @Override
    @SneakyThrows
    public void registerSelf() {
        var host = Inet4Address.getLocalHost().getHostAddress();
        int port = properties.getPort();
        var routePath = PathUtils.of(properties.getZookeeper().getWatchRoot(), "routes", host + "_" + port);
        if (this.zooKeeper.exists(routePath, false) != null) {
            return;
        }
        create(routePath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
    }

    @SneakyThrows
    @Override
    public List<RegisterRouter> all() {
        var routePath = PathUtils.of(properties.getZookeeper().getWatchRoot(), "routes");
        var children = this.zooKeeper.getChildren(routePath, false);
        if (children == null || children.isEmpty()) {
            return Collections.emptyList();
        }
        return children.stream()
                .map(e -> e.split("_"))
                .map(e -> new RegisterRouter(e[0], Integer.parseInt(e[1])))
                .toList();
    }

    void create(final String path, byte[] data, List<ACL> acl, CreateMode createMode) throws Exception {
        var arrays = path.split("/");
        String p = "";
        for (int i = 0; i < arrays.length; i++) {
            p += (arrays[i].startsWith("/") || p.endsWith("/") ? "" : "/") + arrays[i];
            if (this.zooKeeper.exists(p, false) == null) {
                this.zooKeeper.create(p, i == arrays.length - 1 ? data : new byte[0], acl,
                        i == arrays.length - 1 ? createMode : CreateMode.PERSISTENT);
            }
        }

    }
}
