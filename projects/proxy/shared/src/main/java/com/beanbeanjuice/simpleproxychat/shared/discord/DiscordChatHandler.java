package com.beanbeanjuice.simpleproxychat.shared.discord;

import com.beanbeanjuice.simpleproxychat.shared.config.Config;
import com.beanbeanjuice.simpleproxychat.shared.config.ConfigKey;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.function.Consumer;

public class DiscordChatHandler extends ListenerAdapter {

    private final Config config;
    private final String selfBotId;
    private final Consumer<MessageReceivedEvent> sendFromDiscord;

    public DiscordChatHandler(Config config,
                              String selfBotId,
                              Consumer<MessageReceivedEvent> sendFromDiscord) {
        this.config = config;
        this.selfBotId = selfBotId;
        this.sendFromDiscord = sendFromDiscord;
    }

    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.getChannel().getId().equalsIgnoreCase(config.get(ConfigKey.CHANNEL_ID).asString())) return;
        if (event.getAuthor().getId().equals(selfBotId)) return;
        if (!config.get(ConfigKey.DISCORD_CHAT_ENABLED).asBoolean()) return;

        sendFromDiscord.accept(event);
    }

}
