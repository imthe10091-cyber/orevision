package com.example.orevision;

import com.example.orevision.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Periodically scans loaded chunks around the player for configured ore blocks
 * and caches their positions + colors for the ESP renderer to draw.
 *
 * VERIFY: written against the documented Mojang-mapping class names for 26.2
 * (LevelChunk / LevelChunkSection / ClientLevel / BlockPos / BuiltInRegistries).
 * These match Fabric's official porting docs as of writing, but double check
 * against your IDE's autocomplete if something doesn't resolve.
 */
public class OreScanner {

    private static volatile Map<BlockPos, Integer> foundOres = new HashMap<>();
    private static int tickCounter = 0;

    public static Map<BlockPos, Integer> getFoundOres() {
        return foundOres;
    }

    public static void onClientTick(Minecraft client) {
        if (!OreVisionClient.STATE.espEnabled) {
            return;
        }

        tickCounter++;
        ModConfig config = OreVisionClient.CONFIG;
        if (tickCounter < config.scanIntervalTicks) {
            return;
        }
        tickCounter = 0;

        if (client.player == null || client.level == null) {
            return;
        }

        scan(client);
    }

    private static void scan(Minecraft client) {
        ClientLevel level = client.level;
        ModConfig config = OreVisionClient.CONFIG;

        Map<Block, Integer> targets = new HashMap<>();
        for (Map.Entry<String, Boolean> entry : config.oreEnabled.entrySet()) {
            if (!entry.getValue()) continue;
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id == null) continue;
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block == null) continue;
            String colorHex = config.oreColors.getOrDefault(entry.getKey(), "#FFFFFF");
            targets.put(block, parseColor(colorHex));
        }

        if (targets.isEmpty()) {
            foundOres = new HashMap<>();
            return;
        }

        int radius = Math.max(1, config.scanRadiusChunks);
        BlockPos playerPos = client.player.blockPosition();
        int pcx = playerPos.getX() >> 4;
        int pcz = playerPos.getZ() >> 4;

        Map<BlockPos, Integer> results = new HashMap<>();
        int bottomSection = level.getMinSection();

        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                LevelChunk chunk = level.getChunk(pcx + cx, pcz + cz);
                if (chunk == null) continue;

                LevelChunkSection[] sections = chunk.getSections();
                ChunkPos chunkPos = chunk.getPos();

                for (int sy = 0; sy < sections.length; sy++) {
                    LevelChunkSection section = sections[sy];
                    if (section == null || section.hasOnlyAir()) continue;

                    int worldSectionY = bottomSection + sy;
                    int baseY = worldSectionY * 16;

                    for (int lx = 0; lx < 16; lx++) {
                        for (int ly = 0; ly < 16; ly++) {
                            for (int lz = 0; lz < 16; lz++) {
                                Block block = section.getBlockState(lx, ly, lz).getBlock();
                                Integer color = targets.get(block);
                                if (color != null) {
                                    int wx = chunkPos.getMinBlockX() + lx;
                                    int wy = baseY + ly;
                                    int wz = chunkPos.getMinBlockZ() + lz;
                                    results.put(new BlockPos(wx, wy, wz), color);
                                }
                            }
                        }
                    }
                }
            }
        }

        foundOres = results;
    }

    private static int parseColor(String hex) {
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            return 0xFF000000 | (Integer.parseInt(clean, 16) & 0xFFFFFF);
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }
}
