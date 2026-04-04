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

/**
 * /channels [channel] [send|receive]
 *
 * With no arguments: displays a clickable toggle menu in chat. Each channel row
 * shows two clickable buttons — [Send: ON/OFF] and [Receive: ON/OFF] — that run
 * /channels &lt;channel&gt; send (or receive) when clicked.
 *
 * With arguments: toggles the named preference for the named channel directly.
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

        // /channels  – show clickable toggle menu
        if (args.length == 0) {
            String header = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_HEADER).asString();
            header = CommonHelper.replaceKey(header, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
            player.sendMessage(Helper.convertToBungee(header));

            // Resolve the primary command name (first alias, or fallback "apc-channels")
            List<String> channelAliases = config.get(ConfigKey.CHANNELS_ALIASES).asList();
            String baseCmd = channelAliases.isEmpty() ? "apc-channels" : channelAliases.get(0);

            for (ChannelDefinition ch : registry.all()) {
                PlayerChannelPrefsManager.ChannelPrefs prefs = prefsManager.getPrefs(player.getUniqueId(), ch);
                player.sendMessage(buildChannelRow(ch, prefs, baseCmd));
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

    /**
     * Builds a single channel row with two clickable toggle buttons:
     *   &lt;channel name&gt;  [Send: ON]  [Receive: OFF]
     */
    private net.md_5.bungee.api.chat.BaseComponent[] buildChannelRow(ChannelDefinition ch,
                                                                      PlayerChannelPrefsManager.ChannelPrefs prefs,
                                                                      String baseCmd) {
        boolean sendOn = prefs.isSend();
        boolean rcvOn  = prefs.isReceive();

        ComponentBuilder builder = new ComponentBuilder("  " + ch.getName() + " ").color(ChatColor.AQUA);

        // Send button
        TextComponent sendBtn = new TextComponent("[Send: " + (sendOn ? "ON" : "OFF") + "]");
        sendBtn.setColor(sendOn ? ChatColor.GREEN : ChatColor.RED);
        sendBtn.setBold(true);
        sendBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/" + baseCmd + " " + ch.getName() + " send"));
        sendBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(sendOn ? "Click to disable sending to " + ch.getName()
                               : "Click to enable sending to " + ch.getName())));

        builder.append(sendBtn);
        builder.append("  ").reset();

        // Receive button
        TextComponent rcvBtn = new TextComponent("[Receive: " + (rcvOn ? "ON" : "OFF") + "]");
        rcvBtn.setColor(rcvOn ? ChatColor.GREEN : ChatColor.RED);
        rcvBtn.setBold(true);
        rcvBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/" + baseCmd + " " + ch.getName() + " receive"));
        rcvBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(rcvOn ? "Click to stop receiving from " + ch.getName()
                               : "Click to start receiving from " + ch.getName())));

        builder.append(rcvBtn);
        return builder.create();
    }

    private void sendUsage(ProxiedPlayer player) {
        String msg = config.get(ConfigKey.MINECRAFT_COMMAND_CHANNELS_USAGE).asString();
        msg = CommonHelper.replaceKey(msg, "plugin-prefix", config.get(ConfigKey.PLUGIN_PREFIX).asString());
        player.sendMessage(Helper.convertToBungee(msg));
    }
}

