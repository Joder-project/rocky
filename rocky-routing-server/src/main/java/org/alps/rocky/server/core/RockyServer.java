package org.alps.rocky.server.core;

import org.alps.core.AlpsServer;
import org.alps.core.FrameListeners;
import org.alps.core.frame.RoutingFrame;

public class RockyServer {

    private final AlpsServer alpsServer;
    private final FrameListeners frameListeners;
    private final ModuleNotification moduleNotification;
    private final Register register;

    public RockyServer(AlpsServer alpsServer, FrameListeners frameListeners, ModuleNotification moduleNotification, Register register) {
        this.alpsServer = alpsServer;
        this.frameListeners = frameListeners;
        this.moduleNotification = moduleNotification;
        this.register = register;
    }

    void start() {
        alpsServer.start();
        frameListeners.addFrameListener(RoutingFrame.class, new RockyRoutingFrameRouter(moduleNotification, register));
    }

    void close() {
        alpsServer.close();
    }
}
