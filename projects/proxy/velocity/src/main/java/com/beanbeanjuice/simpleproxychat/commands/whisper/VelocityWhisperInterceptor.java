package com.beanbeanjuice.simpleproxychat.commands.whisper;

import com.beanbeanjuice.simpleproxychat.SimpleProxyChatVelocity;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigKey;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.CommandExecuteEvent.CommandResult;
import com.velocitypowered.api.proxy.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Intercepts whisper and reply commands at the proxy level so that sub-server plugins
 * (e.g. Essentials) never receive them. Velocity's CommandManager already routes
 * registered proxy commands before forwarding to the backend, but some server-side
 * plugins hook into the vanilla command dispatcher directly and still respond.
 * This listener explicitly ensures proxy handling wins.
 */
public class VelocityWhisperInterceptor {

    private final SimpleProxyChatVelocity plugin;

    public VelocityWhisperInterceptor(SimpleProxyChatVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) return;

        String raw = event.getCommand().toLowerCase(Locale.ROOT);
        String cmd = raw.split("\\s+")[0];

        Set<String> whisperCmds = new HashSet<>(plugin.getConfig().get(ConfigKey.WHISPER_ALIASES).asList());
        whisperCmds.add("apc-whisper");
        Set<String> replyCmds = new HashSet<>(plugin.getConfig().get(ConfigKey.REPLY_ALIASES).asList());
        replyCmds.add("apc-reply");

        if (whisperCmds.contains(cmd) || replyCmds.contains(cmd)) {
            event.setResult(CommandResult.allowed());
        }
    }
}

