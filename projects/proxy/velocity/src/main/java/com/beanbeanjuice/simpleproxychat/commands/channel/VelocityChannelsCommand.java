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

/**
 * /channels [channel] [send|receive]
 *
 * With no arguments: displays a clickable toggle menu in chat. Each channel row
 * shows two clickable buttons -- [Send: ON/OFF] and [Receive: ON/OFF] -- that run
 * /channels &lt;channel&gt; send (or receive) when clicked.
 *
 * With arguments: toggles the named preference for the named channel directly.
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
        if (registry == null || registry.isEmpty()) {
            String msg = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_NO_CHANNELS).asString();
            msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            player.sendMessage(Helper.stringToComponent(msg));
            return;
        }

        // /channels  -- show clickable toggle menu
        if (args.length == 0) {
            String header = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_HEADER).asString();
            header = CommonHelper.replaceKey(header, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            player.sendMessage(Helper.stringToComponent(header));

            // Resolve the primary command name (first alias, or fallback "apc-channels")
            List<String> channelAliases = config.get(ConfigKey.CHANNELS_ALIASES).asList();
            String baseCmd = channelAliases.isEmpty() ? "apc-channels" : channelAliases.get(0);

            for (ChannelDefinition ch : registry.all()) {
                PlayerChannelPrefsManager.ChannelPrefs prefs = prefsManager.getPrefs(player.getUniqueId(), ch);
                player.sendMessage(buildChannelRow(ch, prefs, baseCmd));
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

    /**
     * Builds a single channel row with two clickable toggle buttons:
     *   channel-name  [Send: ON]  [Receive: OFF]
     *
     * Clicking a button runs "/{baseCmd} {channelName} send" or
     * "/{baseCmd} {channelName} receive" in the player's command bar.
     */
    private Component buildChannelRow(ChannelDefinition ch,
                                      PlayerChannelPrefsManager.ChannelPrefs prefs,
                                      String baseCmd) {
        Component name = Component.text("  " + ch.getName() + " ", NamedTextColor.AQUA);

        // Prefix hint
        Component prefixHint = Component.text("[!" + ch.getPrefix() + "] ", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Type !" + ch.getPrefix() + " <message> to send to " + ch.getName() + " regardless of your send default.",
                        NamedTextColor.GRAY)));

        // Send button
        boolean sendOn = prefs.isSend();
        Component sendBtn = Component.text("[Send: " + (sendOn ? "ON" : "OFF") + "]",
                sendOn ? NamedTextColor.GREEN : NamedTextColor.RED,
                TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/" + baseCmd + " " + ch.getName() + " send"))
                .hoverEvent(HoverEvent.showText(Component.text(
                        sendOn ? "Click to disable sending to " + ch.getName()
                               : "Click to enable sending to " + ch.getName(),
                        NamedTextColor.GRAY)));

        Component space = Component.text("  ");

        // Receive button
        boolean rcvOn = prefs.isReceive();
        Component rcvBtn = Component.text("[Receive: " + (rcvOn ? "ON" : "OFF") + "]",
                rcvOn ? NamedTextColor.GREEN : NamedTextColor.RED,
                TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/" + baseCmd + " " + ch.getName() + " receive"))
                .hoverEvent(HoverEvent.showText(Component.text(
                        rcvOn ? "Click to stop receiving from " + ch.getName()
                              : "Click to start receiving from " + ch.getName(),
                        NamedTextColor.GRAY)));

        return name.append(prefixHint).append(sendBtn).append(space).append(rcvBtn);
    }

    private void sendUsage(Player player) {
        String msg = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_USAGE).asString();
        msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
        player.sendMessage(Helper.stringToComponent(msg));
    }
}

