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

import java.util.List;
import java.util.Optional;

/**
 * /history <channel> <amount>
 *
 * Replays the last {@code amount} messages received from the given Discord channel.
 */
public class VelocityHistoryCommand implements SimpleCommand {

    private final SimpleProxyChatVelocity plugin;
    private final Config config;
    private final PlayerChannelPrefsManager prefsManager;

    public VelocityHistoryCommand(final SimpleProxyChatVelocity plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.prefsManager = plugin.getChannelPrefsManager();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        if (!config.get(ConfigKey.USE_PERMISSIONS).asBoolean()) return true;
        return invocation.source().hasPermission(Permission.COMMAND_HISTORY.getPermissionNode());
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

        if (args.length < 2) {
            sendUsage(player);
            return;
        }

        ChannelRegistry registry = config.getChannelRegistry();
        Optional<ChannelDefinition> maybeCh = (registry != null) ? registry.byName(args[0]) : Optional.empty();

        if (maybeCh.isEmpty()) {
            String msg = config.get(ConfigKey.MINECRAFT_COMMAND_HISTORY_NO_SUCH_CHANNEL).asString();
            msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            msg = CommonHelper.replaceKey(msg, "channel", args[0]);
            player.sendMessage(Helper.stringToComponent(msg));
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
        List<String> history = prefsManager.getHistory(ch, amount);

        String header = config.get(ConfigKey.MINECRAFT_COMMAND_HISTORY_HEADER).asString();
        header = CommonHelper.replaceKey(header, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
        header = CommonHelper.replaceKey(header, "channel", ch.getName());
        header = CommonHelper.replaceKey(header, "amount", String.valueOf(amount));
        player.sendMessage(Helper.stringToComponent(header));

        if (history.isEmpty()) {
            String empty = config.get(ConfigKey.MINECRAFT_COMMAND_HISTORY_EMPTY).asString();
            empty = CommonHelper.replaceKey(empty, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            empty = CommonHelper.replaceKey(empty, "channel", ch.getName());
            player.sendMessage(Helper.stringToComponent(empty));
            return;
        }

        history.forEach(line -> player.sendMessage(Helper.stringToComponent(line)));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        ChannelRegistry registry = config.getChannelRegistry();
        if (args.length <= 1) {
            if (registry == null) return List.of();
            return registry.all().stream().map(ChannelDefinition::getName).toList();
        }
        if (args.length == 2) return List.of("5", "10", "20");
        return List.of();
    }

    private void sendUsage(Player player) {
        String msg = config.get(ConfigKey.MINECRAFT_COMMAND_HISTORY_USAGE).asString();
        msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
        player.sendMessage(Helper.stringToComponent(msg));
    }
}

