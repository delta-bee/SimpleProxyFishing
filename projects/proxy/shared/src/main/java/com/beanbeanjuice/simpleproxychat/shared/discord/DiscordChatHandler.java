package com.beanbeanjuice.simpleproxychat.shared.discord;

import com.beanbeanjuice.simpleproxychat.shared.config.Config;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigKey;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Listens for incoming Discord messages and routes them to the appropriate
 * {@link ChannelDefinition} handler based on the {@link ChannelRegistry}.
 *
 * <p>Only messages from channels that appear in the {@code channels:} list in
 * {@code config.yml} are processed. The system-messages channel
 * ({@code system-messages-channel-id}) is intentionally ignored here; it is
 * write-only from the plugin's perspective.
 *
 * <p>When {@code drop-ping-attempts: true} is set in {@code config.yml}, any
 * message whose raw content contains a Discord mention ({@code <@id>} or
 * {@code <@&id>}) is silently dropped and a WARN is emitted so the admin can
 * see it happened.
 */
public class DiscordChatHandler extends ListenerAdapter {

    /**
     * Matches Discord user mentions ({@code <@123>}) and role mentions
     * ({@code <@&123>}) in raw message content.
     */
    private static final Pattern PING_PATTERN = Pattern.compile("<@[!&]?\\d+>");

    private final Config config;
    private final String selfBotId;

    /**
     * Called when a valid message is received from a configured Discord channel.
     * The first argument is the originating {@link ChannelDefinition} (never {@code null}).
     * The second argument is the raw {@link MessageReceivedEvent}.
     */
    private final BiConsumer<ChannelDefinition, MessageReceivedEvent> sendFromDiscord;

    /** Called with a WARN-level message whenever a ping-attempt is dropped. */
    private final Consumer<String> warnLogger;

    public DiscordChatHandler(Config config,
                              String selfBotId,
                              BiConsumer<ChannelDefinition, MessageReceivedEvent> sendFromDiscord) {
        this(config, selfBotId, sendFromDiscord, msg -> System.err.printf("[AdvancedProxyChat] WARN: %s%n", msg));
    }

    public DiscordChatHandler(Config config,
                              String selfBotId,
                              BiConsumer<ChannelDefinition, MessageReceivedEvent> sendFromDiscord,
                              Consumer<String> warnLogger) {
        this.config = config;
        this.selfBotId = selfBotId;
        this.sendFromDiscord = sendFromDiscord;
        this.warnLogger = warnLogger;
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

        // -- 1.0.4: drop ping attempts --
        if (config.get(ConfigKey.DISCORD_DROP_PING_ATTEMPTS).asBoolean()) {
            String rawContent = event.getMessage().getContentRaw();
            if (PING_PATTERN.matcher(rawContent).find()) {
                warnLogger.accept(String.format(
                    "Dropped a message from Discord channel '%s' (user: %s) because it contained a ping attempt. " +
                    "Raw content: %s",
                    def.getName(), event.getAuthor().getName(), rawContent
                ));
                return;
            }
        }

        try {
            sendFromDiscord.accept(def, event);
        } catch (Exception e) {
            System.err.printf(
                "[AdvancedProxyChat] Exception while processing Discord message from channel '%s': %s%n",
                def.getName(), e.getMessage()
            );
        }
    }

}

