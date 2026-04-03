package com.beanbeanjuice.simpleproxychat.shared;

import com.beanbeanjuice.simpleproxychat.shared.channel.PlayerChannelPrefsManager;
import com.beanbeanjuice.simpleproxychat.shared.config.Config;
import com.beanbeanjuice.simpleproxychat.shared.discord.Bot;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public interface ISimpleProxyChat {

    boolean isPluginStarting();

    boolean isLuckPermsEnabled();
    Optional<?> getLuckPerms();

    boolean isVanishAPIEnabled();

    boolean isLiteBansEnabled();
    Optional<?> getLiteBansDatabase();

    boolean isAdvancedBanEnabled();
    Optional<?> getAdvancedBanUUIDManager();
    Optional<?> getAdvancedBanPunishmentManager();

    boolean isNetworkManagerEnabled();
    Optional<?> getNetworkManager();

    Config getSPCConfig();
    Bot getDiscordBot();
    PlayerChannelPrefsManager getChannelPrefsManager();

    /** Send {@code message} to all online players. */
    void sendAll(String message);

    /**
     * Send {@code message} only to players for whom {@code filter} returns {@code true}.
     * The predicate receives each player's {@link UUID}.
     */
    void sendPerPlayer(String message, Predicate<UUID> filter);

    /** Send {@code message} to a single player identified by their {@link UUID}. */
    void sendToPlayer(UUID playerId, String message);

    void log(String message);

}
