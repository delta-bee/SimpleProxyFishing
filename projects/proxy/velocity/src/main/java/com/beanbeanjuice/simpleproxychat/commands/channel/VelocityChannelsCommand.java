package com.beanbeanjuice.simpleproxychat.commands.channel;

import com.beanbeanjuice.simpleproxychat.SimpleProxyChatVelocity;
import com.beanbeanjuice.simpleproxychat.common.CommonHelper;
import com.beanbeanjuice.simpleproxychat.shared.channel.PlayerChannelPrefsManager;
import com.beanbeanjuice.simpleproxychat.shared.config.Config;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigKey;
import com.beanbeanjuice.simpleproxychat.shared.config.Permission;
import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelDefinition;
import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelRegistry;
import com.beanbeanjuice.simpleproxychat.shared.helper.Helper;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;

public class VelocityChannelsCommand implements SimpleCommand {

    private final SimpleProxyChatVelocity plugin;
    private final Config config;
    private final PlayerChannelPrefsManager prefsManager;

    public VelocityChannelsCommand(final SimpleProxyChatVelocity plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.prefsManager = plugin.getChannelPrefsManager();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        if (!config.get(ConfigKey.USE_PERMISSIONS).asBoolean()) return true;
        return invocation.source().hasPermission(Permission.COMMAND_CHANNELS.getPermissionNode());
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!(source instanceof Player player)) {
            String msg = config.get(ConfigKey.MINECRAFT_COMMAND_MUST_BE_PLAYER).asString();
            msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            source.sendMessage(Helper.stringToComponent(msg));
            return;
        }

        ChannelRegistry registry = config.getChannelRegistry();
        List<String> channelAliases = config.get(ConfigKey.CHANNELS_ALIASES).asList();
        String baseCmd = channelAliases.isEmpty() ? "apc-channels" : channelAliases.get(0);
        String prefix = config.get(ConfigKey.PLUGIN_PREFIX).asString();

        // /channels  — show menu
        if (args.length == 0) {
            if (registry == null || registry.isEmpty()) {
                String msg = CommonHelper.replaceKey(config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_NO_CHANNELS).asString(), "plugin-prefix", prefix);
                player.sendMessage(Helper.stringToComponent(msg));
                return;
            }
            String header = CommonHelper.replaceKey(config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_HEADER).asString(), "plugin-prefix", prefix);
            player.sendMessage(Helper.stringToComponent(header));

            // Global settings row (one line, not per-channel)
            player.sendMessage(buildGlobalRow(player.getUniqueId(), baseCmd));

            for (ChannelDefinition ch : registry.all()) {
                player.sendMessage(buildChannelRow(ch, prefsManager.getPrefs(player.getUniqueId(), ch), baseCmd));
            }
            return;
        }

        // /channels global <gifs|nickname>
        if (args[0].equalsIgnoreCase("global") && args.length >= 2) {
            String setting = args[1].toLowerCase(Locale.ROOT);
            switch (setting) {
                case "gifs" -> {
                    boolean nowOn = !prefsManager.shouldSuppressGifs(player.getUniqueId());
                    prefsManager.setSuppressGifs(player.getUniqueId(), nowOn);
                    String msg = CommonHelper.replaceKey(
                            config.get(nowOn ? ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_GIFS_ON
                                             : ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_GIFS_OFF).asString(),
                            "plugin-prefix", prefix);
                    player.sendMessage(Helper.stringToComponent(msg));
                }
                case "nickname" -> {
                    boolean nowOn = !prefsManager.shouldUseDiscordNickname(player.getUniqueId());
                    prefsManager.setUseDiscordNickname(player.getUniqueId(), nowOn);
                    String msg = CommonHelper.replaceKey(
                            config.get(nowOn ? ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_NICK_ON
                                             : ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_NICK_OFF).asString(),
                            "plugin-prefix", prefix);
                    player.sendMessage(Helper.stringToComponent(msg));
                }
                default -> sendUsage(player);
            }
            return;
        }

        // /channels <channel> [send|receive]
        if (args.length < 2) { sendUsage(player); return; }

        if (registry == null || registry.isEmpty()) {
            player.sendMessage(Helper.stringToComponent(
                    CommonHelper.replaceKey(config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_NO_CHANNELS).asString(), "plugin-prefix", prefix)));
            return;
        }

        Optional<ChannelDefinition> maybeCh = registry.byName(args[0]);
        if (maybeCh.isEmpty()) { sendUsage(player); return; }

        ChannelDefinition ch = maybeCh.get();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "send" -> {
                boolean nowOn = !prefsManager.getPrefs(player.getUniqueId(), ch).isSend();
                prefsManager.setSend(player.getUniqueId(), ch, nowOn);
                String msg = CommonHelper.replaceKey(
                        CommonHelper.replaceKey(
                                config.get(nowOn ? ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_SEND_ON
                                                 : ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_SEND_OFF).asString(),
                                "plugin-prefix", prefix),
                        "channel", ch.getName());
                player.sendMessage(Helper.stringToComponent(msg));
            }
            case "receive" -> {
                boolean nowOn = !prefsManager.getPrefs(player.getUniqueId(), ch).isReceive();
                prefsManager.setReceive(player.getUniqueId(), ch, nowOn);
                String msg = CommonHelper.replaceKey(
                        CommonHelper.replaceKey(
                                config.get(nowOn ? ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_RCV_ON
                                                 : ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_RCV_OFF).asString(),
                                "plugin-prefix", prefix),
                        "channel", ch.getName());
                player.sendMessage(Helper.stringToComponent(msg));
            }
            default -> sendUsage(player);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        ChannelRegistry registry = config.getChannelRegistry();
        if (args.length <= 1) {
            List<String> opts = new ArrayList<>();
            opts.add("global");
            if (registry != null) registry.all().stream().map(ChannelDefinition::getName).forEach(opts::add);
            return opts;
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("global")) return List.of("gifs", "nickname");
            return List.of("send", "receive");
        }
        return List.of();
    }

    /** Global row: one [GIFs] and one [Discord Name] button, not per-channel. */
    private Component buildGlobalRow(UUID playerId, String baseCmd) {
        Component label = Component.text("  \u25B6 Global: ", NamedTextColor.GRAY);

        boolean gifsOn = prefsManager.shouldSuppressGifs(playerId);
        Component gifsBtn = Component.text("[GIFs: " + (gifsOn ? "HIDE" : "SHOW") + "]",
                gifsOn ? NamedTextColor.YELLOW : NamedTextColor.GRAY, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/" + baseCmd + " global gifs"))
                .hoverEvent(HoverEvent.showText(Component.text(
                        gifsOn ? "GIF links hidden. Click to show." : "GIF links shown. Click to hide.",
                        NamedTextColor.GRAY)));

        boolean nickOn = prefsManager.shouldUseDiscordNickname(playerId);
        Component nickBtn = Component.text("  [Discord: " + (nickOn ? "NICK" : "USER") + "]",
                nickOn ? NamedTextColor.AQUA : NamedTextColor.WHITE, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/" + baseCmd + " global nickname"))
                .hoverEvent(HoverEvent.showText(Component.text(
                        nickOn ? "Showing Discord nickname. Click for username."
                               : "Showing Discord username. Click for nickname.",
                        NamedTextColor.GRAY)));

        return label.append(gifsBtn).append(nickBtn);
    }

    private Component buildChannelRow(ChannelDefinition ch, PlayerChannelPrefsManager.ChannelPrefs prefs, String baseCmd) {
        String hoverText = "!" + ch.getPrefix() + " <msg> \u2192 this Discord channel only\n"
                         + "! <msg> \u2192 MC network only (no Discord)\n"
                         + "!! <msg> \u2192 local server only (vanilla)";

        Component name = Component.text("  " + ch.getName() + " ", NamedTextColor.AQUA);
        Component pfx = Component.text("[!" + ch.getPrefix() + "] ", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text(hoverText, NamedTextColor.GRAY)));

        boolean sendOn = prefs.isSend();
        Component sendBtn = Component.text("[Send: " + (sendOn ? "ON" : "OFF") + "]",
                sendOn ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/" + baseCmd + " " + ch.getName() + " send"))
                .hoverEvent(HoverEvent.showText(Component.text(
                        sendOn ? "Click to stop sending to " + ch.getName()
                               : "Click to start sending to " + ch.getName(), NamedTextColor.GRAY)));

        boolean rcvOn = prefs.isReceive();
        Component rcvBtn = Component.text("  [Receive: " + (rcvOn ? "ON" : "OFF") + "]",
                rcvOn ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/" + baseCmd + " " + ch.getName() + " receive"))
                .hoverEvent(HoverEvent.showText(Component.text(
                        rcvOn ? "Click to stop receiving from " + ch.getName()
                              : "Click to start receiving from " + ch.getName(), NamedTextColor.GRAY)));

        return name.append(pfx).append(sendBtn).append(rcvBtn);
    }

    private void sendUsage(Player player) {
        String msg = CommonHelper.replaceKey(config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_USAGE).asString(),
                "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
        player.sendMessage(Helper.stringToComponent(msg));
    }
}

