package org.alps.rocky.server.core;

import java.util.List;

public record ModuleNotifyInfo(String namespace, String moduleName, List<InstanceInfo> instances, OpsType type) {
}

enum OpsType {
    Modify, Delete
}
