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

import java.util.Optional;

/**
 * /listen <channel> <amount>
 *
 * Temporarily subscribe a player to a channel for the next {@code amount} messages,
 * regardless of their default receive preference.
 */
public class BungeeListenCommand extends Command {

    private final SimpleProxyChatBungee plugin;
    private final Config config;
    private final PlayerChannelPrefsManager prefsManager;

    public BungeeListenCommand(final SimpleProxyChatBungee plugin, final String... aliases) {
        super("apc-listen", Permission.COMMAND_LISTEN.getPermissionNode(), aliases);
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

        if (args.length < 2) { sendUsage(player); return; }

        ChannelRegistry registry = config.getChannelRegistry();
        Optional<ChannelDefinition> maybeCh = (registry != null) ? registry.byName(args[0]) : Optional.empty();

        if (maybeCh.isEmpty()) {
            String msg = config.get(ConfigKey.MINECRAFT_COMMAND_LISTEN_NO_SUCH_CHANNEL).asString();
            msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            msg = CommonHelper.replaceKey(msg, "channel", args[0]);
            player.sendMessage(Helper.convertToBungee(msg));
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sendUsage(player);
            return;
        }

        ChannelDefinition ch = maybeCh.get();
        prefsManager.setListenOnce(player.getUniqueId(), ch, amount);

        String msg = config.get(ConfigKey.MINECRAFT_COMMAND_LISTEN_STARTED).asString();
        msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
        msg = CommonHelper.replaceKey(msg, "channel", ch.getName());
        msg = CommonHelper.replaceKey(msg, "amount", String.valueOf(amount));
        player.sendMessage(Helper.convertToBungee(msg));
    }

    private void sendUsage(ProxiedPlayer player) {
        String msg = config.get(ConfigKey.MINECRAFT_COMMAND_LISTEN_USAGE).asString();
        msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
        player.sendMessage(Helper.convertToBungee(msg));
    }
}

