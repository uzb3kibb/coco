package com.diamondeconomy;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Рисует счётчик алмазов и всплывающие тосты. */
public final class DiamondHud {
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final long SLIDE_MS = 300;

    private static ItemStack icon;

    private DiamondHud() {}

    public static void render(GuiGraphicsExtractor g, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        DiamondConfig cfg = DiamondConfig.get();
        long now = System.currentTimeMillis();

        if (cfg.showHud) drawCounter(g, mc, cfg, now);
        if (cfg.showToasts) drawToasts(g, mc, cfg, now);
    }

    private static void drawCounter(GuiGraphicsExtractor g, Minecraft mc, DiamondConfig cfg, long now) {
        if (icon == null) icon = new ItemStack(Items.DIAMOND);

        String text = String.valueOf(DiamondTracker.total);
        int color = WHITE;
        if (DiamondTracker.flashUntilMs > now) {
            color = DiamondTracker.flashDir > 0 ? GREEN : RED;
        }

        int x = cfg.hudX;
        int y = cfg.hudY;
        int w = 24 + mc.font.width(text) + 6;
        g.fill(x, y, x + w, y + 20, 0x90000000);
        g.item(icon, x + 2, y + 2);
        g.text(mc.font, text, x + 22, y + 6, color, true);
    }

    private static void drawToasts(GuiGraphicsExtractor g, Minecraft mc, DiamondConfig cfg, long now) {
        long duration = cfg.toastDurationTicks * 50L;
        DiamondTracker.TOASTS.removeIf(t -> now - t.createdMs > duration);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int w = 140;
        int h = 26;
        int index = 0;

        for (DiamondTracker.Toast t : DiamondTracker.TOASTS) {
            long age = now - t.createdMs;
            float in = Math.min(1f, age / (float) SLIDE_MS);
            float out = Math.min(1f, (duration - age) / (float) SLIDE_MS);
            float k = easeOut(Math.max(0f, Math.min(in, out)));

            int x = (screenW - w) / 2;
            int targetY = 8 + index * (h + 4);
            int y = (int) (targetY - (h + 12) * (1f - k));

            boolean gain = t.delta > 0;
            int accent = gain ? GREEN : RED;
            String title = (gain ? "+" : "-") + Math.abs(t.delta) + " " + plural(t.delta);
            String sub = "Всего: " + t.total;

            g.fill(x, y, x + w, y + h, 0xE0101010);
            g.fill(x, y, x + 3, y + h, accent);
            g.text(mc.font, title, x + 9, y + 5, accent, true);
            g.text(mc.font, sub, x + 9, y + 16, GRAY, false);
            index++;
        }
    }

    private static float easeOut(float p) {
        float inv = 1f - p;
        return 1f - inv * inv * inv;
    }

    private static String plural(int n) {
        n = Math.abs(n);
        int m100 = n % 100;
        int m10 = n % 10;
        if (m100 >= 11 && m100 <= 14) return "алмазов";
        if (m10 == 1) return "алмаз";
        if (m10 >= 2 && m10 <= 4) return "алмаза";
        return "алмазов";
    }
}
