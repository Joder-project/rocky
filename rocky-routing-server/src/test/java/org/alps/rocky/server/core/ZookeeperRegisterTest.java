package org.alps.rocky.server.core;

import org.alps.rocky.server.common.PathUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZookeeperRegisterTest {

    @Test
    void test() {
        assertEquals("/a/b/c", PathUtils.of("/a", "b/c"));
        assertEquals("/a/b/c", PathUtils.of("/a", "/b/c"));
        assertEquals("/a/b/c", PathUtils.of("/a", "/b/c"));

    }

}