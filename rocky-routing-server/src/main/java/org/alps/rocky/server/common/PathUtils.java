package org.alps.rocky.server.common;

import java.nio.file.Path;

public class PathUtils {

    public static String of(String path, String...others) {
        return Path.of(path, others).toString().replaceAll("\\\\", "/");
    }
}
