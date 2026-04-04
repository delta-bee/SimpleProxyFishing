package com.beanbeanjuice.simpleproxychat.test.discord;

import com.beanbeanjuice.simpleproxychat.shared.config.Config;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigKey;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigValueWrapper;
import com.beanbeanjuice.simpleproxychat.shared.discord.Bot;
import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Bot reload behaviour without a live JDA connection.
 *
 * On reload, the bot must only refresh its activity/status/channel-registry.
 * It must NEVER call stop() or send a "Proxy disabled" embed.
 */
public class BotReloadTest {

    private Config config;
    private List<String> warnMessages;

    @BeforeEach
    public void setUp() {
        config = Mockito.mock(Config.class);
        warnMessages = new ArrayList<>();

        stubDiscord(false, "TOKEN_HERE");
        Mockito.when(config.getChannelRegistry()).thenReturn(new ChannelRegistry(List.of()));
    }

    private void stubDiscord(boolean useDiscord, String token) {
        Mockito.when(config.get(ConfigKey.USE_DISCORD)).thenReturn(new ConfigValueWrapper(useDiscord));
        Mockito.when(config.get(ConfigKey.BOT_TOKEN)).thenReturn(new ConfigValueWrapper(token));
        Mockito.when(config.get(ConfigKey.SYSTEM_MESSAGES_CHANNEL_ID))
                .thenReturn(new ConfigValueWrapper("SYSTEM_CHANNEL_ID"));
        Mockito.when(config.get(ConfigKey.BOT_ACTIVITY_STATUS)).thenReturn(new ConfigValueWrapper("ONLINE"));
        Mockito.when(config.get(ConfigKey.BOT_ACTIVITY_TYPE)).thenReturn(new ConfigValueWrapper("WATCHING"));
        Mockito.when(config.get(ConfigKey.BOT_ACTIVITY_TEXT)).thenReturn(new ConfigValueWrapper("test"));
    }

    private Bot makeBot() {
        return new Bot(config, warnMessages::add, () -> 1, () -> 20);
    }

    @Test
    @DisplayName("Bot constructor registers reload listeners only when discord is enabled")
    public void testReloadListenerRegisteredWhenEnabled() {
        // Discord disabled – constructor returns early without registering listeners.
        List<Runnable> listeners = new ArrayList<>();
        Mockito.doAnswer(inv -> { listeners.add(inv.getArgument(0)); return null; })
                .when(config).addReloadListener(Mockito.any());

        stubDiscord(false, "TOKEN_HERE");
        makeBot();
        assertEquals(0, listeners.size(), "No listeners should be registered when discord is disabled");
    }

    @Test
    @DisplayName("Bot constructor registers exactly two reload listeners when discord is enabled")
    public void testReloadListenersRegisteredWhenEnabled() {
        List<Runnable> listeners = new ArrayList<>();
        Mockito.doAnswer(inv -> { listeners.add(inv.getArgument(0)); return null; })
                .when(config).addReloadListener(Mockito.any());

        stubDiscord(true, "REAL.BOT.TOKEN");
        makeBot();
        assertEquals(2, listeners.size(),
                "Bot must register exactly two listeners (updateActivity + updateStatus) when discord is enabled");
    }

    @Test
    @DisplayName("update() does not throw when bot is not connected")
    public void testUpdateDoesNotThrowWhenNotConnected() {
        stubDiscord(false, "TOKEN_HERE");
        Bot bot = makeBot();
        assertDoesNotThrow(bot::update, "update() must be safe to call when JDA is not running");
    }

    @Test
    @DisplayName("Calling update() multiple times does not throw")
    public void testMultipleUpdatesAreSafe() {
        stubDiscord(false, "TOKEN_HERE");
        Bot bot = makeBot();
        assertDoesNotThrow(() -> {
            bot.update();
            bot.update();
            bot.update();
        }, "Multiple successive update() calls must not throw");
    }
}
