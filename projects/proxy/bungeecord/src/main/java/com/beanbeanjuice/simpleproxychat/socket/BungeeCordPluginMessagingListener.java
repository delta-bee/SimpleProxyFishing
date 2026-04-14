package com.beanbeanjuice.simpleproxychat.socket;

import com.beanbeanjuice.simpleproxychat.SimpleProxyChatBungee;
import com.beanbeanjuice.simpleproxychat.common.CommonHelper;
import com.beanbeanjuice.simpleproxychat.listeners.BungeeServerListener;
import com.beanbeanjuice.simpleproxychat.shared.utility.listeners.MessageType;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class BungeeCordPluginMessagingListener implements Listener {

    private final SimpleProxyChatBungee plugin;
    private final BungeeServerListener listener;

    public BungeeCordPluginMessagingListener(SimpleProxyChatBungee plugin, BungeeServerListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals("BungeeCord")) return;

        ByteArrayDataInput input = ByteStreams.newDataInput(event.getData());

        if (!input.readUTF().equals("AdvancedProxyChat")) return;

        MessageType type = MessageType.valueOf(input.readUTF());

        switch (type) {
            case CHAT -> runChat(input);
        }
    }

    private void runChat(ByteArrayDataInput input) {
        String serverName = input.readUTF();
        String playerName = input.readUTF();
        String playerMessage = input.readUTF();
        String parsedMinecraftString = input.readUTF();
        String parsedDiscordString = input.readUTF();
        String parsedDiscordEmbedTitle = input.readUTF();
        String parsedDiscordEmbedMessage = input.readUTF();
        // 1.0.6: display name from /nick; gracefully absent if old helper installed
        String displayName = tryReadUTF(input, playerName);

        ProxiedPlayer player = plugin.getProxy().getPlayer(playerName);
        ServerInfo serverInfo = plugin.getProxy().getServerInfo(serverName);

        BungeeChatMessageData messageData = new BungeeChatMessageData(
                plugin, MessageType.CHAT, serverInfo, player, playerMessage,
                parsedMinecraftString, parsedDiscordString, parsedDiscordEmbedTitle, parsedDiscordEmbedMessage
        );
        messageData.setDisplayName(displayName);

        this.listener.getChatHandler().chat(
                messageData,
                CommonHelper.translateLegacyCodes(parsedMinecraftString),
                CommonHelper.translateLegacyCodes(parsedDiscordString),
                CommonHelper.translateLegacyCodes(parsedDiscordEmbedTitle),
                CommonHelper.translateLegacyCodes(parsedDiscordEmbedMessage)
        );
    }

    private static String tryReadUTF(ByteArrayDataInput in, String fallback) {
        try { return in.readUTF(); } catch (Exception e) { return fallback; }
    }

}

