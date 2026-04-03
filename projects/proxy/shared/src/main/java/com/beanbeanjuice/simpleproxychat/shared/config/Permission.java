package com.beanbeanjuice.simpleproxychat.shared.config;

import lombok.Getter;

public enum Permission {

    READ_CHAT_MESSAGE("advancedproxychat.read.chat"),
    READ_JOIN_MESSAGE("advancedproxychat.read.join"),
    READ_LEAVE_MESSAGE("advancedproxychat.read.leave"),
    READ_FAKE_MESSAGE("advancedproxychat.read.fake"),
    READ_SWITCH_MESSAGE("advancedproxychat.read.switch"),
    READ_UPDATE_NOTIFICATION("advancedproxychat.read.update"),
    COMMAND_TOGGLE_CHAT("advancedproxychat.toggle.chat"),
    COMMAND_TOGGLE_CHAT_ALL("advancedproxychat.toggle.chat.all"),
    COMMAND_RELOAD("advancedproxychat.reload"),
    COMMAND_BAN("advancedproxychat.ban"),
    COMMAND_UNBAN("advancedproxychat.unban"),
    COMMAND_WHISPER("advancedproxychat.whisper"),
    COMMAND_BROADCAST("advancedproxychat.broadcast"),
    COMMAND_CHANNELS("advancedproxychat.channels"),
    COMMAND_LISTEN("advancedproxychat.listen"),
    COMMAND_HISTORY("advancedproxychat.history");

    @Getter private final String permissionNode;

    Permission(String permissionNode) {
        this.permissionNode = permissionNode;
    }

}
