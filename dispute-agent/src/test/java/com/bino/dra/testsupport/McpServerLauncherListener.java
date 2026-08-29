package com.bino.dra.testsupport;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

// Not @BeforeAll: the MCP handshake happens while the context starts, so the peer must
// already be listening before the first test class is instantiated
public class McpServerLauncherListener implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        if (Boolean.getBoolean("dra.test.mcp-server")) {
            McpServerProcess.start();
        }
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        McpServerProcess.stop();
    }
}
