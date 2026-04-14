package com.beanbeanjuice.simpleproxychat.shared.chat;

import com.beanbeanjuice.simpleproxychat.common.CommonHelper;
import com.beanbeanjuice.simpleproxychat.shared.discord.Bot;
import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelDefinition;
import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelRegistry;
import com.beanbeanjuice.simpleproxychat.shared.discord.DiscordChatHandler;
import com.beanbeanjuice.simpleproxychat.shared.channel.PlayerChannelPrefsManager;
import com.beanbeanjuice.simpleproxychat.shared.socket.ChatMessageData;
import com.beanbeanjuice.simpleproxychat.shared.ISimpleProxyChat;
import com.beanbeanjuice.simpleproxychat.shared.helper.Helper;
import com.beanbeanjuice.simpleproxychat.shared.config.Config;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigKey;
import com.beanbeanjuice.simpleproxychat.shared.config.Permission;
import com.beanbeanjuice.simpleproxychat.shared.helper.EpochHelper;
import com.beanbeanjuice.simpleproxychat.shared.helper.LastMessagesHelper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import net.luckperms.api.query.QueryOptions;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatHandler {

    private static final String MINECRAFT_PLAYER_HEAD_URL = "https://crafthead.net/avatar/{PLAYER_UUID}";
    private static final Pattern PING_PATTERN = Pattern.compile("<@[!&]?\\d+>");
    /** Matches GIF URLs: direct .gif links and Tenor/Giphy embed links. */
    private static final Pattern GIF_PATTERN = Pattern.compile(
        "https?://\\S*\\.gif(?:\\?\\S*)?|https?://(?:tenor\\.com|giphy\\.com|media\\.giphy\\.com|c\\.tenor\\.com)\\S*",
        Pattern.CASE_INSENSITIVE
    );

    private final ISimpleProxyChat plugin;
    private final Config config;
    private final Bot discordBot;
    private final LastMessagesHelper lastMessagesHelper;
    private final PlayerChannelPrefsManager channelPrefsManager;

    public ChatHandler(ISimpleProxyChat plugin) {
        this.plugin = plugin;
        this.config = plugin.getSPCConfig();
        this.discordBot = plugin.getDiscordBot();
        this.lastMessagesHelper = new LastMessagesHelper(plugin.getSPCConfig());
        this.channelPrefsManager = plugin.getChannelPrefsManager();

        // Register the Discord listener. Routes messages from configured channels
        // to sendFromDiscord(ChannelDefinition, MessageReceivedEvent).
        plugin.getDiscordBot().addRunnableToQueue(() ->
            plugin.getDiscordBot().getJDA().ifPresent((jda) ->
                jda.addEventListener(new DiscordChatHandler(
                    config,
                    jda.getSelfUser().getId(),
                    this::sendFromDiscord
                ))
            )
        );
    }

    private Optional<String> getValidMessage(String message) {
        String messagePrefix = config.get(ConfigKey.PROXY_MESSAGE_PREFIX).asString();

        if (messagePrefix.isEmpty()) return Optional.of(message);
        if (!message.startsWith(messagePrefix)) return Optional.empty();

        message = message.substring(messagePrefix.length());
        if (message.isEmpty()) return Optional.empty();
        return Optional.of(message);
    }

    public void chat(ChatMessageData chatMessageData, String minecraftMessage, String discordMessage, String discordEmbedTitle, String discordEmbedMessage) {
        // Log to Console
        if (config.get(ConfigKey.CONSOLE_CHAT).asBoolean()) plugin.log(minecraftMessage);

        // Log to Minecraft
        if (config.get(ConfigKey.MINECRAFT_CHAT_ENABLED).asBoolean()) {
            chatMessageData.chatSendToAllOtherPlayers(minecraftMessage);
            lastMessagesHelper.addMessage(minecraftMessage);
        }

    }

    public void runProxyChatMessage(ChatMessageData chatMessageData) {
        if (Helper.serverHasChatLocked(plugin, chatMessageData.getServername())) return;

        String playerMessage = chatMessageData.getMessage();
        String serverName = chatMessageData.getServername();
        String playerName = chatMessageData.getPlayerName();
        UUID playerUUID = chatMessageData.getPlayerUUID();

        Optional<String> optionalPlayerMessage = getValidMessage(playerMessage);
        if (optionalPlayerMessage.isEmpty()) return;
        playerMessage = optionalPlayerMessage.get();

        // ------------------------------------------------------------------
        // Multi-channel !flag routing
        //
        //   !!<body>       → local server only (do not touch proxy or Discord)
        //   ! <body>       → MC proxy network only (no Discord)
        //   !<flags> <body>→ only the Discord channels for those flag letters
        //                    (ignores user send-prefs, obeys server disable-send)
        //   (no prefix)    → normal flow: MC proxy + all default-send channels
        // ------------------------------------------------------------------
        ChannelRegistry registry = config.getChannelRegistry();

        if (playerMessage.startsWith("!") && playerMessage.length() > 1) {
            // !! = local server only: return without doing anything at proxy level
            if (playerMessage.startsWith("!!")) return;

            // Collect flag characters up to the first space
            int flagEnd = 1;
            while (flagEnd < playerMessage.length() && playerMessage.charAt(flagEnd) != ' ') flagEnd++;
            String flags = playerMessage.substring(1, flagEnd).toLowerCase(java.util.Locale.ROOT);
            String body = (flagEnd < playerMessage.length() ? playerMessage.substring(flagEnd + 1) : "").trim();

            // Try to resolve flags as channel prefixes
            if (!body.isEmpty() && registry != null && !registry.isEmpty()) {
                Set<ChannelDefinition> targets = new LinkedHashSet<>();
                for (char flag : flags.toCharArray()) {
                    registry.byPrefix(flag).ifPresent(targets::add);
                }
                if (!targets.isEmpty()) {
                    // !flags body → those Discord channels only, skip MC proxy chat
                    for (ChannelDefinition channel : targets) {
                        if (channel.isDisableSend()) continue;
                        sendToDiscordChannel(channel, chatMessageData, playerName, playerUUID, serverName, body);
                    }
                    return;
                }
            }

            // ! alone (no recognised channel flags) → MC proxy network only, no Discord
            String mcBody = body.isEmpty() ? flags : (flags + " " + body);
            if (mcBody.isBlank()) return;
            String mcMinecraft = CommonHelper.replaceKeys(config.get(ConfigKey.MINECRAFT_CHAT_MESSAGE).asString(),
                    buildStandardReplacements(mcBody, playerName, playerUUID, serverName));
            if (config.get(ConfigKey.CONSOLE_CHAT).asBoolean()) plugin.log(mcMinecraft);
            if (config.get(ConfigKey.MINECRAFT_CHAT_ENABLED).asBoolean()) {
                chatMessageData.chatSendToAllOtherPlayers(mcMinecraft);
                lastMessagesHelper.addMessage(mcMinecraft);
            }
            return;
        }

        // ------------------------------------------------------------------
        // Default send: forward to all channels where the player has send enabled.
        // ------------------------------------------------------------------
        if (registry != null && !registry.isEmpty()) {
            String finalPlayerMessage = playerMessage;
            registry.all().forEach(channel -> {
                if (channel.isDisableSend()) return;
                if (!channelPrefsManager.shouldSend(playerUUID, channel)) return;
                sendToDiscordChannel(channel, chatMessageData, playerName, playerUUID, serverName, finalPlayerMessage);
            });
        }

        // Standard MC proxy chat + legacy single Discord channel.
        String finalPlayerMessage2 = playerMessage;
        // Use /nick display name for Discord; falls back to playerName if helper isn't installed.
        String displayName = chatMessageData.getDisplayName();

        String minecraftConfigString = config.get(ConfigKey.MINECRAFT_CHAT_MESSAGE).asString();
        String discordConfigString = config.get(ConfigKey.MINECRAFT_DISCORD_MESSAGE).asString();

        String aliasedServerName = Helper.convertAlias(config, serverName);

        HashMap<String, String> replacements = new HashMap<>(Map.ofEntries(
                Map.entry("message", finalPlayerMessage2),
                Map.entry("server", aliasedServerName),
                Map.entry("original_server", serverName),
                Map.entry("to", aliasedServerName),
                Map.entry("original_to", serverName),
                Map.entry("player", displayName),
                Map.entry("escaped_player", Helper.escapeString(displayName)),
                Map.entry("epoch", String.valueOf(EpochHelper.getEpochSecond())),
                Map.entry("time", getTimeString()),
                Map.entry("plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString()),
                Map.entry("prefix", getPrefix(playerUUID, aliasedServerName, serverName)),
                Map.entry("suffix", getSuffix(playerUUID, aliasedServerName, serverName))
        ));

        String minecraftMessage = CommonHelper.replaceKeys(minecraftConfigString, replacements);
        String discordMessage = CommonHelper.replaceKeys(discordConfigString, replacements);
        String discordEmbedTitle = CommonHelper.replaceKeys(config.get(ConfigKey.MINECRAFT_DISCORD_EMBED_TITLE).asString(), replacements);
        String discordEmbedMessage = CommonHelper.replaceKeys(config.get(ConfigKey.MINECRAFT_DISCORD_EMBED_MESSAGE).asString(), replacements);

        if (config.get(ConfigKey.USE_HELPER).asBoolean()) {
            chatMessageData.setMinecraftMessage(minecraftMessage);
            chatMessageData.setDiscordMessage(discordMessage);
            chatMessageData.setDiscordEmbedTitle(discordEmbedTitle);
            chatMessageData.setDiscordEmbedMessage(discordEmbedMessage);
            chatMessageData.startPluginMessage();
            return;
        }

        chat(chatMessageData, minecraftMessage, discordMessage, discordEmbedTitle, discordEmbedMessage);
    }

    /** Builds the standard replacement map used for MC-proxy and MC-only messages. */
    private HashMap<String, String> buildStandardReplacements(String message, String playerName, UUID playerUUID, String serverName) {
        String aliased = Helper.convertAlias(config, serverName);
        return new HashMap<>(Map.ofEntries(
                Map.entry("message", message),
                Map.entry("server", aliased),
                Map.entry("original_server", serverName),
                Map.entry("to", aliased),
                Map.entry("original_to", serverName),
                Map.entry("player", playerName),
                Map.entry("escaped_player", Helper.escapeString(playerName)),
                Map.entry("epoch", String.valueOf(EpochHelper.getEpochSecond())),
                Map.entry("time", getTimeString()),
                Map.entry("plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString()),
                Map.entry("prefix", getPrefix(playerUUID, aliased, serverName)),
                Map.entry("suffix", getSuffix(playerUUID, aliased, serverName))
        ));
    }

    /**
     * Sends a player message to a specific Discord channel using the per-channel
     * or global mc-to-discord format string.
     */
    private void sendToDiscordChannel(ChannelDefinition channel, ChatMessageData chatMessageData,
                                      String playerName, UUID playerUUID, String serverName, String message) {
        String aliasedServerName = Helper.convertAlias(config, serverName);

        // Strip ping attempts from the player message before it reaches Discord.
        if (config.get(ConfigKey.DISCORD_DROP_PING_ATTEMPTS).asBoolean()) {
            String sanitized = PING_PATTERN.matcher(message).replaceAll("[ping removed]");
            if (!sanitized.equals(message)) {
                System.err.printf("[AdvancedProxyChat] WARN: Stripped a ping attempt from %s's message before relaying to Discord channel '%s'.%n",
                        playerName, channel.getName());
                message = sanitized;
            }
        }

        // Always use the /nick display name when available; falls back to playerName automatically
        // (ChatMessageData.setDisplayName guards against blank values).
        // Display name is populated by the helper plugin. Without it, we fall back to playerName.
        String discordName = chatMessageData.getDisplayName();
        if (discordName.equals(playerName) && !config.get(ConfigKey.USE_HELPER).asBoolean()) {
            plugin.log("[AdvancedProxyChat] WARN: Sending message to Discord as '" + playerName + "' " +
                    "(no /nick available — helper plugin not enabled or not installed on sub-server).");
        }

        String fmt = channel.getMcToDiscordFormat() != null
                ? channel.getMcToDiscordFormat()
                : config.get(ConfigKey.MC_TO_DISCORD_FORMAT_DEFAULT).asString();

        HashMap<String, String> replacements = new HashMap<>(Map.ofEntries(
                Map.entry("message", message),
                Map.entry("server", aliasedServerName),
                Map.entry("original_server", serverName),
                Map.entry("player", discordName),
                Map.entry("escaped_player", Helper.escapeString(discordName)),
                Map.entry("channel", channel.getName()),
                Map.entry("epoch", String.valueOf(EpochHelper.getEpochSecond())),
                Map.entry("time", getTimeString()),
                Map.entry("plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString()),
                Map.entry("prefix", getPrefix(playerUUID, aliasedServerName, serverName)),
                Map.entry("suffix", getSuffix(playerUUID, aliasedServerName, serverName))
        ));

        String formatted = CommonHelper.replaceKeys(fmt, replacements);
        discordBot.sendMessageToChannel(channel.getChannelId(), formatted);
    }

    public void runProxyLeaveMessage(String playerName, UUID playerUUID, String serverName,
                                     BiConsumer<String, Permission> minecraftLogger) {
        String configString = config.get(ConfigKey.MINECRAFT_LEAVE).asString();
        String discordConfigString = config.get(ConfigKey.DISCORD_LEAVE_MESSAGE).asString();

        String aliasedServerName = Helper.convertAlias(config, serverName);

        HashMap<String, String> replacements = new HashMap<>(Map.ofEntries(
                Map.entry("player", playerName),
                Map.entry("escaped_player", Helper.escapeString(playerName)),
                Map.entry("server", aliasedServerName),
                Map.entry("original_server", serverName),
                Map.entry("to", aliasedServerName),
                Map.entry("original_to", serverName),
                Map.entry("epoch", String.valueOf(EpochHelper.getEpochSecond())),
                Map.entry("time", getTimeString()),
                Map.entry("plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString()),
                Map.entry("prefix", getPrefix(playerUUID, aliasedServerName, serverName)),
                Map.entry("suffix", getSuffix(playerUUID, aliasedServerName, serverName))
        ));

        String message = CommonHelper.replaceKeys(configString, replacements);
        String discordMessage = CommonHelper.replaceKeys(discordConfigString, replacements);

        // Log to Console
        if (config.get(ConfigKey.CONSOLE_LEAVE).asBoolean()) plugin.log(message);

        // Log to Discord
        DISCORD_SENT: if (config.get(ConfigKey.DISCORD_LEAVE_ENABLED).asBoolean()) {
            if (!config.get(ConfigKey.DISCORD_LEAVE_USE_EMBED).asBoolean()) {
                discordBot.sendSystemMessage(discordMessage);
                break DISCORD_SENT;
            }

            EmbedBuilder embedBuilder = simpleAuthorEmbedBuilder(playerUUID, discordMessage).setColor(Color.RED);
            if (config.get(ConfigKey.DISCORD_LEAVE_USE_TIMESTAMP).asBoolean()) embedBuilder.setTimestamp(EpochHelper.getEpochInstant());
            discordBot.sendSystemMessageEmbed(embedBuilder.build());
        }

        // Log to Minecraft
        if (config.get(ConfigKey.MINECRAFT_LEAVE_ENABLED).asBoolean()) minecraftLogger.accept(message, Permission.READ_LEAVE_MESSAGE);
    }

    public void runProxyJoinMessage(String playerName, UUID playerUUID, String serverName,
                                    BiConsumer<String, Permission> minecraftLogger) {
        String configString = config.get(ConfigKey.MINECRAFT_JOIN).asString();
        String discordConfigString = config.get(ConfigKey.DISCORD_JOIN_MESSAGE).asString();

        String aliasedServerName = Helper.convertAlias(config, serverName);

        HashMap<String, String> replacements = new HashMap<>(Map.ofEntries(
                Map.entry("player", playerName),
                Map.entry("escaped_player", Helper.escapeString(playerName)),
                Map.entry("server", aliasedServerName),
                Map.entry("to", aliasedServerName),
                Map.entry("original_server", serverName),
                Map.entry("original_to", serverName),
                Map.entry("epoch", String.valueOf(EpochHelper.getEpochSecond())),
                Map.entry("time", getTimeString()),
                Map.entry("plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString())
        ));

        String message = CommonHelper.replaceKeys(configString, replacements);
        String discordMessage = CommonHelper.replaceKeys(discordConfigString, replacements);

        // Log to Console
        if (config.get(ConfigKey.CONSOLE_JOIN).asBoolean()) plugin.log(message);

        // Log to Discord
        DISCORD_SENT: if (config.get(ConfigKey.DISCORD_JOIN_ENABLED).asBoolean()) {
            if (!config.get(ConfigKey.DISCORD_JOIN_USE_EMBED).asBoolean()) {
                discordBot.sendSystemMessage(discordMessage);
                break DISCORD_SENT;
            }

            EmbedBuilder embedBuilder = simpleAuthorEmbedBuilder(playerUUID, discordMessage).setColor(Color.GREEN);
            if (config.get(ConfigKey.DISCORD_JOIN_USE_TIMESTAMP).asBoolean()) embedBuilder.setTimestamp(EpochHelper.getEpochInstant());
            discordBot.sendSystemMessageEmbed(embedBuilder.build());
        }

        // Log to Minecraft
        if (config.get(ConfigKey.MINECRAFT_JOIN_ENABLED).asBoolean())
            minecraftLogger.accept(message, Permission.READ_JOIN_MESSAGE);
    }

    public void runProxySwitchMessage(String from, String to, String playerName, UUID playerUUID,
                                      Consumer<String> minecraftLogger, Consumer<String> playerLogger) {
        String consoleConfigString = config.get(ConfigKey.MINECRAFT_SWITCH_DEFAULT).asString();
        String discordConfigString = config.get(ConfigKey.DISCORD_SWITCH_MESSAGE).asString();
        String minecraftConfigString = config.get(ConfigKey.MINECRAFT_SWITCH_SHORT).asString();

        String aliasedFrom = Helper.convertAlias(config, from);
        String aliasedTo = Helper.convertAlias(config, to);

        HashMap<String, String> replacements = new HashMap<>(Map.ofEntries(
                Map.entry("from", aliasedFrom),
                Map.entry("original_from", from),
                Map.entry("to", aliasedTo),
                Map.entry("original_to", to),
                Map.entry("server", aliasedTo),
                Map.entry("original_server", to),
                Map.entry("player", playerName),
                Map.entry("escaped_player", Helper.escapeString(playerName)),
                Map.entry("epoch", String.valueOf(EpochHelper.getEpochSecond())),
                Map.entry("time", getTimeString()),
                Map.entry("prefix", getPrefix(playerUUID, aliasedTo, to)),
                Map.entry("suffix", getSuffix(playerUUID, aliasedTo, to))
        ));
        replacements.put("plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString()); // need this here because Map#of is limited to 10 entries.

        String consoleMessage = CommonHelper.replaceKeys(consoleConfigString, replacements);
        String discordMessage = CommonHelper.replaceKeys(discordConfigString, replacements);
        String minecraftMessage = CommonHelper.replaceKeys(minecraftConfigString, replacements);

        // Log to Console
        if (config.get(ConfigKey.CONSOLE_SWITCH).asBoolean()) plugin.log(consoleMessage);

        // Log to Discord
        DISCORD_SENT: if (config.get(ConfigKey.DISCORD_SWITCH_ENABLED).asBoolean()) {
            if (!config.get(ConfigKey.DISCORD_SWITCH_USE_EMBED).asBoolean()) {
                discordBot.sendSystemMessage(discordMessage);
                break DISCORD_SENT;
            }

            EmbedBuilder embedBuilder = simpleAuthorEmbedBuilder(playerUUID, discordMessage).setColor(Color.YELLOW);
            if (config.get(ConfigKey.DISCORD_SWITCH_USE_TIMESTAMP).asBoolean()) embedBuilder.setTimestamp(EpochHelper.getEpochInstant());
            discordBot.sendSystemMessageEmbed(embedBuilder.build());
        }

        // Log to Minecraft
        if (config.get(ConfigKey.MINECRAFT_SWITCH_ENABLED).asBoolean()) {
            minecraftLogger.accept(minecraftMessage);
            lastMessagesHelper.getBoundedArrayList().forEach(playerLogger);
        }
    }

    /**
     * Creates a sanitized {@link EmbedBuilder} based on the message.
     * @param playerUUID The {@link UUID} of the in-game player.
     * @param message The {@link String} message to send in the Discord server.
     * @return A sanitized {@link EmbedBuilder} containing the contents.
     */
    private EmbedBuilder simpleAuthorEmbedBuilder(UUID playerUUID, String message) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setAuthor(message, null, getPlayerHeadURL(playerUUID));
        return embedBuilder;
    }

    private String getPlayerHeadURL(UUID playerUUID) {
        return MINECRAFT_PLAYER_HEAD_URL.replace("{PLAYER_UUID}", playerUUID.toString());
    }

    /** Overload used by the multi-channel {@link DiscordChatHandler}. */
    public void sendFromDiscord(ChannelDefinition channelDef, MessageReceivedEvent event) {
        // Resolve format: per-channel override -> global channel default -> legacy single-channel key.
        String message;
        if (channelDef != null && channelDef.getDiscordToMcFormat() != null) {
            message = channelDef.getDiscordToMcFormat();
        } else if (channelDef != null && !config.getChannelRegistry().isEmpty()) {
            message = config.get(ConfigKey.DISCORD_TO_MC_FORMAT_DEFAULT).asString();
        } else {
            message = config.get(ConfigKey.DISCORD_CHAT_MINECRAFT_MESSAGE).asString();
        }

        String username = event.getAuthor().getName();
        String nickname = username;
        String roleName = "[no-role]";
        Color roleColor = Color.GRAY;

        if (event.getMember() != null) {
            if (event.getMember().getNickname() != null) nickname = event.getMember().getNickname();
            if (!event.getMember().getRoles().isEmpty()) {
                Role role = event.getMember().getRoles().get(0);
                roleName = role.getName();
                if (role.getColor() != null) roleColor = role.getColor();
            }
        }

        String discordMessage = event.getMessage().getContentStripped();
        String hex = "#" + Integer.toHexString(roleColor.getRGB()).substring(2);
        String channelName = channelDef.getName();
        final String finalNickname = nickname;
        final String finalUsername = username;

        plugin.log("[DEBUG-NICK] username=" + finalUsername + " nickname=" + finalNickname);

        // Pre-build both variants so we can pick per-player without formatting twice.
        HashMap<String, String> replacementsUser = new HashMap<>(Map.of(
                "role", String.format("<%s>%s</%s>", hex, roleName, hex),
                "user", finalUsername,
                "nick", finalNickname,
                "message", discordMessage,
                "channel", channelName,
                "epoch", String.valueOf(EpochHelper.getEpochSecond()),
                "time", getTimeString(),
                "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString()
        ));
        HashMap<String, String> replacementsNick = new HashMap<>(replacementsUser);
        replacementsNick.put("user", finalNickname);

        String formattedUser = CommonHelper.replaceKeys(message, replacementsUser);
        String formattedNick = CommonHelper.replaceKeys(message, replacementsNick);

        if (!config.get(ConfigKey.MINECRAFT_DISCORD_ENABLED).asBoolean()) return;

        // Log to console (use username variant as canonical).
        if (config.get(ConfigKey.CONSOLE_DISCORD_CHAT).asBoolean()) plugin.log(formattedUser);

        // Record history (username variant).
        channelPrefsManager.recordHistory(channelDef, formattedUser);

        // Deliver to each player individually, respecting their prefs.
        plugin.sendPerPlayer(formattedUser, (player) -> {
            if (!channelPrefsManager.shouldReceive(player, channelDef)) return false;

            // Decrement listen-once and notify if expired.
            int remaining = channelPrefsManager.getRemainingListenOnce(player, channelDef);
            if (remaining > 0) {
                int after = channelPrefsManager.decrementListenOnce(player, channelDef);
                if (after == 0) {
                    String expiredMsg = config.get(ConfigKey.MINECRAFT_COMMAND_LISTEN_EXPIRED).asString();
                    expiredMsg = CommonHelper.replaceKey(expiredMsg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
                    expiredMsg = CommonHelper.replaceKey(expiredMsg, "channel", channelDef.getName());
                    plugin.sendToPlayer(player, expiredMsg);
                }
            }

            // Pick the right formatted string for this player's nickname preference.
            boolean wantsNick = channelPrefsManager.shouldUseDiscordNickname(player);
            String personal = wantsNick ? formattedNick : formattedUser;
            plugin.log("[DEBUG-NICK] player=" + player + " wantsNick=" + wantsNick + " nick=" + finalNickname + " user=" + finalUsername);

            // Apply global GIF suppression.
            if (channelPrefsManager.shouldSuppressGifs(player)) {
                String stripped = GIF_PATTERN.matcher(personal).replaceAll("").trim();
                if (stripped.isEmpty()) return false; // pure GIF, nothing left to show
                plugin.sendToPlayer(player, stripped);
                return false; // we already sent it manually
            }

            // If this player's string differs from the default (formattedUser), send manually.
            if (!personal.equals(formattedUser)) {
                plugin.sendToPlayer(player, personal);
                return false;
            }

            return true; // let sendPerPlayer send formattedUser for us
        });
    }


    private List<String> getPrefixBasedOnServerContext(User user, String... serverKeys) {
        return user.resolveInheritedNodes(QueryOptions.nonContextual())
                .stream()
                .filter((node) -> {
                    if (!node.getContexts().containsKey("server")) return true;
                    for (String key : serverKeys) if (node.getContexts().contains("server", key)) return true;
                    return false;
                })
                .filter(Node::getValue)
                .filter(NodeType.PREFIX::matches)
                .map(NodeType.PREFIX::cast)
                .map(PrefixNode::getKey)
                .map(prefix -> prefix.replace("prefix.", "")) // 200.Owner.is.awesome
                .map(prefix -> prefix.split("\\."))  // [200, Owner, is, awesome]
                .sorted((left, right) -> {  // Sorting it properly.
                    try {
                        Integer leftWeight = Integer.parseInt(left[0]);
                        Integer rightWeight = Integer.parseInt(right[0]);

                        return rightWeight.compareTo(leftWeight);
                    } catch (NumberFormatException e) { return 0; }
                })
                .map(prefix -> Arrays.stream(prefix).skip(1).collect(Collectors.joining(".")))  // Owner.is.awesome
                .toList();
    }

    private List<String> getSuffixBasedOnServerContext(User user, String... serverKeys) {
        return user.resolveInheritedNodes(QueryOptions.nonContextual())
                .stream()
                .filter((node) -> {
                    if (!node.getContexts().containsKey("server")) return true;
                    for (String key : serverKeys) if (node.getContexts().contains("server", key)) return true;
                    return false;
                })
                .filter(Node::getValue)
                .filter(NodeType.SUFFIX::matches)
                .map(NodeType.SUFFIX::cast)
                .map(SuffixNode::getKey)
                .map(suffix -> suffix.replace("suffix.", "")) // 200.Owner.is.awesome
                .map(suffix -> suffix.split("\\."))  // [200, Owner, is, awesome]
                .sorted((left, right) -> {  // Sorting it properly.
                    try {
                        Integer leftWeight = Integer.parseInt(left[0]);
                        Integer rightWeight = Integer.parseInt(right[0]);

                        return rightWeight.compareTo(leftWeight);
                    } catch (NumberFormatException e) { return 0; }
                })
                .map(suffix -> Arrays.stream(suffix).skip(1).collect(Collectors.joining(".")))  // Owner.is.awesome
                .toList();
    }

    private String getPrefix(UUID playerUUID, String aliasedServerName, String serverName) {
        if (!this.plugin.isLuckPermsEnabled()) return "";

        return this.plugin.getLuckPerms()
                .map(LuckPerms.class::cast)
                .map((luckPerms) -> {
                    User user = null;
                    try {
                        user = luckPerms.getUserManager().loadUser(playerUUID).get();
                    } catch (Exception e) {
                        plugin.log("Error contacting the LuckPerms API: " + e.getMessage());
                        return "";
                    }

                    // Get prefix based on aliased name. If none show up, use original name. If none show up, use top prefix.
                    List<String> prefixList = getPrefixBasedOnServerContext(user, serverName, aliasedServerName, "");

                    return prefixList.isEmpty() ? "" : CommonHelper.translateLegacyCodes(prefixList.get(0));
                })
                .orElse("");
    }

    private String getSuffix(UUID playerUUID, String aliasedServerName, String serverName) {
        if (!this.plugin.isLuckPermsEnabled()) return "";

        return this.plugin.getLuckPerms()
                .map(LuckPerms.class::cast)
                .map(luckPerms -> {
                    User user;
                    try {
                        user = luckPerms.getUserManager().loadUser(playerUUID).get();
                    } catch (Exception e) {
                        plugin.log("Error contacting the LuckPerms API: " + e.getMessage());
                        return "";
                    }

                    List<String> suffixList = getSuffixBasedOnServerContext(user, serverName, aliasedServerName, "");

                    return suffixList.isEmpty() ? "" : CommonHelper.translateLegacyCodes(suffixList.get(0));
                })
                .orElse("");
    }

    /**
     * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html">Format</a>
     */
    private String getTimeString() {
        DateTimeZone zone = config.get(ConfigKey.TIMESTAMP_TIMEZONE).asDateTimeZone();
        DateTimeFormatter format = DateTimeFormat.forPattern(config.get(ConfigKey.TIMESTAMP_FORMAT).asString());

        long timeInMillis = EpochHelper.getEpochMillisecond();
        DateTime time = new DateTime(timeInMillis).withZone(zone);

        return time.toString(format);
    }

}
