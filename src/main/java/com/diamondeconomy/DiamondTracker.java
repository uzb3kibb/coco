package com.diamondeconomy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Считает алмазы в инвентаре и превращает резкие изменения в тосты. */
public final class DiamondTracker {
    public static final class Toast {
        public final long createdMs = System.currentTimeMillis();
        public final int delta;
        public final int total;

        Toast(int delta, int total) {
            this.delta = delta;
            this.total = total;
        }
    }

    /** Текущее количество алмазов (блок = 9). */
    public static int total = 0;
    public static final List<Toast> TOASTS = new ArrayList<>();
    /** Для подсветки счётчика: до какого момента (мс) мигать и в какую сторону. */
    public static long flashUntilMs = 0;
    public static int flashDir = 0;

    private static boolean ready = false;
    private static int baseline = 0;
    private static int pending = 0;
    private static int quietTicks = 0;
    private static boolean screenWasOpen = false;

    private DiamondTracker() {}

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            ready = false;
            pending = 0;
            quietTicks = 0;
            screenWasOpen = false;
            total = 0;
            TOASTS.clear();
            return;
        }

        DiamondConfig cfg = DiamondConfig.get();
        int now = count(player);
        total = now;

        if (!ready) {
            baseline = now;
            ready = true;
            return;
        }

        // Пока открыт сундук/инвентарь/торговля, ничего не фиксируем: ждём закрытия окна
        if (mc.gui.screen() instanceof AbstractContainerScreen<?>) {
            screenWasOpen = true;
            return;
        }
        if (screenWasOpen) {
            screenWasOpen = false;
            if (cfg.silentAfterScreen) {
                baseline = now;
                pending = 0;
                quietTicks = 0;
                return;
            }
        }

        int diff = now - baseline;
        if (diff != 0) {
            baseline = now;
            pending += diff;
            quietTicks = 0;
            flashDir = diff > 0 ? 1 : -1;
            flashUntilMs = System.currentTimeMillis() + 800;
        } else if (pending != 0) {
            quietTicks++;
            if (quietTicks >= cfg.toastMergeTicks) {
                flush(now, cfg);
            }
        }
    }

    private static void flush(int now, DiamondConfig cfg) {
        int delta = pending;
        pending = 0;
        quietTicks = 0;
        if (cfg.showToasts && Math.abs(delta) >= cfg.minToastDiamonds) {
            TOASTS.add(new Toast(delta, now));
            while (TOASTS.size() > 3) TOASTS.remove(0);
        }
    }

    private static int count(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(Items.DIAMOND)) n += s.getCount();
            else if (s.is(Items.DIAMOND_BLOCK)) n += s.getCount() * 9;
        }
        return n;
    }
}
