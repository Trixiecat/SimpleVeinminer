package net.cyanmarine.simpleveinminer.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.cyanmarine.simpleveinminer.Constants;
import net.cyanmarine.simpleveinminer.SimpleVeinminer;
import net.cyanmarine.simpleveinminer.commands.CommandRegisterClient;
import net.cyanmarine.simpleveinminer.config.SimpleConfig;
import net.cyanmarine.simpleveinminer.config.SimpleConfigClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public class SimpleVeinminerClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(SimpleVeinminer.MOD_ID + " - client");
    public static boolean isVeinMiningServerSide = false;
    public static AtomicBoolean isInstalledOnServerSide = new AtomicBoolean(false);
    static SimpleConfig.SimpleConfigCopy worldConfig;
    private static SimpleConfigClient config;

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SimpleVeinminer.getId("veinminer"));
    public static KeyMapping veinMineKeybind;
    public static KeyMapping increaseRadius;
    public static KeyMapping decreaseRadius;
    public static KeyMapping freezePreview;
    public static KeyMapping openSettings;
    /** Set by /veinminingclient config; the screen is opened on the next tick (after the chat screen closes) */
    public static boolean openSettingsNextTick = false;
    public static boolean veinMining;

    public static SimpleConfig.SimpleConfigCopy getWorldConfig() {
        if (worldConfig == null) return SimpleConfig.SimpleConfigCopy.from(config);
        return worldConfig;
    }

    public static SimpleConfigClient getConfig() {
        return config;
    }

    public static void send(CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
    }

    private void changeClientRadius(Minecraft client, int change) {
        SimpleConfigClient config = SimpleVeinminerClient.getConfig();

        int newRadius = Mth.clamp(config.clientRadius + change, 1, Math.max(1, getWorldConfig().limits.radius));
        config.setClientRadius(newRadius);
        if (client.player != null)
            client.player.sendOverlayMessage(Component.translatable("messages.simpleveinminer.radiusChanged", newRadius));

        send(new Constants.RadiusPayload(newRadius));
    }

    @Override
    public void onInitializeClient() {
        veinMining = false;
        config = SimpleConfig.load(SimpleConfigClient.class);
        SimpleVeinminer.setConfig(config);
        SimpleVeinminer.setClientRadiusSupplier(() -> config.clientRadius);
        SimpleVeinminer.setShowRestrictionMessages(config.showRestrictionMessages);
        worldConfig = null;

        veinMineKeybind = KeyMappingHelper.registerKeyMapping(new ToggleKeyMapping("key.simpleveinminer.veinminingKey", InputConstants.KEY_GRAVE, CATEGORY, () -> config.keybindToggles, false));
        increaseRadius = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.simpleveinminer.increaseRadius", InputConstants.KEY_RBRACKET, CATEGORY));
        decreaseRadius = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.simpleveinminer.decreaseRadius", InputConstants.KEY_LBRACKET, CATEGORY));
        freezePreview = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.simpleveinminer.freezePreview", InputConstants.UNKNOWN.getValue(), CATEGORY));
        openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.simpleveinminer.openSettings", InputConstants.UNKNOWN.getValue(), CATEGORY));

        new CommandRegisterClient();
        VeinminePreview.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SimpleVeinminer.setShowRestrictionMessages(config.showRestrictionMessages);
            if (openSettingsNextTick) {
                openSettingsNextTick = false;
                client.gui.setScreen(new ConfigScreen(client.gui.screen()));
            }
            while (openSettings.consumeClick())
                client.gui.setScreen(new ConfigScreen(client.gui.screen()));
            if (client.player == null) return;
            if (veinMining != veinMineKeybind.isDown()) {
                veinMining = !veinMining;
                if (config.keybindToggles)
                    client.player.sendOverlayMessage(veinMining ? Component.translatable("messages.simpleveinminer.veinminingToggled.on") : Component.translatable("messages.simpleveinminer.veinminingToggled.off"));

                send(new Constants.VeinminePayload(veinMining));
            }
            while (increaseRadius.consumeClick())
                changeClientRadius(client, 1);
            while (decreaseRadius.consumeClick())
                changeClientRadius(client, -1);
            while (freezePreview.consumeClick()) {
                VeinminePreview.previewFrozen = !VeinminePreview.previewFrozen;
                client.player.sendOverlayMessage(VeinminePreview.previewFrozen ? Component.translatable("messages.simpleveinminer.previewFrozen") : Component.translatable("messages.simpleveinminer.previewUnfrozen"));
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            worldConfig = null;
            isInstalledOnServerSide.set(false);
            isVeinMiningServerSide = false;
            VeinminePreview.reset();
        });

        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            worldConfig = null;
            isInstalledOnServerSide.set(false);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Let the server know our current state
            send(new Constants.RadiusPayload(config.clientRadius));
            send(new Constants.VeinminePayload(veinMining));
        });

        ClientPlayNetworking.registerGlobalReceiver(Constants.ConfigSyncPayload.TYPE, (payload, context) -> {
            worldConfig = payload.config();
            isInstalledOnServerSide.set(true);
        });

        ClientPlayNetworking.registerGlobalReceiver(Constants.ServerSideUpdatePayload.TYPE, (payload, context) -> {
            isVeinMiningServerSide = payload.enabled();
        });

        LOGGER.info("Simple VeinMiner initialized");
    }
}
