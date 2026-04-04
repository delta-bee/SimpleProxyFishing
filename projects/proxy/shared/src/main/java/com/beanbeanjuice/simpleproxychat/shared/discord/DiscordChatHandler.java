package com.beanbeanjuice.simpleproxychat.shared.discord;

import com.beanbeanjuice.simpleproxychat.shared.config.Config;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigKey;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Listens for incoming Discord messages and routes them to the appropriate
 * {@link ChannelDefinition} handler based on the {@link ChannelRegistry}.
 *
 * <p>Only messages from channels that appear in the {@code channels:} list in
 * {@code config.yml} are processed. The system-messages channel
 * ({@code system-messages-channel-id}) is intentionally ignored here; it is
 * write-only from the plugin's perspective.
 */
public class DiscordChatHandler extends ListenerAdapter {

    private final Config config;
    private final String selfBotId;

    /**
     * Called when a valid message is received from a configured Discord channel.
     * The first argument is the originating {@link ChannelDefinition} (never {@code null}).
     * The second argument is the raw {@link MessageReceivedEvent}.
     */
    private final BiConsumer<ChannelDefinition, MessageReceivedEvent> sendFromDiscord;

    public DiscordChatHandler(Config config,
                              String selfBotId,
                              BiConsumer<ChannelDefinition, MessageReceivedEvent> sendFromDiscord) {
        this.config = config;
        this.selfBotId = selfBotId;
        this.sendFromDiscord = sendFromDiscord;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().getId().equals(selfBotId)) return;
        if (!config.get(ConfigKey.DISCORD_CHAT_ENABLED).asBoolean()) return;

        ChannelRegistry registry = config.getChannelRegistry();
        if (registry == null || registry.isEmpty()) return;

        // Only process messages from channels that are explicitly configured.
        Optional<ChannelDefinition> maybeDef = registry.byChannelId(event.getChannel().getId());
        if (maybeDef.isEmpty()) return;

        ChannelDefinition def = maybeDef.get();
        if (def.isDisableReceive()) return;

        sendFromDiscord.accept(def, event);
    }

}

