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

import java.util.*;

/**
 * /channels [channel] [send|receive]
 *
 * With no arguments: lists all configured channels and the player's current
 * send/receive preference for each.
 * With arguments: toggles the named preference for the named channel.
 */
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
        if (registry == null || registry.isEmpty()) {
            String msg = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_NO_CHANNELS).asString();
            msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            player.sendMessage(Helper.stringToComponent(msg));
            return;
        }

        // /channels  – list all
        if (args.length == 0) {
            String header = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_HEADER).asString();
            header = CommonHelper.replaceKey(header, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            player.sendMessage(Helper.stringToComponent(header));

            for (ChannelDefinition ch : registry.all()) {
                PlayerChannelPrefsManager.ChannelPrefs prefs = prefsManager.getPrefs(player.getUniqueId(), ch);
                String sendIcon    = prefs.isSend()    ? "&a✅" : "&c❌";
                String receiveIcon = prefs.isReceive() ? "&a✅" : "&c❌";
                String entry = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_ENTRY).asString();
                entry = CommonHelper.replaceKey(entry, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
                entry = CommonHelper.replaceKey(entry, "channel", ch.getName());
                entry = CommonHelper.replaceKey(entry, "send", sendIcon);
                entry = CommonHelper.replaceKey(entry, "receive", receiveIcon);
                player.sendMessage(Helper.stringToComponent(entry));
            }
            return;
        }

        // /channels <channel> [send|receive]
        if (args.length < 2) {
            sendUsage(player);
            return;
        }

        Optional<ChannelDefinition> maybeCh = registry.byName(args[0]);
        if (maybeCh.isEmpty()) {
            sendUsage(player);
            return;
        }
        ChannelDefinition ch = maybeCh.get();
        String type = args[1].toLowerCase(Locale.ROOT);
        String prefix = config.get(ConfigKey.PLUGIN_PREFIX).asString();

        switch (type) {
            case "send" -> {
                boolean nowOn = !prefsManager.getPrefs(player.getUniqueId(), ch).isSend();
                prefsManager.setSend(player.getUniqueId(), ch, nowOn);
                ConfigKey msgKey = nowOn ? ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_SEND_ON
                                         : ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_SEND_OFF;
                String msg = config.get(msgKey).asString();
                msg = CommonHelper.replaceKey(msg, "plugin-prefix", prefix);
                msg = CommonHelper.replaceKey(msg, "channel", ch.getName());
                player.sendMessage(Helper.stringToComponent(msg));
            }
            case "receive" -> {
                boolean nowOn = !prefsManager.getPrefs(player.getUniqueId(), ch).isReceive();
                prefsManager.setReceive(player.getUniqueId(), ch, nowOn);
                ConfigKey msgKey = nowOn ? ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_RCV_ON
                                         : ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_RCV_OFF;
                String msg = config.get(msgKey).asString();
                msg = CommonHelper.replaceKey(msg, "plugin-prefix", prefix);
                msg = CommonHelper.replaceKey(msg, "channel", ch.getName());
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
            if (registry == null) return List.of();
            return registry.all().stream().map(ChannelDefinition::getName).toList();
        }
        if (args.length == 2) return List.of("send", "receive");
        return List.of();
    }

    private void sendUsage(Player player) {
        String msg = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_USAGE).asString();
        msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
        player.sendMessage(Helper.stringToComponent(msg));
    }
}

