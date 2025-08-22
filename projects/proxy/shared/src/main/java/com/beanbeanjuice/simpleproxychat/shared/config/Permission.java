package com.beanbeanjuice.simpleproxychat.shared.config;

import lombok.Getter;

public enum Permission {

    // Minecraft Chat (Read + Write)
    MESSAGES_READ_MINECRAFT_JOIN("simpleproxychat.read.minecraft.join"),
    MESSAGES_READ_MINECRAFT_LEAVE("simpleproxychat.read.minecraft.leave"),
    MESSAGES_READ_MINECRAFT_CHAT("simpleproxychat.read.minecraft.chat"),
    MESSAGES_READ_DISCORD_CHAT("simpleproxychat.read.discord.chat"),
    MESSAGES_READ_MINECRAFT_SWITCH("simpleproxychat.read.minecraft.switch"),

    // Minecraft Admin Messages
    READ_FAKE_MESSAGE("simpleproxychat.read.admin.fake"),
    READ_UPDATE_NOTIFICATION("simpleproxychat.read.admin.update"),

    // Minecraft Commands
    COMMAND_WHISPER("simpleproxychat.command.whisper"),
    COMMAND_TOGGLE_CHAT("simpleproxychat.command.toggle.chat"),
    COMMAND_TOGGLE_CHAT_ALL("simpleproxychat.command.toggle.chat.all"),
    COMMAND_RELOAD("simpleproxychat.command.reload"),
    COMMAND_BAN("simpleproxychat.command.ban"),
    COMMAND_UNBAN("simpleproxychat.command.unban"),
    COMMAND_BROADCAST("simpleproxychat.command.broadcast");

    @Getter private final String node;

    Permission(String node) {
        this.node = node;
    }

}
