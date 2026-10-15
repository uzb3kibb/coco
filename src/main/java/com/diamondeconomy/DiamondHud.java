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
    private static final int GRAY = 0xFFA0A8B0;
    private static final int CYAN = 0xFF4DD0E1;
    private static final int BG = 0xD0141A22;
    private static final long SLIDE_MS = 350;

    private static ItemStack icon;
    private static float shown = -1f; // число, которое сейчас показано (для плавной анимации)

    private DiamondHud() {}

    public static void render(GuiGraphicsExtractor g, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            shown = -1f;
            return;
        }
        DiamondConfig cfg = DiamondConfig.get();
        long now = System.currentTimeMillis();
        if (icon == null) icon = new ItemStack(Items.DIAMOND);

        if (cfg.showHud) drawCounter(g, mc, cfg, now);
        if (cfg.showToasts) drawToasts(g, mc, cfg, now);
    }

    /** Прямоугольник со срезанными углами (в пиксельном стиле майнкрафта). */
    private static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    private static int lerpColor(int a, int b, float t) {
        int aa = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        int ar = (int) (((a >>> 16) & 255) * (1 - t) + ((b >>> 16) & 255) * t);
        int ag = (int) (((a >>> 8) & 255) * (1 - t) + ((b >>> 8) & 255) * t);
        int ab = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    private static void drawCounter(GuiGraphicsExtractor g, Minecraft mc, DiamondConfig cfg, long now) {
        int total = DiamondTracker.total;

        // плавное «накручивание» числа
        if (shown < 0 || Math.abs(total - shown) > 300) {
            shown = total;
        } else {
            shown += (total - shown) * 0.2f;
            if (Math.abs(total - shown) < 0.5f) shown = total;
        }
        int value = Math.round(shown);

        String text = String.valueOf(value);
        boolean hasSub = total >= 9;
        String sub = (total / 9) + " бл. + " + (total % 9);

        int color = WHITE;
        int accent = CYAN;
        if (DiamondTracker.flashUntilMs > now) {
            color = DiamondTracker.flashDir > 0 ? GREEN : RED;
            accent = color;
        }

        int x = cfg.hudX;
        int y = cfg.hudY;
        int h = hasSub ? 30 : 26;
        int textW = Math.max(mc.font.width(text), hasSub ? mc.font.width(sub) : 0);
        int w = 32 + textW + 8;

        // панель с вертикальным градиентом
        int top = 0xE02A3A4C;
        int bottom = 0xE00B1118;
        for (int i = 0; i < h; i++) {
            float t = i / (float) (h - 1);
            int inset = (i == 0 || i == h - 1) ? 1 : 0;
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, lerpColor(top, bottom, t));
        }
        // светлая кромка сверху и цветная линия снизу
        g.fill(x + 2, y, x + w - 2, y + 1, 0x40FFFFFF);
        g.fill(x + 4, y + h - 2, x + w - 4, y + h - 1, accent);

        // «слот» для иконки
        g.fill(x + 4, y + 4, x + 26, y + 22, 0xFF3C5568);
        g.fill(x + 5, y + 5, x + 25, y + 21, 0xFF0B1016);
        g.item(icon, x + 7, y + 5);

        // число и расшифровка
        if (hasSub) {
            g.text(mc.font, text, x + 32, y + 6, color, true);
            g.text(mc.font, sub, x + 32, y + 17, GRAY, false);
        } else {
            g.text(mc.font, text, x + 32, y + 9, color, true);
        }
    }

    private static void drawToasts(GuiGraphicsExtractor g, Minecraft mc, DiamondConfig cfg, long now) {
        long duration = cfg.toastDurationTicks * 50L;
        DiamondTracker.TOASTS.removeIf(t -> now - t.createdMs > duration);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int w = 150;
        int h = 32;
        int gap = 6;
        int index = 0;

        for (DiamondTracker.Toast t : DiamondTracker.TOASTS) {
            long age = now - t.createdMs;
            float in = Math.min(1f, age / (float) SLIDE_MS);
            float out = Math.min(1f, (duration - age) / (float) SLIDE_MS);
            float k = easeOut(Math.max(0f, Math.min(in, out)));

            int x = (screenW - w) / 2;
            int targetY = 8 + index * (h + gap);
            int y = (int) (targetY - (h + 16) * (1f - k));

            boolean gain = t.delta > 0;
            int accent = gain ? GREEN : RED;
            String title = (gain ? "+" : "-") + Math.abs(t.delta) + " " + plural(t.delta);
            String sub = "Всего: " + t.total;

            roundRect(g, x, y, w, h, BG);
            g.fill(x + 4, y + 6, x + 6, y + h - 8, accent);

            g.item(icon, x + 11, y + 5);
            g.text(mc.font, title, x + 32, y + 6, accent, true);
            g.text(mc.font, sub, x + 32, y + 18, GRAY, false);

            int barX = x + 6;
            int barW = w - 12;
            int barY = y + h - 4;
            float left = Math.max(0f, 1f - age / (float) duration);
            g.fill(barX, barY, barX + barW, barY + 1, 0x40FFFFFF);
            g.fill(barX, barY, barX + (int) (barW * left), barY + 1, accent);

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
