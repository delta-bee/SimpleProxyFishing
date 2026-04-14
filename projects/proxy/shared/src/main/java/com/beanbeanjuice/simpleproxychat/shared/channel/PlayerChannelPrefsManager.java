package com.beanbeanjuice.simpleproxychat.shared.channel;

import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelDefinition;
import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player channel preferences for the multi-channel Discord bridge.
 *
 * <p>Preferences are held in memory only; they reset on proxy restart.
 * Each player starts with send/receive enabled for every channel (mirroring
 * {@link ChannelDefinition#isDisableSend()} / {@link ChannelDefinition#isDisableReceive()}).
 *
 * <h3>Listen-once</h3>
 * A player may call {@code /listen <channel> <n>} to forcibly receive the next
 * {@code n} messages from a channel regardless of their default receive preference.
 * Once the counter reaches zero the player is notified automatically.
 *
 * <h3>History</h3>
 * Every message received from Discord is stored in a bounded ring buffer per
 * channel.  {@code /history <channel> <n>} replays the last {@code n} entries.
 */
public class PlayerChannelPrefsManager {

    /** Maximum messages retained per channel in the history buffer. */
    private static final int MAX_HISTORY = 100;

    // player UUID -> channel name (lower-case) -> prefs
    private final Map<UUID, Map<String, ChannelPrefs>> playerPrefs = new ConcurrentHashMap<>();

    // player UUID -> global (non-channel) display preferences
    private final Map<UUID, GlobalPrefs> globalPrefs = new ConcurrentHashMap<>();

    // channel name (lower-case) -> ring-buffer of the last MAX_HISTORY messages
    private final Map<String, Deque<String>> channelHistory = new ConcurrentHashMap<>();

    // player UUID -> channel name (lower-case) -> remaining listen-once messages
    private final Map<UUID, Map<String, Integer>> listenOnce = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Global preferences (not per-channel)
    // -------------------------------------------------------------------------

    public GlobalPrefs getGlobalPrefs(UUID playerId) {
        return globalPrefs.computeIfAbsent(playerId, k -> new GlobalPrefs());
    }

    public boolean shouldSuppressGifs(UUID playerId) {
        return getGlobalPrefs(playerId).isSuppressGifs();
    }

    public void setSuppressGifs(UUID playerId, boolean suppress) {
        getGlobalPrefs(playerId).setSuppressGifs(suppress);
    }

    public boolean shouldUseDiscordNickname(UUID playerId) {
        return getGlobalPrefs(playerId).isUseDiscordNickname();
    }

    public void setUseDiscordNickname(UUID playerId, boolean use) {
        getGlobalPrefs(playerId).setUseDiscordNickname(use);
    }

    // -------------------------------------------------------------------------
    // Preferences
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link ChannelPrefs} for the given player and channel,
     * creating a default entry (send=true, receive=true) if one does not yet exist.
     * The defaults respect {@link ChannelDefinition#isDisableSend()} and
     * {@link ChannelDefinition#isDisableReceive()} from the config.
     */
    public ChannelPrefs getPrefs(UUID playerId, ChannelDefinition channel) {
        return playerPrefs
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(channel.getName().toLowerCase(Locale.ROOT),
                        k -> new ChannelPrefs(!channel.isDisableSend(), !channel.isDisableReceive()));
    }

    public void setSend(UUID playerId, ChannelDefinition channel, boolean send) {
        getPrefs(playerId, channel).setSend(send);
    }

    public void setReceive(UUID playerId, ChannelDefinition channel, boolean receive) {
        getPrefs(playerId, channel).setReceive(receive);
    }

    /** Returns true if the player should receive this channel's messages right now. */
    public boolean shouldReceive(UUID playerId, ChannelDefinition channel) {
        // Check listen-once counter first
        int remaining = getRemainingListenOnce(playerId, channel);
        if (remaining > 0) return true;

        return getPrefs(playerId, channel).isReceive();
    }

    /** Returns true if the player should have their chat forwarded to this channel by default. */
    public boolean shouldSend(UUID playerId, ChannelDefinition channel) {
        return getPrefs(playerId, channel).isSend();
    }

    // -------------------------------------------------------------------------
    // Listen-once
    // -------------------------------------------------------------------------

    /**
     * Sets the number of upcoming messages from {@code channel} that the player
     * will receive regardless of their default receive preference.
     */
    public void setListenOnce(UUID playerId, ChannelDefinition channel, int count) {
        listenOnce
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(channel.getName().toLowerCase(Locale.ROOT), count);
    }

    /**
     * Returns the remaining listen-once count for the player on this channel,
     * or {@code 0} if there is none active.
     */
    public int getRemainingListenOnce(UUID playerId, ChannelDefinition channel) {
        Map<String, Integer> map = listenOnce.get(playerId);
        if (map == null) return 0;
        return map.getOrDefault(channel.getName().toLowerCase(Locale.ROOT), 0);
    }

    /**
     * Decrements the listen-once counter.  Returns the value <em>after</em> the decrement.
     * Automatically removes the entry when it reaches zero.
     */
    public int decrementListenOnce(UUID playerId, ChannelDefinition channel) {
        String key = channel.getName().toLowerCase(Locale.ROOT);
        Map<String, Integer> map = listenOnce.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        int newVal = map.getOrDefault(key, 0) - 1;
        if (newVal <= 0) {
            map.remove(key);
            return 0;
        }
        map.put(key, newVal);
        return newVal;
    }

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------

    /**
     * Records a message that was broadcast to Minecraft from the given Discord channel.
     * Keeps the last {@value #MAX_HISTORY} messages per channel.
     */
    public void recordHistory(ChannelDefinition channel, String formattedMessage) {
        Deque<String> buf = channelHistory.computeIfAbsent(
                channel.getName().toLowerCase(Locale.ROOT),
                k -> new ArrayDeque<>(MAX_HISTORY));
        if (buf.size() >= MAX_HISTORY) buf.pollFirst();
        buf.addLast(formattedMessage);
    }

    /**
     * Returns the last {@code count} history entries for the channel, oldest first.
     * Returns fewer entries if fewer are stored.
     */
    public List<String> getHistory(ChannelDefinition channel, int count) {
        Deque<String> buf = channelHistory.get(channel.getName().toLowerCase(Locale.ROOT));
        if (buf == null) return Collections.emptyList();

        List<String> all = new ArrayList<>(buf); // oldest -> newest
        int from = Math.max(0, all.size() - count);
        return all.subList(from, all.size());
    }

    // -------------------------------------------------------------------------
    // Clean-up
    // -------------------------------------------------------------------------

    /** Removes all stored preferences for a player (call on disconnect). */
    public void removePlayer(UUID playerId) {
        playerPrefs.remove(playerId);
        globalPrefs.remove(playerId);
        listenOnce.remove(playerId);
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    public static class ChannelPrefs {
        private boolean send;
        private boolean receive;

        public ChannelPrefs(boolean send, boolean receive) {
            this.send = send;
            this.receive = receive;
        }

        public boolean isSend() { return send; }
        public boolean isReceive() { return receive; }
        public void setSend(boolean send) { this.send = send; }
        public void setReceive(boolean receive) { this.receive = receive; }
    }

    /** Global per-player display preferences (not per-channel). */
    public static class GlobalPrefs {
        private boolean suppressGifs = true;        // default ON — GIF links are noisy
        private boolean useDiscordNickname = false;  // default OFF — show username

        public boolean isSuppressGifs() { return suppressGifs; }
        public void setSuppressGifs(boolean v) { this.suppressGifs = v; }
        public boolean isUseDiscordNickname() { return useDiscordNickname; }
        public void setUseDiscordNickname(boolean v) { this.useDiscordNickname = v; }
    }
}

