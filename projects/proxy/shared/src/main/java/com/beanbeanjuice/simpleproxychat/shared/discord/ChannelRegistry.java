package com.beanbeanjuice.simpleproxychat.shared.discord;

import java.util.*;

/**
 * Holds all configured {@link ChannelDefinition}s and provides fast look-ups by
 * prefix character, channel name, and Discord channel ID.
 */
public class ChannelRegistry {

    private final Map<Character, ChannelDefinition> byPrefixMap = new LinkedHashMap<>();
    private final Map<String, ChannelDefinition> byNameMap = new LinkedHashMap<>();
    private final Map<String, ChannelDefinition> byChannelIdMap = new LinkedHashMap<>();

    public ChannelRegistry(List<ChannelDefinition> definitions) {
        for (ChannelDefinition def : definitions) {
            byPrefixMap.put(Character.toLowerCase(def.getPrefix()), def);
            byNameMap.put(def.getName().toLowerCase(Locale.ROOT), def);
            byChannelIdMap.put(def.getChannelId(), def);
        }
    }

    /** Look up a channel by its single-character routing flag (case-insensitive). */
    public Optional<ChannelDefinition> byPrefix(char prefix) {
        return Optional.ofNullable(byPrefixMap.get(Character.toLowerCase(prefix)));
    }

    /** Look up a channel by its human-readable name (case-insensitive). */
    public Optional<ChannelDefinition> byName(String name) {
        return Optional.ofNullable(byNameMap.get(name.toLowerCase(Locale.ROOT)));
    }

    /** Look up a channel by its Discord channel ID. */
    public Optional<ChannelDefinition> byChannelId(String channelId) {
        return Optional.ofNullable(byChannelIdMap.get(channelId));
    }

    /** Returns all registered channels in insertion order. */
    public Collection<ChannelDefinition> all() {
        return Collections.unmodifiableCollection(byNameMap.values());
    }

    /** Returns {@code true} if no channels are registered. */
    public boolean isEmpty() {
        return byNameMap.isEmpty();
    }

}

