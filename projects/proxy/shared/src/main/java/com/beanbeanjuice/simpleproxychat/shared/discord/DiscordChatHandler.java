package com.beanbeanjuice.simpleproxychat.shared.discord;

import com.beanbeanjuice.simpleproxychat.shared.config.Config;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigKey;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DiscordChatHandler extends ListenerAdapter {

    private final Config config;
    private final String selfBotId;

    /**
     * Called when a valid message is received from Discord.
     * The first argument is the originating {@link ChannelDefinition} (never {@code null}).
     * The second argument is the raw {@link MessageReceivedEvent}.
     */
    private final BiConsumer<ChannelDefinition, MessageReceivedEvent> sendFromDiscord;

    /**
     * Legacy constructor for single-channel backwards-compat.
     * Routes all messages to the provided consumer regardless of channel.
     */
    public DiscordChatHandler(Config config,
                              String selfBotId,
                              Consumer<MessageReceivedEvent> sendFromDiscord) {
        this.config = config;
        this.selfBotId = selfBotId;
        // Wrap the old Consumer so it fits the new BiConsumer signature.
        this.sendFromDiscord = (channel, event) -> sendFromDiscord.accept(event);
    }

    /**
     * Multi-channel constructor. The {@link ChannelDefinition} (or {@code null} in legacy mode)
     * is resolved in {@link #onMessageReceived} and passed to {@code sendFromDiscord}.
     */
    public DiscordChatHandler(Config config,
                              String selfBotId,
                              BiConsumer<ChannelDefinition, MessageReceivedEvent> sendFromDiscord) {
        this.config = config;
        this.selfBotId = selfBotId;
        this.sendFromDiscord = sendFromDiscord;
    }

    /**
     * Registry-aware constructor. The {@link ChannelRegistry} is consulted on each incoming
     * message to decide which {@link ChannelDefinition} owns the channel.
     * @deprecated Prefer the {@link #DiscordChatHandler(Config, String, BiConsumer)} constructor
     *             and let {@link #onMessageReceived} handle the registry lookup from config.
     */
    @Deprecated
    public DiscordChatHandler(Config config,
                              String selfBotId,
                              ChannelRegistry channelRegistry,
                              BiConsumer<ChannelDefinition, MessageReceivedEvent> sendFromDiscord) {
        this.config = config;
        this.selfBotId = selfBotId;
        // Pre-wrap: look up the registry, then dispatch.
        this.sendFromDiscord = (ignored, event) -> {
            Optional<ChannelDefinition> maybeDef = channelRegistry.byChannelId(event.getChannel().getId());
            maybeDef.ifPresent((def) -> sendFromDiscord.accept(def, event));
        };
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().getId().equals(selfBotId)) return;
        if (!config.get(ConfigKey.DISCORD_CHAT_ENABLED).asBoolean()) return;

        ChannelRegistry registry = config.getChannelRegistry();

        if (registry != null && !registry.isEmpty()) {
            // Multi-channel mode: only process messages from known channels that allow receiving.
            Optional<ChannelDefinition> maybeDef = registry.byChannelId(event.getChannel().getId());
            if (maybeDef.isEmpty()) return;
            ChannelDefinition def = maybeDef.get();
            if (def.isDisableReceive()) return;
            sendFromDiscord.accept(def, event);
        } else {
            // Legacy single-channel mode: fall back to the old CHANNEL-ID key.
            if (!event.getChannel().getId().equalsIgnoreCase(config.get(ConfigKey.CHANNEL_ID).asString())) return;
            sendFromDiscord.accept(null, event);
        }
    }

}
