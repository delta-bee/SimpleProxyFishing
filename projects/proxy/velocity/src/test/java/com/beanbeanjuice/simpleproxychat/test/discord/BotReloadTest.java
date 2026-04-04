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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Bot.reload() without a live JDA connection.
 *
 * Key invariant: when the token is unchanged and the bot is running, reload()
 * must route all JDA calls (presence, status) through the asyncRunner -- NOT
 * execute them inline on the calling thread. Calling JDA APIs inline on the
 * Velocity command thread was the root cause of the Discord->MC bridge dying
 * after /apc-reload.
 */
public class BotReloadTest {

    private Config config;
    private AtomicInteger asyncCallCount;
    private List<String> warnMessages;

    @BeforeEach
    public void setUp() {
        config = Mockito.mock(Config.class);
        asyncCallCount = new AtomicInteger(0);
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
        return new Bot(
                config,
                warnMessages::add,
                task -> asyncCallCount.incrementAndGet(),
                () -> 1,
                () -> 20
        );
    }

    @Test
    @DisplayName("reload() is a no-op when discord is disabled and bot is not running")
    public void testReloadNoopWhenDisabled() {
        stubDiscord(false, "TOKEN_HERE");
        Bot bot = makeBot();

        bot.reload();

        assertEquals(0, asyncCallCount.get(),
                "No async tasks should be scheduled when discord is disabled and bot is not running");
    }

    @Test
    @DisplayName("Bot constructor registers exactly one reload listener with config")
    public void testReloadListenerRegisteredExactlyOnce() {
        List<Runnable> listeners = new ArrayList<>();
        Mockito.doAnswer(inv -> { listeners.add(inv.getArgument(0)); return null; })
                .when(config).addReloadListener(Mockito.any());

        makeBot();

        assertEquals(1, listeners.size(),
                "Bot constructor must register exactly ONE reload listener. " +
                "More than one causes double-reload on every /apc-reload.");
    }

    @Test
    @DisplayName("reload() with enabled discord and non-placeholder token uses asyncRunner")
    public void testEnabledDiscordUsesAsyncRunner() {
        stubDiscord(true, "REAL.BOT.TOKEN");
        Bot bot = makeBot();

        bot.reload(); // bot==null, tokenChanged -> schedules async start

        assertTrue(asyncCallCount.get() > 0,
                "reload() must schedule work via asyncRunner when discord is enabled. " +
                "Inline JDA calls on the command thread break the event loop.");
    }

    @Test
    @DisplayName("The reload listener registered by Bot is safe to call and does not throw")
    public void testRegisteredListenerDoesNotThrow() {
        List<Runnable> listeners = new ArrayList<>();
        Mockito.doAnswer(inv -> { listeners.add(inv.getArgument(0)); return null; })
                .when(config).addReloadListener(Mockito.any());

        makeBot();

        assertFalse(listeners.isEmpty());
        assertDoesNotThrow(() -> listeners.get(0).run(),
                "The reload listener Bot registers with Config must not throw. " +
                "An exception here aborts Config.reload() and leaves plugin state inconsistent.");
    }

    @Test
    @DisplayName("Calling reload() multiple times does not throw")
    public void testMultipleReloadsAreSafe() {
        stubDiscord(false, "TOKEN_HERE");
        Bot bot = makeBot();

        assertDoesNotThrow(() -> {
            bot.reload();
            bot.reload();
            bot.reload();
        }, "Multiple successive reload() calls must not throw");
    }
}

