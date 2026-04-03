package com.beanbeanjuice.simpleproxychat.shared.config;

import com.beanbeanjuice.simpleproxychat.common.CommonHelper;
import com.beanbeanjuice.simpleproxychat.common.Tuple;
import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelDefinition;
import com.beanbeanjuice.simpleproxychat.shared.discord.ChannelRegistry;
import com.beanbeanjuice.simpleproxychat.shared.helper.ServerChatLockHelper;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import lombok.Getter;
import org.joda.time.DateTimeZone;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;

public class Config {

    private YamlDocument yamlConfig;
    private YamlDocument yamlMessages;
    private final File configFolder;
    private final HashMap<ConfigKey, ConfigValueWrapper> config;
    private final ArrayList<Runnable> reloadFunctions;

    @Getter private final ServerChatLockHelper serverChatLockHelper;
    @Getter private ChannelRegistry channelRegistry;

    public Config(File configFolder) {
        this.configFolder = configFolder;
        config = new HashMap<>();
        reloadFunctions = new ArrayList<>();
        serverChatLockHelper = new ServerChatLockHelper();
    }

    public void initialize() {
        migrateFromSimpleProxyChat();
        try {
            yamlConfig   = loadConfig("config.yml");
            yamlMessages = loadConfig("messages.yml");
            yamlConfig.update();
            yamlMessages.update();
            renameSPCPrefix();
            yamlConfig.save();
            yamlMessages.save();
            readConfig();
        } catch (IOException ignored) { }
    }

    /**
     * If the user's {@code messages.yml} still contains the old SimpleProxyChat plugin-prefix,
     * silently update it to the AdvancedProxyChat branding so that command feedback no longer
     * displays the old plugin name.  Called after {@link YamlDocument#update()} but before
     * {@link YamlDocument#save()}, so the corrected value is persisted immediately.
     */
    private void renameSPCPrefix() {
        String current = yamlMessages.getString("plugin-prefix", "");
        if (current.contains("SimpleProxyChat")) {
            yamlMessages.set("plugin-prefix", "&8[<bold><rainbow>AdvancedProxyChat&r&8]");
        }
    }

    /**
     * If a {@code SimpleProxyChat/} folder exists next to the current data directory and the
     * current data directory does not yet contain a {@code config.yml}, the old files are copied
     * over so that existing installations are migrated automatically.
     *
     * <p>The original {@code SimpleProxyChat/} folder is left intact so that a roll-back is
     * always possible without data loss.
     */
    private void migrateFromSimpleProxyChat() {
        File newConfig = new File(configFolder, "config.yml");
        if (newConfig.exists()) return;

        File[] candidateFolders = {
            new File(configFolder.getParentFile(), "SimpleProxyChat"),
            new File(configFolder.getParentFile(), "simpleproxychat")
        };

        File sourceFolder = null;
        for (File candidate : candidateFolders) {
            if (candidate.isDirectory() && new File(candidate, "config.yml").exists()) {
                sourceFolder = candidate;
                break;
            }
        }

        if (sourceFolder == null) return;

        System.out.printf(
            "[AdvancedProxyChat] Detected a SimpleProxyChat installation at %s. " +
            "Migrating config files to %s - the originals are left untouched.%n",
            sourceFolder.getAbsolutePath(),
            configFolder.getAbsolutePath()
        );

        configFolder.mkdirs();

        for (String fileName : new String[]{"config.yml", "messages.yml"}) {
            File src  = new File(sourceFolder, fileName);
            File dest = new File(configFolder, fileName);
            if (!src.exists() || dest.exists()) continue;
            try {
                Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.printf("[AdvancedProxyChat] Migrated %s%n", fileName);
            } catch (IOException e) {
                System.err.printf("[AdvancedProxyChat] Failed to migrate %s: %s%n", fileName, e.getMessage());
            }
        }
    }

    public void addReloadListener(Runnable runnable) {
        reloadFunctions.add(runnable);
    }

    public void reload() {
        try {
            yamlConfig.reload();
            yamlMessages.reload();
            renameSPCPrefix();
            readConfig();
            reloadFunctions.forEach(Runnable::run);
        } catch (IOException ignored) { }
    }

    public ConfigValueWrapper get(ConfigKey key) {
        return config.get(key);
    }

    private void readConfig() throws IOException {
        Arrays.stream(ConfigKey.values()).forEach((key) -> {
            YamlDocument document = (key.getFile() == ConfigFileType.CONFIG) ? yamlConfig : yamlMessages;
            String route = key.getKey();

            if (key.getClassType() == String.class) {
                String raw = document.getString(route);
                String message = CommonHelper.translateLegacyCodes(raw != null ? raw : "");
                this.config.put(key, new ConfigValueWrapper(message));
                return;
            }

            if (key.getClassType() == Integer.class) {
                this.config.put(key, new ConfigValueWrapper(document.getInt(route)));
                return;
            }

            if (key.getClassType() == Boolean.class) {
                this.config.put(key, new ConfigValueWrapper(document.getBoolean(route)));
                return;
            }

            if (key.getClassType() == Map.class) {
                Map<String, String> map = new HashMap<>();
                Section mapSection = document.getSection(route);
                if (mapSection != null) {
                    mapSection.getKeys().stream()
                            .map((mapKey) -> (String) mapKey)
                            .map((mapKey) -> Tuple.of(mapKey, mapSection.getString(mapKey)))
                            .forEach((pair) -> map.put(pair.getKey(), CommonHelper.translateLegacyCodes(pair.getValue())));
                }
                this.config.put(key, new ConfigValueWrapper(map));
                return;
            }

            if (key.getClassType() == List.class) {
                List<String> list = document.getStringList(route);
                this.config.put(key, new ConfigValueWrapper(list.stream().map(CommonHelper::translateLegacyCodes).toList()));
                return;
            }

            if (key.getClassType() == Color.class) {
                String colorString = document.getString(route);
                Color color;
                if (colorString == null) {
                    color = Color.black;
                } else {
                    try {
                        color = Color.decode(colorString);
                    } catch (NumberFormatException e) {
                        System.err.printf("%s is not a valid color. Defaulting to black.\n", colorString);
                        color = Color.black;
                    }
                }
                this.config.put(key, new ConfigValueWrapper(color));
                return;
            }

            if (key.getClassType() == DateTimeZone.class) {
                String timezoneString = document.getString(route);
                DateTimeZone timezone;
                if (timezoneString == null) {
                    timezone = DateTimeZone.forID("America/Los_Angeles");
                } else {
                    try {
                        timezone = DateTimeZone.forID(timezoneString);
                    } catch (IllegalArgumentException e) {
                        System.err.printf("%s is not a valid timezone. Using default timezone. %s\n",
                                timezoneString, "https://www.joda.org/joda-time/timezones.html");
                        timezone = DateTimeZone.forID("America/Los_Angeles");
                    }
                }
                this.config.put(key, new ConfigValueWrapper(timezone));
                return;
            }
        });

        // Rebuild the channel registry on every config load/reload.
        this.channelRegistry = buildChannelRegistry();
    }

    private ChannelRegistry buildChannelRegistry() {
        List<ChannelDefinition> definitions = new ArrayList<>();

        List<?> rawList = yamlConfig.getList("channels");
        if (rawList == null) return new ChannelRegistry(definitions);

        for (Object rawEntry : rawList) {
            if (!(rawEntry instanceof Map<?, ?> rawMap)) continue;
            @SuppressWarnings("unchecked")
            Map<Object, Object> entry = (Map<Object, Object>) rawMap;

            String name = String.valueOf(entry.getOrDefault("name", ""));
            if (name.isEmpty()) continue;

            String prefixStr = String.valueOf(entry.getOrDefault("prefix", ""));
            char prefix = prefixStr.isEmpty() ? name.charAt(0) : prefixStr.charAt(0);

            String channelId = String.valueOf(entry.getOrDefault("channel-id", ""));
            boolean disableSend    = Boolean.parseBoolean(String.valueOf(entry.getOrDefault("disable-send",    "false")));
            boolean disableReceive = Boolean.parseBoolean(String.valueOf(entry.getOrDefault("disable-receive", "false")));

            Object mcFmtRaw      = entry.get("mc-to-discord");
            Object discordFmtRaw = entry.get("discord-to-mc");
            String mcToDiscordFormat = (mcFmtRaw      != null) ? CommonHelper.translateLegacyCodes(String.valueOf(mcFmtRaw))      : null;
            String discordToMcFormat = (discordFmtRaw != null) ? CommonHelper.translateLegacyCodes(String.valueOf(discordFmtRaw)) : null;

            definitions.add(new ChannelDefinition(name, prefix, channelId, disableSend, disableReceive, mcToDiscordFormat, discordToMcFormat));
        }

        return new ChannelRegistry(definitions);
    }

    public void overwrite(ConfigKey key, Object value) {
        config.put(key, new ConfigValueWrapper(value));
    }

    private YamlDocument loadConfig(String fileName) throws IOException {
        return YamlDocument.create(
                new File(configFolder, fileName),
                Objects.requireNonNull(getClass().getResourceAsStream("/" + fileName)),
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(true).build(),
                DumperSettings.DEFAULT,
                UpdaterSettings.builder()
                        .setVersioning(new BasicVersioning("file-version"))
                        .setOptionSorting(UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS)

                        .addRelocation("7", "minecraft.join.use", "minecraft.join.enabled", '.')
                        .addRelocation("7", "minecraft.leave.use", "minecraft.leave.enabled", '.')
                        .addRelocation("7", "minecraft.message", "minecraft.chat.message", '.')
                        .addRelocation("7", "minecraft.switch.use", "minecraft.switch.enabled", '.')
                        .addRelocation("7", "discord.join.use", "discord.join.enabled", '.')
                        .addRelocation("7", "discord.leave.use", "discord.leave.enabled", '.')
                        .addRelocation("7", "discord.switch.use", "discord.switch.enabled", '.')
                        .addRelocation("7", "discord.minecraft-message", "discord.chat.minecraft-message", '.')

                        .addRelocation("7", "discord.proxy-status.enabled", "discord.proxy-status.messages.enabled", '.')
                        .addRelocation("7", "discord.proxy-status.disabled", "discord.proxy-status.messages.disabled", '.')
                        .addRelocation("7", "discord.proxy-status.title", "discord.proxy-status.messages.title", '.')
                        .addRelocation("7", "discord.proxy-status.message", "discord.proxy-status.messages.message", '.')
                        .addRelocation("7", "discord.proxy-status.online", "discord.proxy-status.messages.online", '.')
                        .addRelocation("7", "discord.proxy-status.offline", "discord.proxy-status.messages.offline", '.')
                        .addRelocation("7", "discord.proxy-status.use-timestamp", "discord.proxy-status.messages.use-timestamp", '.')

                        .build()
        );
    }

}

