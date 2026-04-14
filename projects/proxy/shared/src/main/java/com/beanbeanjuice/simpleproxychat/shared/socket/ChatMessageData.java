package com.beanbeanjuice.simpleproxychat.shared.socket;

import com.beanbeanjuice.simpleproxychat.shared.utility.listeners.MessageType;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import lombok.Getter;

import java.util.UUID;

public abstract class ChatMessageData extends MessageData {

    @Getter private final String servername;
    @Getter private final String playerName;
    @Getter private final UUID playerUUID;
    @Getter private final String message;
    /** Display name from /nick, falls back to playerName if not set. */
    @Getter private String displayName;

    // Chat
    public ChatMessageData(MessageType type, String servername, String playerName,
                           UUID playerUUID, String message) {
        super(type);
        this.servername = servername;
        this.playerName = playerName;
        this.playerUUID = playerUUID;
        this.message = message;
        this.displayName = playerName;
    }

    public ChatMessageData(MessageType type, String servername, String playerName,
                           UUID playerUUID, String message,
                           String minecraftMessage, String discordMessage,
                           String discordEmbedTitle, String discordEmbedMessage) {
        super(type);
        this.servername = servername;
        this.playerName = playerName;
        this.playerUUID = playerUUID;
        this.message = message;
        this.displayName = playerName;
        this.setMinecraftMessage(minecraftMessage);
        this.setDiscordMessage(discordMessage);
        this.setDiscordEmbedTitle(discordEmbedTitle);
        this.setDiscordEmbedMessage(discordEmbedMessage);
    }

    public void setDisplayName(String displayName) {
        this.displayName = (displayName != null && !displayName.isBlank()) ? displayName : playerName;
    }

    @Override
    public byte[] getAsBytes() {
        if (this.getMinecraftMessage().isEmpty() || this.getDiscordMessage().isEmpty() || this.getDiscordEmbedTitle().isEmpty() || this.getDiscordEmbedMessage().isEmpty())
            throw new NullPointerException("Minecraft or Discord message is null!");

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("AdvancedProxyChat");
        out.writeUTF(this.getType().name());
        out.writeUTF(this.servername);
        out.writeUTF(this.playerName);
        out.writeUTF(this.message);
        out.writeUTF(this.getMinecraftMessage().get());
        out.writeUTF(this.getDiscordMessage().get());
        out.writeUTF(this.getDiscordEmbedTitle().get());
        out.writeUTF(this.getDiscordEmbedMessage().get());
        return out.toByteArray();
    }

    public abstract void chatSendToAllOtherPlayers(String parsedMessage);

}
