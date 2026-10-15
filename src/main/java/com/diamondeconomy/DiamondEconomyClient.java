package com.diamondeconomy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public class DiamondEconomyClient implements ClientModInitializer {
    public static final String MOD_ID = "diamondeconomy";

    @Override
    public void onInitializeClient() {
        DiamondConfig.load();

        // Считаем алмазы раз в тик
        ClientTickEvents.END_CLIENT_TICK.register(DiamondTracker::tick);

        // HUD: счётчик + тосты
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "diamond_hud"),
                DiamondHud::render);

        // Цена в тултипе предмета
        ItemTooltipCallback.EVENT.register(PriceTooltip::onTooltip);
    }
}
