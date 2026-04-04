package com.beanbeanjuice.simpleproxychat.shared.discord;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a single configured Discord channel with its routing metadata.
 */
@Getter
@RequiredArgsConstructor
public class ChannelDefinition {

    /** Human-readable name, e.g. "discord" or "staff". Used as the channel key everywhere. */
    private final String name;

    /** Single character used as a routing flag when a player types {@code !<flags> message}. */
    private final char prefix;

    /** Discord channel snowflake ID. */
    private final String channelId;

    /** When {@code true}, no messages from Minecraft are relayed to this Discord channel. */
    private final boolean disableSend;

    /** When {@code true}, no messages from this Discord channel are relayed to Minecraft. */
    private final boolean disableReceive;

    /**
     * Optional per-channel override for the MC->Discord format string.
     * Falls back to the global {@code ConfigKey.MC_TO_DISCORD_FORMAT_DEFAULT} when {@code null}.
     */
    private final String mcToDiscordFormat;

    /**
     * Optional per-channel override for the Discord->MC format string.
     * Falls back to the global {@code ConfigKey.DISCORD_TO_MC_FORMAT_DEFAULT} when {@code null}.
     */
    private final String discordToMcFormat;

}

