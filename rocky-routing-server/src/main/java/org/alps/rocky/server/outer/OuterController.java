package org.alps.rocky.server.outer;

import lombok.RequiredArgsConstructor;
import org.alps.rocky.server.common.PathUtils;
import org.alps.rocky.server.config.RockyServerProperties;
import org.alps.rocky.server.core.RegisterRouter;
import org.alps.rocky.server.core.ServerRegister;
import org.alps.rocky.server.core.ZookeeperRegister;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对外接口
 */
@RestController
@RequiredArgsConstructor
public class OuterController {

    private final ServerRegister serverRegister;
    private final RockyServerProperties properties;
    private final ZookeeperRegister zookeeperRegister;

    /**
     * 获取所有可用服务器
     */
    @GetMapping("/rocky/routers")
    public Flux<RegisterRouter> query(ServerWebExchange exchange) {
        // todo 做检查，类似IP白名单
        return Flux.defer(() -> Flux.fromIterable(serverRegister.all()));
    }

    @GetMapping("/rocky/module/{namespace}")
    public Mono<Map<String, List<String>>> moduleInfo(@PathVariable String namespace) throws Exception {
        return Mono.create(sink -> {
            var zooKeeper = zookeeperRegister.getZooKeeper();
            var namespacePath = PathUtils.of(properties.getZookeeper().getWatchRoot(), "infos/modules", namespace);
            List<String> children = null;
            try {
                children = zooKeeper.getChildren(namespacePath, false);
                if (children == null || children.isEmpty()) {
                    sink.success();
                    return;
                }
                Map<String, List<String>> map = new HashMap<>();
                for (String child : children) {
                    List<String> ls = new ArrayList<>();
                    var modulePath = PathUtils.of(namespacePath, child);
                    var instanceChildren = zooKeeper.getChildren(modulePath, false);
                    ;
                    for (String instanceChild : instanceChildren) {
                        var instancePath = PathUtils.of(modulePath, instanceChild);
                        var data = zooKeeper.getData(instancePath, false, null);
                        ls.add(new String(data));
                    }
                    map.put(child, ls);
                }
                sink.success(map);
            } catch (Exception e) {
                sink.error(new RuntimeException(e));
            }

        });
    }
}
