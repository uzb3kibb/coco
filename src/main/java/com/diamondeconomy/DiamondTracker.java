package com.diamondeconomy;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Рисует счётчик алмазов (капсула слева сверху) и всплывающие тосты (стеклянные панели). */
public final class DiamondHud {
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFB4BDC8;
    private static final int CYAN = 0xFF4DD0E1;
    private static final int AMBER = 0xFFE0A84F;
    private static final int PURPLE = 0xFFB07CFF;
    /** «Стекло»: полупрозрачный градиент, сквозь который виден мир. */
    private static final int GLASS_TOP = 0xA0263344;
    private static final int GLASS_BOTTOM = 0x8C0C121A;
    private static final long SLIDE_MS = 350;

    private static ItemStack icon;
    private static ItemStack chestIcon;
    private static ItemStack enderIcon;
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
        if (icon == null) {
            icon = new ItemStack(Items.DIAMOND);
            chestIcon = new ItemStack(Items.CHEST);
            enderIcon = new ItemStack(Items.ENDER_CHEST);
        }

        if (cfg.showHud) drawCounter(g, mc, cfg, now);
        if (cfg.showToasts) drawToasts(g, mc, cfg, now);
    }

    // ------------------------------------------------------------ примитивы

    private static int lerpColor(int a, int b, float t) {
        int aa = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        int ar = (int) (((a >>> 16) & 255) * (1 - t) + ((b >>> 16) & 255) * t);
        int ag = (int) (((a >>> 8) & 255) * (1 - t) + ((b >>> 8) & 255) * t);
        int ab = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Прямоугольник со скруглёнными углами (радиус 2 px). */
    private static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x + 2, y, x + w - 2, y + h, c);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, c);
        g.fill(x, y + 2, x + w, y + h - 2, c);
    }

    /** Стеклянная панель: полупрозрачный градиент, блик сверху, тонкая обводка по краю. */
    private static void glass(GuiGraphicsExtractor g, int x, int y, int w, int h, int border) {
        for (int i = 0; i < h; i++) {
            int inset = (i == 0 || i == h - 1) ? 2 : ((i == 1 || i == h - 2) ? 1 : 0);
            int c = lerpColor(GLASS_TOP, GLASS_BOTTOM, i / (float) (h - 1));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
        }
        g.fill(x + 3, y + 1, x + w - 3, y + 2, 0x22FFFFFF); // блик

        // обводка только снаружи, чтобы не просвечивала сквозь стекло
        g.fill(x + 2, y - 1, x + w - 2, y, border);
        g.fill(x + 2, y + h, x + w - 2, y + h + 1, border);
        g.fill(x - 1, y + 2, x, y + h - 2, border);
        g.fill(x + w, y + 2, x + w + 1, y + h - 2, border);
        g.fill(x + 1, y, x + 2, y + 1, border);
        g.fill(x, y + 1, x + 1, y + 2, border);
        g.fill(x + w - 2, y, x + w - 1, y + 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + 2, border);
        g.fill(x + 1, y + h - 1, x + 2, y + h, border);
        g.fill(x, y + h - 2, x + 1, y + h - 1, border);
        g.fill(x + w - 2, y + h - 1, x + w - 1, y + h, border);
        g.fill(x + w - 1, y + h - 2, x + w, y + h - 1, border);
    }

    /** Отступ строки i у капсулы высотой h (радиус h/2). */
    private static int pillInset(int i, int h) {
        float r = h / 2f;
        float dy = Math.abs(i + 0.5f - r);
        return Math.round(r - (float) Math.sqrt(Math.max(0f, r * r - dy * dy)));
    }

    /** Капсула (полностью скруглённые края) с вертикальным градиентом. */
    private static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
        for (int i = 0; i < h; i++) {
            int inset = pillInset(i, h);
            int c = lerpColor(top, bottom, h > 1 ? i / (float) (h - 1) : 0f);
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
        }
    }

    /** Тонкое кольцо-обводка вокруг капсулы. */
    private static void pillRing(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        int total = h + 2;
        for (int i = 0; i < total; i++) {
            int oi = pillInset(i, total);
            int xl = x - 1 + oi;
            int xr = x + w + 1 - oi;
            int row = y - 1 + i;
            if (i == 0 || i == total - 1) {
                g.fill(xl, row, xr, row + 1, c);
                continue;
            }
            int ii = pillInset(i - 1, h);
            int il = x + ii;
            int ir = x + w - ii;
            if (il > xl) g.fill(xl, row, il, row + 1, c);
            if (xr > ir) g.fill(ir, row, xr, row + 1, c);
        }
    }

    /** Слот 22x22 с иконкой алмаза; (x, y) это левый верхний угол слота. */
    private static void iconSlot(GuiGraphicsExtractor g, int x, int y) {
        roundRect(g, x, y, 22, 22, 0x28FFFFFF);
        roundRect(g, x + 1, y + 1, 20, 20, 0x70000000);
        g.item(icon, x + 3, y + 3);
    }

    // ---------------------------------------------------------------- счётчик

    private static String blocks(int n) {
        return n >= 9 ? (n / 9) + " бл. + " + (n % 9) : "";
    }

    /** Одна капсула: слот с иконкой, число и (если есть) расшифровка в блоках. Дизайн общий для всех капсул. */
    private static void drawPill(GuiGraphicsExtractor g, Minecraft mc, int x, int y, ItemStack ic,
                                 String num, String sub, int numColor, int accent, int smear) {
        int h = 26;
        int x0 = x + 30;
        int numW = mc.font.width(num);
        int w = 30 + numW + (sub.isEmpty() ? 0 : 8 + mc.font.width(sub)) + 12;

        pillRing(g, x, y, w, h, withAlpha(accent, 0x80));
        pill(g, x, y, w, h, GLASS_TOP, GLASS_BOTTOM);

        pill(g, x + 3, y + 3, 20, 20, 0x66000000, 0x66000000);
        g.item(ic, x + 5, y + 5);

        if (smear != 0) {
            g.text(mc.font, num, x0, y + 9 + smear, withAlpha(numColor, 0x44), false);
        }
        g.text(mc.font, num, x0, y + 9, numColor, true);
        if (!sub.isEmpty()) {
            g.text(mc.font, sub, x0 + numW + 8, y + 9, GRAY, true);
        }
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
        boolean rolling = Math.abs(total - shown) >= 1f;

        int numColor = WHITE;
        int accent = CYAN;
        if (DiamondTracker.flashUntilMs > now) {
            numColor = DiamondTracker.flashDir > 0 ? GREEN : RED;
            accent = numColor;
        }

        int x = cfg.hudX;
        int y = cfg.hudY;
        int smear = rolling ? (total > shown ? 3 : -3) : 0;
        drawPill(g, mc, x, y, icon, String.valueOf(Math.round(shown)), blocks(total), numColor, accent, smear);

        // капсулы под основной: открытый сундук и эндер-сундук
        int step = 30;
        int py = y + step;
        int chest = DiamondTracker.chestTotal;
        if (chest >= 0) {
            drawPill(g, mc, x, py, chestIcon, String.valueOf(chest), blocks(chest), WHITE, AMBER, 0);
            py += step;
        }
        int ender = DiamondTracker.enderTotal;
        if (ender >= 0) {
            drawPill(g, mc, x, py, enderIcon, String.valueOf(ender), blocks(ender), WHITE, PURPLE, 0);
        }
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

            // «motion blur»: пока плашка едет, за ней тянется затухающий шлейф
            if (k < 1f) {
                int dir = age < duration / 2 ? -1 : 1; // при появлении шлейф сверху, при уходе снизу
                for (int j = 3; j >= 1; j--) {
                    int a = (int) (0x34 * (1f - k) * (4 - j) / 3f);
                    if (a > 0) roundRect(g, x, y + dir * j * 5, w, h, withAlpha(0x8FB4D8, a));
                }
            }

            glass(g, x, y, w, h, withAlpha(accent, 0x70));
            iconSlot(g, x + 4, y + 9);

            // шапка: подпись слева, всего справа
            String totalStr = String.valueOf(t.total);
            g.text(mc.font, "Алмазы", x0, y + 7, GRAY, true);
            g.text(mc.font, totalStr, right - mc.font.width(totalStr), y + 7, WHITE, true);
            g.fill(x0, y + 17, right, y + 18, 0x30FFFFFF);

            // строка: изменение слева, секунды справа
            String title = (gain ? "+" : "-") + Math.abs(t.delta) + " " + plural(t.delta);
            long leftMs = Math.max(0L, duration - age);
            String secs = ((leftMs + 999) / 1000) + " с";
            g.text(mc.font, title, x0, y + 21, accent, true);
            g.text(mc.font, secs, right - mc.font.width(secs), y + 21, GRAY, true);

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
