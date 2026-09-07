package com.example.orevision;

import com.example.orevision.config.ModConfig;
import com.example.orevision.render.OreEspRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

public class OreVisionClient implements ClientModInitializer {

    public static final String MOD_ID = "orevision";
    public static final ModState STATE = new ModState();
    public static ModConfig CONFIG;

    // Matches the "resourcepacks/<id path>" convention Fabric API's
    // registerBuiltinResourcePack expects inside the mod jar.
    private static final Identifier XRAY_PACK_ID = Identifier.fromNamespaceAndPath(MOD_ID, "orevision_xray");
    private static final String XRAY_PACK_PROFILE = "builtin/" + XRAY_PACK_ID.getPath();

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    private KeyMapping toggleEspKey;
    private KeyMapping toggleXrayKey;
    private KeyMapping reloadConfigKey;

    @Override
    public void onInitializeClient() {
        CONFIG = ModConfig.loadOrCreateDefault();

        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container ->
                ResourceManagerHelper.registerBuiltinResourcePack(
                        XRAY_PACK_ID,
                        container,
                        Component.literal("Ore Vision X-ray"),
                        ResourcePackActivationType.NORMAL
                )
        );

        toggleEspKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.orevision.toggle_esp",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_O,
                CATEGORY
        ));

        toggleXrayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.orevision.toggle_xray",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_P,
                CATEGORY
        ));

        reloadConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.orevision.reload_config",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_L,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        OreEspRenderer.register();
    }

    private void onClientTick(Minecraft client) {
        while (toggleEspKey.consumeClick()) {
            STATE.espEnabled = !STATE.espEnabled;
            sendMessage(client, "Ore ESP " + (STATE.espEnabled ? "enabled" : "disabled"));
        }

        while (toggleXrayKey.consumeClick()) {
            STATE.xrayEnabled = !STATE.xrayEnabled;
            applyXrayPackState(client);
            sendMessage(client, "X-ray " + (STATE.xrayEnabled ? "enabled" : "disabled") + " (reloading resources...)");
        }

        while (reloadConfigKey.consumeClick()) {
            CONFIG = ModConfig.loadOrCreateDefault();
            sendMessage(client, "Ore Vision config reloaded from disk");
        }

        OreScanner.onClientTick(client);
    }

    /**
     * Enables/disables the bundled X-ray resource pack and triggers a resource reload.
     * VERIFY: PackRepository.enable/disable(String) is written against the most
     * recently documented Mojang names for this class. If these don't resolve,
     * check your IDE's autocomplete on Minecraft.getInstance().getResourcePackRepository()
     * for the current enable/disable or getSelectedIds()/setSelected() methods.
     */
    private void applyXrayPackState(Minecraft client) {
        PackRepository repository = client.getResourcePackRepository();

        if (STATE.xrayEnabled) {
            repository.enable(XRAY_PACK_PROFILE);
        } else {
            repository.disable(XRAY_PACK_PROFILE);
        }

        client.reloadResourcePacks();
    }

    private void sendMessage(Minecraft client, String msg) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Ore Vision] " + msg));
        }
    }
}
