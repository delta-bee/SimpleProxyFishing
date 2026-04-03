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
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.Locale;
import java.util.Optional;

/**
 * /channels [channel] [send|receive]
 *
 * With no arguments: lists all configured channels and the player's current
 * send/receive preference for each.
 * With arguments: toggles the named preference for the named channel.
 */
public class BungeeChannelsCommand extends Command {

    private final SimpleProxyChatBungee plugin;
    private final Config config;
    private final PlayerChannelPrefsManager prefsManager;

    public BungeeChannelsCommand(final SimpleProxyChatBungee plugin, final String... aliases) {
        super("apc-channels", Permission.COMMAND_CHANNELS.getPermissionNode(), aliases);
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.prefsManager = plugin.getChannelPrefsManager();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer player)) {
            String msg = config.get(ConfigKey.MINECRAFT_COMMAND_MUST_BE_PLAYER).asString();
            msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            sender.sendMessage(Helper.convertToBungee(msg));
            return;
        }

        ChannelRegistry registry = config.getChannelRegistry();
        if (registry == null || registry.isEmpty()) {
            String msg = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_NO_CHANNELS).asString();
            msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            player.sendMessage(Helper.convertToBungee(msg));
            return;
        }

        if (args.length == 0) {
            String header = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_HEADER).asString();
            header = CommonHelper.replaceKey(header, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            player.sendMessage(Helper.convertToBungee(header));

            for (ChannelDefinition ch : registry.all()) {
                PlayerChannelPrefsManager.ChannelPrefs prefs = prefsManager.getPrefs(player.getUniqueId(), ch);
                String sendIcon    = prefs.isSend()    ? "&a✅" : "&c❌";
                String receiveIcon = prefs.isReceive() ? "&a✅" : "&c❌";
                String entry = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_ENTRY).asString();
                entry = CommonHelper.replaceKey(entry, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
                entry = CommonHelper.replaceKey(entry, "channel", ch.getName());
                entry = CommonHelper.replaceKey(entry, "send", sendIcon);
                entry = CommonHelper.replaceKey(entry, "receive", receiveIcon);
                player.sendMessage(Helper.convertToBungee(entry));
            }
            return;
        }

        if (args.length < 2) {
            sendUsage(player);
            return;
        }

        Optional<ChannelDefinition> maybeCh = registry.byName(args[0]);
        if (maybeCh.isEmpty()) { sendUsage(player); return; }

        ChannelDefinition ch = maybeCh.get();
        String type = args[1].toLowerCase(Locale.ROOT);
        String prefix = config.get(ConfigKey.PLUGIN_PREFIX).asString();

        switch (type) {
            case "send" -> {
                boolean nowOn = !prefsManager.getPrefs(player.getUniqueId(), ch).isSend();
                prefsManager.setSend(player.getUniqueId(), ch, nowOn);
                ConfigKey msgKey = nowOn ? ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_SEND_ON
                                         : ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_SEND_OFF;
                String msg = CommonHelper.replaceKey(config.get(msgKey).asString(), "plugin-prefix", prefix);
                msg = CommonHelper.replaceKey(msg, "channel", ch.getName());
                player.sendMessage(Helper.convertToBungee(msg));
            }
            case "receive" -> {
                boolean nowOn = !prefsManager.getPrefs(player.getUniqueId(), ch).isReceive();
                prefsManager.setReceive(player.getUniqueId(), ch, nowOn);
                ConfigKey msgKey = nowOn ? ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_RCV_ON
                                         : ConfigKey.MINECRAFT_COMMAND_CHANNELS_TOGGLED_RCV_OFF;
                String msg = CommonHelper.replaceKey(config.get(msgKey).asString(), "plugin-prefix", prefix);
                msg = CommonHelper.replaceKey(msg, "channel", ch.getName());
                player.sendMessage(Helper.convertToBungee(msg));
            }
            default -> sendUsage(player);
        }
    }

    private void sendUsage(ProxiedPlayer player) {
        String msg = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_USAGE).asString();
        msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
        player.sendMessage(Helper.convertToBungee(msg));
    }
}

