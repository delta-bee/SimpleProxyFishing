package com.beanbeanjuice.simpleproxychat.commands.channel;

import com.beanbeanjuice.simpleproxychat.SimpleProxyChatBungee;
import com.beanbeanjuice.simpleproxychat.common.CommonHelper;
import com.beanbeanjuice.simpleproxychat.shared.channel.PlayerChannelPrefsManager;
import com.beanbeanjuice.simpleproxychat.shared.config.Config;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigKey;
import com.beanbeanjuice.simpleproxychat.shared.config.Permission;
import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelDefinition;
import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelRegistry;
import com.beanbeanjuice.simpleproxychat.shared.helper.Helper;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class BungeeChannelsCommand extends Command {

    private final SimpleProxyChatBungee plugin;
    private final Config config;
    private final PlayerChannelPrefsManager prefsManager;

    public BungeeChannelsCommand(final SimpleProxyChatBungee plugin, final String... aliases) {
        super("channels", null, aliases);
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.prefsManager = plugin.getChannelPrefsManager();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (config.get(ConfigKey.USE_PERMISSIONS).asBoolean()
                && !sender.hasPermission(Permission.COMMAND_CHANNELS.getPermissionNode())
                && sender instanceof ProxiedPlayer) {
            sender.sendMessage(Helper.convertToBungee(config.get(ConfigKey.MINECRAFT_COMMAND_NO_PERMISSION).asString()));
            return;
        }

        if (!(sender instanceof ProxiedPlayer player)) {
            String msg = CommonHelper.replaceKey(config.get(ConfigKey.MINECRAFT_COMMAND_MUST_BE_PLAYER).asString(),
                    "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            sender.sendMessage(Helper.convertToBungee(msg));
            return;
        }

        ChannelRegistry registry = config.getChannelRegistry();
        List<String> channelAliases = config.get(ConfigKey.CHANNELS_ALIASES).asList();
        String baseCmd = channelAliases.isEmpty() ? "apc-channels" : channelAliases.get(0);
        String prefix = config.get(ConfigKey.PLUGIN_PREFIX).asString();

        // /channels — show menu
        if (args.length == 0) {
            if (registry == null || registry.isEmpty()) {
                player.sendMessage(Helper.convertToBungee(CommonHelper.replaceKey(
                        config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_NO_CHANNELS).asString(), "plugin-prefix", prefix)));
                return;
            }
            player.sendMessage(Helper.convertToBungee(CommonHelper.replaceKey(
                    config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_HEADER).asString(), "plugin-prefix", prefix)));

            // Global settings row
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
                    player.sendMessage(Helper.convertToBungee(msg));
                }
                case "nickname" -> {
                    boolean nowOn = !prefsManager.shouldUseDiscordNickname(player.getUniqueId());
                    prefsManager.setUseDiscordNickname(player.getUniqueId(), nowOn);
                    String msg = CommonHelper.replaceKey(
                            config.get(nowOn ? ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_NICK_ON
                                             : ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_NICK_OFF).asString(),
                            "plugin-prefix", prefix);
                    player.sendMessage(Helper.convertToBungee(msg));
                }
                default -> sendUsage(player);
            }
            return;
        }

        if (args.length < 2) { sendUsage(player); return; }

        if (registry == null || registry.isEmpty()) {
            player.sendMessage(Helper.convertToBungee(CommonHelper.replaceKey(
                    config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_NO_CHANNELS).asString(), "plugin-prefix", prefix)));
            return;
        }

        Optional<ChannelDefinition> maybeCh = registry.byName(args[0]);
        if (maybeCh.isEmpty()) { sendUsage(player); return; }

        ChannelDefinition ch = maybeCh.get();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "send" -> {
                boolean nowOn = !prefsManager.getPrefs(player.getUniqueId(), ch).isSend();
                prefsManager.setSend(player.getUniqueId(), ch, nowOn);
                String msg = CommonHelper.replaceKey(CommonHelper.replaceKey(
                        config.get(nowOn ? ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_SEND_ON
                                         : ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_SEND_OFF).asString(),
                        "plugin-prefix", prefix), "channel", ch.getName());
                player.sendMessage(Helper.convertToBungee(msg));
            }
            case "receive" -> {
                boolean nowOn = !prefsManager.getPrefs(player.getUniqueId(), ch).isReceive();
                prefsManager.setReceive(player.getUniqueId(), ch, nowOn);
                String msg = CommonHelper.replaceKey(CommonHelper.replaceKey(
                        config.get(nowOn ? ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_RCV_ON
                                         : ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_RCV_OFF).asString(),
                        "plugin-prefix", prefix), "channel", ch.getName());
                player.sendMessage(Helper.convertToBungee(msg));
            }
            default -> sendUsage(player);
        }
    }

    private net.md_5.bungee.api.chat.BaseComponent[] buildGlobalRow(UUID playerId, String baseCmd) {
        ComponentBuilder builder = new ComponentBuilder("  \u25B6 Global: ").color(ChatColor.GRAY);

        boolean gifsOn = prefsManager.shouldSuppressGifs(playerId);
        TextComponent gifsBtn = new TextComponent("[GIFs: " + (gifsOn ? "HIDE" : "SHOW") + "]");
        gifsBtn.setColor(gifsOn ? ChatColor.YELLOW : ChatColor.GRAY);
        gifsBtn.setBold(true);
        gifsBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + baseCmd + " global gifs"));
        gifsBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(gifsOn ? "GIF links hidden. Click to show." : "GIF links shown. Click to hide.")));
        builder.append(gifsBtn).append("  ").reset();

        boolean nickOn = prefsManager.shouldUseDiscordNickname(playerId);
        TextComponent nickBtn = new TextComponent("[Discord: " + (nickOn ? "NICK" : "USER") + "]");
        nickBtn.setColor(nickOn ? ChatColor.AQUA : ChatColor.WHITE);
        nickBtn.setBold(true);
        nickBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + baseCmd + " global nickname"));
        nickBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(nickOn ? "Showing Discord nickname. Click for username."
                               : "Showing Discord username. Click for nickname.")));
        builder.append(nickBtn);
        return builder.create();
    }

    private net.md_5.bungee.api.chat.BaseComponent[] buildChannelRow(ChannelDefinition ch,
                                                                      PlayerChannelPrefsManager.ChannelPrefs prefs,
                                                                      String baseCmd) {
        boolean sendOn = prefs.isSend();
        boolean rcvOn  = prefs.isReceive();

        ComponentBuilder builder = new ComponentBuilder("  " + ch.getName() + " ").color(ChatColor.AQUA);

        TextComponent pfx = new TextComponent("[!" + ch.getPrefix() + "] ");
        pfx.setColor(ChatColor.YELLOW);
        pfx.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("!" + ch.getPrefix() + " <msg> \u2192 this Discord channel only\n"
                       + "! <msg> \u2192 MC network only\n!! <msg> \u2192 local server only")));
        builder.append(pfx).reset();

        TextComponent sendBtn = new TextComponent("[Send: " + (sendOn ? "ON" : "OFF") + "]");
        sendBtn.setColor(sendOn ? ChatColor.GREEN : ChatColor.RED);
        sendBtn.setBold(true);
        sendBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + baseCmd + " " + ch.getName() + " send"));
        sendBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(sendOn ? "Click to stop sending to " + ch.getName()
                               : "Click to start sending to " + ch.getName())));
        builder.append(sendBtn).append("  ").reset();

        TextComponent rcvBtn = new TextComponent("[Receive: " + (rcvOn ? "ON" : "OFF") + "]");
        rcvBtn.setColor(rcvOn ? ChatColor.GREEN : ChatColor.RED);
        rcvBtn.setBold(true);
        rcvBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + baseCmd + " " + ch.getName() + " receive"));
        rcvBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(rcvOn ? "Click to stop receiving from " + ch.getName()
                               : "Click to start receiving from " + ch.getName())));
        builder.append(rcvBtn);
        return builder.create();
    }

    private void sendUsage(ProxiedPlayer player) {
        player.sendMessage(Helper.convertToBungee(CommonHelper.replaceKey(
                config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_USAGE).asString(),
                "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString())));
    }
}

