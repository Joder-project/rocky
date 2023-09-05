package org.alps.rocky.client.core;

public record InstanceInfo(String profile, String instanceId, String ip, int port, boolean active) {
}
