package com.example.orevision.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple JSON config saved to config/orevision.json.
 * Lets you enable/disable individual ores and set ESP colors without recompiling.
 * Edit the file, then use the in-game "reload config" keybind (default: L) to apply changes.
 */
public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("orevision.json");

    public int scanRadiusChunks = 6;      // how far around the player to scan for ores
    public int scanIntervalTicks = 20;    // rescan every N ticks (20 ticks = 1 second)
    public double espLineWidth = 2.0;

    // block id -> hex color (0xRRGGBB) for ESP outline
    public Map<String, String> oreColors = new LinkedHashMap<>();

    // block id -> enabled
    public Map<String, Boolean> oreEnabled = new LinkedHashMap<>();

    public static ModConfig loadOrCreateDefault() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ModConfig def = defaults();
        def.save();
        return def;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static ModConfig defaults() {
        ModConfig c = new ModConfig();

        put(c, "minecraft:diamond_ore", "#4DE8FF", true);
        put(c, "minecraft:deepslate_diamond_ore", "#4DE8FF", true);

        put(c, "minecraft:emerald_ore", "#25D95E", true);
        put(c, "minecraft:deepslate_emerald_ore", "#25D95E", true);

        put(c, "minecraft:gold_ore", "#FFD24D", true);
        put(c, "minecraft:deepslate_gold_ore", "#FFD24D", true);
        put(c, "minecraft:nether_gold_ore", "#FFD24D", true);

        put(c, "minecraft:iron_ore", "#D8B48C", true);
        put(c, "minecraft:deepslate_iron_ore", "#D8B48C", true);

        put(c, "minecraft:redstone_ore", "#FF3B3B", true);
        put(c, "minecraft:deepslate_redstone_ore", "#FF3B3B", true);

        put(c, "minecraft:lapis_ore", "#2657FF", true);
        put(c, "minecraft:deepslate_lapis_ore", "#2657FF", true);

        put(c, "minecraft:copper_ore", "#FF8A4D", true);
        put(c, "minecraft:deepslate_copper_ore", "#FF8A4D", true);

        put(c, "minecraft:coal_ore", "#8C8C8C", false);
        put(c, "minecraft:deepslate_coal_ore", "#8C8C8C", false);

        put(c, "minecraft:ancient_debris", "#B54A3C", true);
        put(c, "minecraft:nether_quartz_ore", "#E6E6E6", false);

        return c;
    }

    private static void put(ModConfig c, String id, String color, boolean enabled) {
        c.oreColors.put(id, color);
        c.oreEnabled.put(id, enabled);
    }
}
