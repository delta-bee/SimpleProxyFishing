package com.beanbeanjuice.advancedproxychathelper.test;

import com.beanbeanjuice.advancedproxychathelper.AdvancedProxyChatHelper;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class InitializationTest {

    private ServerMock server;
    private AdvancedProxyChatHelper plugin;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(AdvancedProxyChatHelper.class);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Confirm MockBukkit is correctly working.")
    public void testInitialization() {
        PlayerMock playerMock = server.addPlayer();
        Assertions.assertEquals(1, server.getOnlinePlayers().size());

        playerMock.sendMessage("Hello, world!");
        Assertions.assertEquals("Hello, world!", playerMock.nextMessage());
    }

    @Test
    @DisplayName("Confirm MockBukkit Server is the Same As Plugin Server")
    public void testServerIsEqual() {
        Assertions.assertEquals(plugin.getServer(), server);
    }
}
