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
    private static final int GRAY = 0xFF8D97A3;
    private static final int CYAN = 0xFF4DD0E1;
    private static final int PANEL_BG = 0xE0101620;
    private static final int PANEL_BORDER = 0x38FFFFFF;
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

    /** Прямоугольник со скруглёнными углами (радиус 2 px). */
    private static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x + 2, y, x + w - 2, y + h, c);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, c);
        g.fill(x, y + 2, x + w, y + h - 2, c);
    }

    /** Панель с тонкой светлой обводкой. */
    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        roundRect(g, x - 1, y - 1, w + 2, h + 2, PANEL_BORDER);
        roundRect(g, x, y, w, h, PANEL_BG);
    }

    /** Слот 22x22 с иконкой алмаза; (x, y) это левый верхний угол слота. */
    private static void iconSlot(GuiGraphicsExtractor g, int x, int y) {
        roundRect(g, x, y, 22, 22, 0x30FFFFFF);
        roundRect(g, x + 1, y + 1, 20, 20, 0xFF0A0E14);
        g.item(icon, x + 3, y + 3);
    }

    // ---------------------------------------------------------------- счётчик

    private static void drawCounter(GuiGraphicsExtractor g, Minecraft mc, DiamondConfig cfg, long now) {
        int total = DiamondTracker.total;

        // плавное «накручивание» числа
        if (shown < 0 || Math.abs(total - shown) > 300) {
            shown = total;
        } else {
            shown += (total - shown) * 0.2f;
            if (Math.abs(total - shown) < 0.5f) shown = total;
        }

        String header = "Алмазы";
        String num = String.valueOf(Math.round(shown));
        String sub = total >= 9 ? (total / 9) + " бл. + " + (total % 9) : "";

        int numColor = WHITE;
        int accent = CYAN;
        if (DiamondTracker.flashUntilMs > now) {
            numColor = DiamondTracker.flashDir > 0 ? GREEN : RED;
            accent = numColor;
        }

        int x = cfg.hudX;
        int y = cfg.hudY;
        int h = 38;
        int x0 = x + 31;
        int rowW = mc.font.width(num) + (sub.isEmpty() ? 0 : 10 + mc.font.width(sub));
        int w = Math.max(92, 31 + Math.max(mc.font.width(header), rowW) + 8);
        int right = x + w - 8;

        panel(g, x, y, w, h);
        iconSlot(g, x + 4, y + 8);

        // шапка и разделитель
        g.text(mc.font, header, x0, y + 7, GRAY, false);
        g.fill(x0, y + 17, right, y + 18, 0x22FFFFFF);

        // число слева, расшифровка в блоках справа
        g.text(mc.font, num, x0, y + 21, numColor, true);
        if (!sub.isEmpty()) {
            g.text(mc.font, sub, right - mc.font.width(sub), y + 21, GRAY, false);
        }

        // полоска: сколько осталось до следующего алмазного блока (по 9 алмазов)
        int barY = y + h - 6;
        float fill = (total % 9) / 9f;
        g.fill(x0, barY, right, barY + 2, 0x30FFFFFF);
        g.fill(x0, barY, x0 + (int) ((right - x0) * fill), barY + 2, accent);
    }

    // ------------------------------------------------------------------ тосты

    private static void drawToasts(GuiGraphicsExtractor g, Minecraft mc, DiamondConfig cfg, long now) {
        long duration = cfg.toastDurationTicks * 50L;
        DiamondTracker.TOASTS.removeIf(t -> now - t.createdMs > duration);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int w = 150;
        int h = 40;
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
            int right = x + w - 8;
            int x0 = x + 31;

            panel(g, x, y, w, h);
            iconSlot(g, x + 4, y + 9);

            // шапка: подпись слева, всего справа
            String totalStr = String.valueOf(t.total);
            g.text(mc.font, "Алмазы", x0, y + 7, GRAY, false);
            g.text(mc.font, totalStr, right - mc.font.width(totalStr), y + 7, WHITE, false);
            g.fill(x0, y + 17, right, y + 18, 0x22FFFFFF);

            // строка: изменение слева, секунды справа
            String title = (gain ? "+" : "-") + Math.abs(t.delta) + " " + plural(t.delta);
            long leftMs = Math.max(0L, duration - age);
            String secs = ((leftMs + 999) / 1000) + " с";
            g.text(mc.font, title, x0, y + 21, accent, true);
            g.text(mc.font, secs, right - mc.font.width(secs), y + 21, GRAY, false);

            // тонкая полоска оставшегося времени
            int barY = y + h - 7;
            float left = Math.max(0f, 1f - age / (float) duration);
            g.fill(x0, barY, right, barY + 2, 0x30FFFFFF);
            g.fill(x0, barY, x0 + (int) ((right - x0) * left), barY + 2, accent);

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
