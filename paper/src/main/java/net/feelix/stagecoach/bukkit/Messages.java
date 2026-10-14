package net.feelix.stagecoach.bukkit;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Messages {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final YamlConfiguration yaml;

    private Messages(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    public static Messages load(JavaPlugin plugin) {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the built-in messages.yml: " + e.getMessage());
        }
        return new Messages(y);
    }

    static Messages of(YamlConfiguration yaml) {
        return new Messages(yaml);
    }

    /** A Component argument keeps its own style. */
    public Component c(String key, Object... args) {
        return fill(LEGACY.deserialize(yaml.getString(key, key)), args);
    }

    /** Not italic: Minecraft draws item names and lore in italics otherwise. */
    public Component item(String key, Object... args) {
        return c(key, args).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** No colours, for the console and logs. */
    public String plain(String key, Object... args) {
        return PlainTextComponentSerializer.plainText().serialize(c(key, args));
    }

    /** A YAML list or a single string, not italic: item lore and help. */
    public List<Component> lines(String key, Object... args) {
        List<String> raw = yaml.isList(key) ? yaml.getStringList(key) : List.of(yaml.getString(key, key));
        List<Component> out = new ArrayList<>();
        for (String s : raw) {
            out.add(fill(LEGACY.deserialize(s), args).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return out;
    }

    /** Plain text to the console or RCON, which shows colour codes raw. */
    public void send(Audience to, String key, Object... args) {
        if (to instanceof org.bukkit.entity.Player) to.sendMessage(c(key, args));
        else to.sendMessage(Component.text(plain(key, args)));
    }

    public void sendLines(Audience to, String key) {
        for (Component line : lines(key)) {
            to.sendMessage(to instanceof org.bukkit.entity.Player ? line
                    : Component.text(PlainTextComponentSerializer.plainText().serialize(line)));
        }
    }

    public void actionBar(Audience to, String key, Object... args) {
        to.sendActionBar(c(key, args));
    }

    static Component fill(Component template, Object... args) {
        Component out = template;
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            Component value = a instanceof Component comp ? comp : Component.text(String.valueOf(a));
            out = out.replaceText(TextReplacementConfig.builder().matchLiteral("{" + i + "}").replacement(value).build());
        }
        return out;
    }
}
