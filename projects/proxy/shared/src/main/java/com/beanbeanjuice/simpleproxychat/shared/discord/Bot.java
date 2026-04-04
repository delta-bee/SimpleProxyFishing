package com.beanbeanjuice.simpleproxychat.shared.discord;

import com.beanbeanjuice.simpleproxychat.shared.config.Config;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigKey;
import com.beanbeanjuice.simpleproxychat.shared.helper.Helper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.awt.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Bot {

    private final Config config;
    private final Consumer<String> errorLogger;
    private JDA bot;

    private final Supplier<Integer> getOnlinePlayers;
    private final Supplier<Integer> getMaxPlayers;

    private final Queue<Runnable> runnableQueue;

    /** Registry populated after JDA is ready. {@code null} until then. */
    private ChannelRegistry channelRegistry;

    private boolean channelTopicErrorSent = false;

    public Bot(final Config config, final Consumer<String> errorLogger, final Supplier<Integer> getOnlinePlayers, final Supplier<Integer> getMaxPlayers) {
        this.config = config;
        this.errorLogger = errorLogger;

        this.getOnlinePlayers = getOnlinePlayers;
        this.getMaxPlayers = getMaxPlayers;

        this.runnableQueue = new ConcurrentLinkedQueue<>();

        if (!config.get(ConfigKey.USE_DISCORD).asBoolean()) {
            bot = null;
            return;
        }

        config.addReloadListener(this::updateActivity);
        config.addReloadListener(this::updateStatus);
    }

    // =========================================================================
    // System-messages channel
    // Used exclusively for join/leave/switch events, proxy-status embeds,
    // server-status updates, and channel topic updates.
    // Chat bridging must be done via the 'channels:' list in config.yml.
    // =========================================================================

    /**
     * Returns the configured system-messages Discord channel, if it exists and JDA is ready.
     */
    public Optional<TextChannel> getSystemMessagesChannel() {
        if (bot == null) return Optional.empty();
        String id = config.get(ConfigKey.SYSTEM_MESSAGES_CHANNEL_ID).asString();
        if (id.isEmpty() || id.equalsIgnoreCase("SYSTEM_CHANNEL_ID")) return Optional.empty();
        return Optional.ofNullable(bot.getTextChannelById(id));
    }

    /**
     * Send a plain-text system message (join/leave/switch) to the system-messages channel.
     */
    public void sendSystemMessage(final String messageToSend) {
        if (bot == null) return;

        getSystemMessagesChannel().ifPresentOrElse(
                (channel) -> {
                    String message = Helper.sanitize(messageToSend);
                    message = Arrays.stream(message.split(" ")).map((word) -> {
                        if (!word.startsWith("@")) return word;
                        String name = word.replace("@", "");
                        Optional<Member> potentialMember = channel.getMembers().stream()
                                .filter((m) -> (m.getNickname() != null && m.getNickname().equalsIgnoreCase(name))
                                        || m.getEffectiveName().equalsIgnoreCase(name))
                                .findFirst();
                        return potentialMember.map(IMentionable::getAsMention).orElse(word);
                    }).collect(Collectors.joining(" "));
                    channel.sendMessage(message).queue();
                },
                () -> errorLogger.accept("Could not send a system message to Discord. " +
                        "Is 'system-messages-channel-id' set correctly in config.yml?")
        );
    }

    /**
     * Send an embed to the system-messages channel (proxy status, server status).
     * The embed is sanitized before sending.
     */
    public void sendSystemMessageEmbed(final MessageEmbed embed) {
        if (bot == null) return;

        getSystemMessagesChannel().ifPresentOrElse(
                (channel) -> channel.sendMessageEmbeds(sanitizeEmbed(embed)).queue(),
                () -> errorLogger.accept("Could not send a system embed to Discord. " +
                        "Is 'system-messages-channel-id' set correctly in config.yml?")
        );
    }

    /**
     * Update the topic of the system-messages channel.
     */
    public void updateSystemChannelTopic(final String topic) {
        if (bot == null) return;

        getSystemMessagesChannel().ifPresentOrElse(
                (textChannel) -> {
                    try {
                        textChannel.getManager().setTopic(topic).queue();
                    } catch (InsufficientPermissionException e) {
                        if (!channelTopicErrorSent) {
                            channelTopicErrorSent = true;
                            errorLogger.accept("No permission to edit the system channel topic. " +
                                    "If you don't want channel topics updated, ignore this. " +
                                    "Otherwise, grant the bot MANAGE_CHANNELS. " +
                                    "This message will only appear once per restart.");
                        }
                    }
                },
                () -> {} // system channel ID may not be set; silently skip
        );
    }

    // =========================================================================
    // Per-channel multi-channel helpers
    // =========================================================================

    /** Look up a Discord {@link TextChannel} by its raw channel ID. */
    public Optional<TextChannel> getTextChannel(String channelId) {
        if (bot == null) return Optional.empty();
        return Optional.ofNullable(bot.getTextChannelById(channelId));
    }

    /**
     * Send a plain-text message to the Discord channel described by {@code channel}.
     * Respects {@link ChannelDefinition#isDisableSend()}.
     */
    public void sendMessage(final ChannelDefinition channel, final String messageToSend) {
        if (bot == null) return;
        if (channel.isDisableSend()) return;

        getTextChannel(channel.getChannelId()).ifPresentOrElse(
                (textChannel) -> {
                    String message = Helper.sanitize(messageToSend);
                    message = Arrays.stream(message.split(" ")).map((originalString) -> {
                        if (!originalString.startsWith("@")) return originalString;
                        String name = originalString.replace("@", "");
                        List<Member> potentialMembers = textChannel.getMembers();
                        Optional<Member> potentialMember = potentialMembers.stream()
                                .filter((member) -> ((member.getNickname() != null && member.getNickname().equalsIgnoreCase(name)) || member.getEffectiveName().equalsIgnoreCase(name)))
                                .findFirst();
                        return potentialMember.map(IMentionable::getAsMention).orElse(originalString);
                    }).collect(Collectors.joining(" "));
                    textChannel.sendMessage(message).queue();
                },
                () -> errorLogger.accept("There was an error sending a message to Discord channel '" + channel.getName() + "'. Does the channel exist?")
        );
    }

    /**
     * Send a plain-text message directly to a Discord channel identified by its snowflake ID.
     * Used by the {@code !flag} routing in {@link com.beanbeanjuice.simpleproxychat.shared.chat.ChatHandler}.
     */
    public void sendMessageToChannel(final String channelId, final String messageToSend) {
        if (bot == null) return;
        getTextChannel(channelId).ifPresentOrElse(
                (textChannel) -> textChannel.sendMessage(Helper.sanitize(messageToSend)).queue(),
                () -> errorLogger.accept("There was an error sending a message to Discord channel ID '" + channelId + "'. Does the channel exist?")
        );
    }

    /**
     * Send a {@link MessageEmbed} to the Discord channel described by {@code channel}.
     * Respects {@link ChannelDefinition#isDisableSend()}.
     * The embed must already be sanitized before calling this method.
     */
    public void sendMessageEmbed(final ChannelDefinition channel, final MessageEmbed embed) {
        if (bot == null) return;
        if (channel.isDisableSend()) return;

        getTextChannel(channel.getChannelId()).ifPresentOrElse(
                (textChannel) -> textChannel.sendMessageEmbeds(sanitizeEmbed(embed)).queue(),
                () -> errorLogger.accept("There was an error sending an embed to Discord channel '" + channel.getName() + "'. Does the channel exist?")
        );
    }

    /**
     * Update the topic of the Discord channel described by {@code channel}.
     * Respects {@link ChannelDefinition#isDisableSend()}.
     */
    public void updateChannelTopic(final ChannelDefinition channel, final String topic) {
        if (bot == null) return;
        if (channel.isDisableSend()) return;

        getTextChannel(channel.getChannelId()).ifPresentOrElse(
                (textChannel) -> {
                    try {
                        textChannel.getManager().setTopic(topic).queue();
                    } catch (InsufficientPermissionException e) {
                        if (!channelTopicErrorSent) {
                            channelTopicErrorSent = true;
                            errorLogger.accept("No permission to edit channel topic for '" + channel.getName() + "'. " +
                                    "If you don't want the channel topics to be updated, simply ignore this message. " +
                                    "Otherwise, please give the Discord bot the MANAGE_CHANNELS permission. " +
                                    "This message will only be sent once per server restart.");
                        }
                    }
                },
                () -> errorLogger.accept("There was an error updating the Discord channel topic for '" + channel.getName() + "'. Does the channel exist?")
        );
    }

    /** Returns the current {@link ChannelRegistry}, or {@code null} if JDA has not started yet. */
    public ChannelRegistry getChannelRegistry() {
        return channelRegistry;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private MessageEmbed sanitizeEmbed(final MessageEmbed oldEmbed) {
        EmbedBuilder embedBuilder = new EmbedBuilder(oldEmbed);

        if (oldEmbed.getTitle() != null)
            embedBuilder.setTitle(Helper.sanitize(oldEmbed.getTitle()));

        if (oldEmbed.getAuthor() != null)
            embedBuilder.setAuthor(
                    Helper.sanitize(oldEmbed.getAuthor().getName()),
                    oldEmbed.getAuthor().getUrl(),
                    oldEmbed.getAuthor().getIconUrl()
            );

        if (oldEmbed.getDescription() != null)
            embedBuilder.setDescription(Helper.sanitize(oldEmbed.getDescription()));

        if (oldEmbed.getFooter() != null)
            embedBuilder.setFooter(
                    Helper.sanitize(oldEmbed.getFooter().getText()),
                    oldEmbed.getFooter().getIconUrl()
            );

        if (!oldEmbed.getFields().isEmpty()) {
            List<MessageEmbed.Field> fields = new ArrayList<>(oldEmbed.getFields());
            embedBuilder.clearFields();

            for (MessageEmbed.Field field : fields) {
                embedBuilder.addField(
                        Helper.sanitize(field.getName()),
                        Helper.sanitize(field.getValue()),
                        field.isInline()
                );
            }
        }

        return embedBuilder.build();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void channelUpdaterFunction() {
        if (bot == null) return;
        String topicMessage = config.get(ConfigKey.DISCORD_TOPIC_ONLINE).asString()
                .replace("%online%", String.valueOf(getOnlinePlayers.get()));

        // Update topic on every configured chat channel.
        if (channelRegistry != null && !channelRegistry.isEmpty()) {
            channelRegistry.all().forEach((ch) -> updateChannelTopic(ch, topicMessage));
        }

        // Also update the system-messages channel topic.
        updateSystemChannelTopic(topicMessage);
    }

    public Optional<JDA> getJDA() {
        return Optional.ofNullable(bot);
    }

    public void addRunnableToQueue(final Runnable runnable) {
        this.runnableQueue.add(runnable);
    }

    public void start() throws InterruptedException {
        String token = config.get(ConfigKey.BOT_TOKEN).asString();
        if (token.isEmpty() || token.equalsIgnoreCase("TOKEN_HERE") || token.equalsIgnoreCase("null")) return;

        bot = JDABuilder
                .createLight(token)
                .setActivity(Activity.watching("Starting Proxy..."))
                .enableCache(CacheFlag.ROLE_TAGS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                .build().awaitReady();

        // Sync the channel registry from config now that JDA is ready.
        this.channelRegistry = config.getChannelRegistry();

        validateDiscordConfig();
        sendProxyStatus(true);

        this.updateActivity();
        this.updateStatus();

        this.runnableQueue.forEach(Runnable::run);
    }

    /**
     * Logs actionable warnings for common Discord configuration mistakes, so admins
     * know exactly what to fix without having to dig through the code.
     */
    private void validateDiscordConfig() {
        String sysId = config.get(ConfigKey.SYSTEM_MESSAGES_CHANNEL_ID).asString();
        boolean sysChannelConfigured = !sysId.isEmpty()
                && !sysId.equalsIgnoreCase("SYSTEM_CHANNEL_ID")
                && !sysId.equalsIgnoreCase("GLOBAL_CHANNEL_ID");

        if (!sysChannelConfigured) {
            errorLogger.accept("[Config] 'system-messages-channel-id' is not set. " +
                    "Join/leave/switch events and proxy-status embeds will NOT be sent to Discord. " +
                    "Set it to your system-events channel ID in config.yml.");
        } else if (bot.getTextChannelById(sysId) == null) {
            errorLogger.accept("[Config] 'system-messages-channel-id' is set to '" + sysId + "' " +
                    "but that channel could not be found. Check the ID and that the bot has access.");
        }

        if (channelRegistry == null || channelRegistry.isEmpty()) {
            errorLogger.accept("[Config] No channels are configured under 'channels:' in config.yml. " +
                    "Chat bridging (Minecraft <-> Discord) is disabled. " +
                    "Add at least one entry to the 'channels:' list to enable it.");
        } else {
            channelRegistry.all().forEach((ch) -> {
                if (ch.getChannelId().isEmpty() || ch.getChannelId().equalsIgnoreCase("000000000000")) {
                    errorLogger.accept("[Config] Channel '" + ch.getName() + "' has no 'channel-id' set. " +
                            "Messages for this channel will fail to send.");
                } else if (bot.getTextChannelById(ch.getChannelId()) == null) {
                    errorLogger.accept("[Config] Channel '" + ch.getName() + "' points to Discord channel ID '" +
                            ch.getChannelId() + "' which could not be found. " +
                            "Check the ID and that the bot has access.");
                }
            });
        }
    }

    public void updateActivity() {
        this.getJDA().ifPresent((jda) -> {
            int onlinePlayers = getOnlinePlayers.get();
            int maxPlayers = getMaxPlayers.get();

            Activity.ActivityType type;
            String text;

            try {
                type = Activity.ActivityType.valueOf(config.get(ConfigKey.BOT_ACTIVITY_TYPE).asString());
                text = config.get(ConfigKey.BOT_ACTIVITY_TEXT).asString();
            } catch (Exception e) {
                type = Activity.ActivityType.WATCHING;
                text = "CONFIG ERROR";
            }

            text = text.replace("%online%", String.valueOf(onlinePlayers))
                       .replace("%max-players%", String.valueOf(maxPlayers));
            jda.getPresence().setActivity(Activity.of(type, text));
        });
    }

    public void updateStatus() {
        this.getJDA().ifPresent((jda) -> {
            OnlineStatus status;

            try {
                status = OnlineStatus.valueOf(config.get(ConfigKey.BOT_ACTIVITY_STATUS).asString());
            } catch (Exception e) {
                status = OnlineStatus.IDLE;
            }
            jda.getPresence().setStatus(status);
        });
    }

    public void sendProxyStatus(final boolean isStart) {
        if (!config.get(ConfigKey.DISCORD_PROXY_STATUS_ENABLED).asBoolean()) return;

        if (isStart) {
            sendSystemMessageEmbed(
                    new EmbedBuilder()
                            .setTitle(config.get(ConfigKey.DISCORD_PROXY_STATUS_MODULE_ENABLED).asString())
                            .setColor(Color.GREEN)
                            .build()
            );
        } else {
            sendSystemMessageEmbed(
                    new EmbedBuilder()
                            .setTitle(config.get(ConfigKey.DISCORD_PROXY_STATUS_MODULE_DISABLED).asString())
                            .setColor(Color.RED)
                            .build()
            );
        }
    }

    public void stop() {
        if (bot == null) return;
        sendProxyStatus(false);

        String offlineTopic = config.get(ConfigKey.DISCORD_TOPIC_OFFLINE).asString();
        if (channelRegistry != null && !channelRegistry.isEmpty()) {
            channelRegistry.all().forEach((ch) -> updateChannelTopic(ch, offlineTopic));
        }
        updateSystemChannelTopic(offlineTopic);

        this.getJDA().ifPresent((jda) -> {
            try {
                jda.shutdown();
                if (!jda.awaitShutdown(Duration.ofSeconds(10))) {
                    jda.shutdownNow();
                    jda.awaitShutdown();
                }
            } catch (InterruptedException ignored) { }
        });
    }

}

