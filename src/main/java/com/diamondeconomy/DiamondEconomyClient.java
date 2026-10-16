package com.diamondeconomy;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class DiamondEconomyClient implements ClientModInitializer {
    public static final String MOD_ID = "diamondeconomy";

    private static KeyMapping editKey;

    @Override
    public void onInitializeClient() {
        DiamondConfig.load();

        // Клавиша (по умолчанию K, меняется в Настройки > Управление): режим настройки панели
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        editKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.diamondeconomy.edit_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                category));

        // Считаем алмазы раз в тик и ловим нажатие клавиши настройки
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            DiamondTracker.tick(mc);
            while (editKey.consumeClick()) {
                if (mc.player != null && mc.gui.screen() == null) {
                    mc.gui.setScreen(new DiamondHud.Editor());
                }
            }
        });

        // HUD: счётчик + тосты
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "diamond_hud"),
                DiamondHud::render);

        // Цена в тултипе предмета
        ItemTooltipCallback.EVENT.register(PriceTooltip::onTooltip);
    }
}
