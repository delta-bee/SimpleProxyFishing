package com.beanbeanjuice.simpleproxychat.test.discord;

import com.beanbeanjuice.simpleproxychat.shared.config.Config;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigKey;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigValueWrapper;
import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelDefinition;
import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelRegistry;
import com.beanbeanjuice.simpleproxychat.shared.discord.DiscordChatHandler;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that DiscordChatHandler is resilient to exceptions during message processing.
 *
 * Critical invariant: onMessageReceived must NEVER let an exception escape into JDA.
 * If it does, JDA silently unregisters the listener and Discord->MC bridging stops
 * for the rest of the session with no visible error in the server log.
 */
public class DiscordChatHandlerTest {

    private static final String SELF_BOT_ID = "BOT_ID_123";
    private static final String CHANNEL_ID = "DISCORD_CHANNEL_999";

    private Config config;
    private MessageReceivedEvent event;
    private User author;
    private Message message;
    private MessageChannelUnion channel;

    @BeforeEach
    public void setUp() {
        config = Mockito.mock(Config.class);
        event = Mockito.mock(MessageReceivedEvent.class);
        author = Mockito.mock(User.class);
        message = Mockito.mock(Message.class);
        channel = Mockito.mock(MessageChannelUnion.class);

        Mockito.when(author.getId()).thenReturn("PLAYER_123");
        Mockito.when(event.getAuthor()).thenReturn(author);
        Mockito.when(event.getMessage()).thenReturn(message);
        Mockito.when(event.getChannel()).thenReturn(channel);
        Mockito.when(event.getMember()).thenReturn(Mockito.mock(Member.class));
        Mockito.when(channel.getId()).thenReturn(CHANNEL_ID);
        Mockito.when(message.getContentStripped()).thenReturn("Hello from Discord");

        Mockito.when(config.get(ConfigKey.DISCORD_CHAT_ENABLED))
                .thenReturn(new ConfigValueWrapper(true));

        ChannelDefinition def = new ChannelDefinition("general", 'g', CHANNEL_ID, false, false, null, null);
        Mockito.when(config.getChannelRegistry())
                .thenReturn(new ChannelRegistry(List.of(def)));
    }

    @Test
    @DisplayName("Handler invokes sendFromDiscord for a matching channel message")
    public void testHandlerRoutesMatchingMessage() {
        AtomicBoolean called = new AtomicBoolean(false);
        DiscordChatHandler handler = new DiscordChatHandler(config, SELF_BOT_ID,
                (def, evt) -> called.set(true));

        handler.onMessageReceived(event);

        assertTrue(called.get(), "sendFromDiscord should be called for a matching channel");
    }

    @Test
    @DisplayName("Handler ignores messages from the bot itself")
    public void testHandlerIgnoresSelf() {
        Mockito.when(author.getId()).thenReturn(SELF_BOT_ID);
        AtomicBoolean called = new AtomicBoolean(false);
        DiscordChatHandler handler = new DiscordChatHandler(config, SELF_BOT_ID,
                (def, evt) -> called.set(true));

        handler.onMessageReceived(event);

        assertFalse(called.get(), "Handler must ignore its own messages");
    }

    @Test
    @DisplayName("Handler ignores messages from channels not in the registry")
    public void testHandlerIgnoresUnknownChannel() {
        Mockito.when(channel.getId()).thenReturn("UNKNOWN_CHANNEL");
        AtomicBoolean called = new AtomicBoolean(false);
        DiscordChatHandler handler = new DiscordChatHandler(config, SELF_BOT_ID,
                (def, evt) -> called.set(true));

        handler.onMessageReceived(event);

        assertFalse(called.get(), "Handler must not route messages from unconfigured channels");
    }

    @Test
    @DisplayName("Exception thrown inside sendFromDiscord does NOT escape into JDA")
    public void testExceptionInHandlerDoesNotPropagate() {
        DiscordChatHandler handler = new DiscordChatHandler(config, SELF_BOT_ID,
                (def, evt) -> { throw new RuntimeException("Simulated MiniMessage parse failure"); });

        // Must not throw -- if it does, JDA unregisters this listener permanently
        assertDoesNotThrow(() -> handler.onMessageReceived(event),
                "An exception inside sendFromDiscord must be swallowed. " +
                "If it escapes, JDA silently unregisters the listener and " +
                "Discord->MC bridging stops working until the next server restart.");
    }

    @Test
    @DisplayName("Listener continues processing messages after one caused an exception")
    public void testListenerSurvivesException() {
        AtomicInteger callCount = new AtomicInteger(0);
        DiscordChatHandler handler = new DiscordChatHandler(config, SELF_BOT_ID, (def, evt) -> {
            if (callCount.incrementAndGet() == 1) throw new RuntimeException("First message blew up");
        });

        handler.onMessageReceived(event); // throws internally, must be swallowed
        handler.onMessageReceived(event); // must still reach the handler

        assertEquals(2, callCount.get(),
                "Handler must process subsequent messages even after one caused an exception. " +
                "This is the core regression being fixed: reload caused the listener to die.");
    }

    @Test
    @DisplayName("Handler respects disable-receive flag on channel definition")
    public void testHandlerRespectsDisableReceive() {
        ChannelDefinition noReceive = new ChannelDefinition("general", 'g', CHANNEL_ID, false, true, null, null);
        Mockito.when(config.getChannelRegistry())
                .thenReturn(new ChannelRegistry(List.of(noReceive)));

        AtomicBoolean called = new AtomicBoolean(false);
        DiscordChatHandler handler = new DiscordChatHandler(config, SELF_BOT_ID,
                (def, evt) -> called.set(true));

        handler.onMessageReceived(event);

        assertFalse(called.get(), "Handler must not route when disable-receive=true");
    }

    @Test
    @DisplayName("Handler respects DISCORD_CHAT_ENABLED = false")
    public void testHandlerRespectsDisabledConfig() {
        Mockito.when(config.get(ConfigKey.DISCORD_CHAT_ENABLED))
                .thenReturn(new ConfigValueWrapper(false));

        AtomicBoolean called = new AtomicBoolean(false);
        DiscordChatHandler handler = new DiscordChatHandler(config, SELF_BOT_ID,
                (def, evt) -> called.set(true));

        handler.onMessageReceived(event);

        assertFalse(called.get(), "Handler must not route when discord chat is globally disabled");
    }
}

