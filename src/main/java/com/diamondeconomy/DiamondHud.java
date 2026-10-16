package com.diamondeconomy;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * HUD в стиле тёмных панелей: шапка с иконкой и числом справа, тонкий разделитель,
 * строки с иконкой, названием, значением справа и тонкой полоской снизу.
 */
public final class DiamondHud {
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int WHITE = 0xFFFFFFFF;
    /** Сиреневый акцент: значения справа и полоски. */
    private static final int LAV = 0xFF8F82DC;

    private static final int BG_TOP = 0xCC17171B;
    private static final int BG_BOTTOM = 0xBB0D0D10;
    private static final int BORDER = 0x48FFFFFF;
    private static final int SEP = 0x40FFFFFF;

    private static final int RADIUS = 4;
    private static final int PAD = 6;
    private static final int HEADER_H = 24;
    private static final int ROW_H = 26;
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

    /** Прямоугольник с углами радиуса 2 (для шлейфа). */
    private static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x + 2, y, x + w - 2, y + h, c);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, c);
        g.fill(x, y + 2, x + w, y + h - 2, c);
    }

    /** Горизонтальный отступ строки i у скруглённого прямоугольника высотой h и радиусом r. */
    private static int inset(int i, int h, int r) {
        int rr = Math.min(r, h / 2);
        float dy;
        if (i < rr) dy = rr - i - 0.5f;
        else if (i >= h - rr) dy = i + 0.5f - (h - rr);
        else return 0;
        return Math.round(rr - (float) Math.sqrt(Math.max(0f, rr * rr - dy * dy)));
    }

    private static void roundGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom) {
        for (int i = 0; i < h; i++) {
            int in = inset(i, h, r);
            g.fill(x + in, y + i, x + w - in, y + i + 1, lerpColor(top, bottom, i / (float) (h - 1)));
        }
    }

    /** Тонкая обводка снаружи скруглённого прямоугольника. */
    private static void roundRing(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        int total = h + 2;
        for (int i = 0; i < total; i++) {
            int oi = inset(i, total, r + 1);
            int xl = x - 1 + oi;
            int xr = x + w + 1 - oi;
            int row = y - 1 + i;
            if (i == 0 || i == total - 1) {
                g.fill(xl, row, xr, row + 1, c);
                continue;
            }
            int ii = inset(i - 1, h, r);
            int il = x + ii;
            int ir = x + w - ii;
            if (il > xl) g.fill(xl, row, il, row + 1, c);
            if (xr > ir) g.fill(ir, row, xr, row + 1, c);
        }
    }

    /** Тёмная полупрозрачная панель с тонкой светлой обводкой. */
    private static void panelBox(GuiGraphicsExtractor g, int x, int y, int w, int h, int border) {
        roundRing(g, x, y, w, h, RADIUS, border);
        roundGradient(g, x, y, w, h, RADIUS, BG_TOP, BG_BOTTOM);
        g.fill(x + RADIUS, y, x + w - RADIUS, y + 1, 0x18FFFFFF); // тонкий блик по верхней кромке
    }

    /** Шапка: иконка и подпись слева, число справа, под ней разделитель. */
    private static void drawHeader(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, String right) {
        g.item(icon, x + PAD, y + 4);
        g.text(mc.font, "Алмазы", x + PAD + 20, y + 8, WHITE, true);
        g.text(mc.font, right, x + w - PAD - mc.font.width(right), y + 8, WHITE, true);
        g.fill(x + PAD, y + HEADER_H, x + w - PAD, y + HEADER_H + 1, SEP);
    }

    /** Строка: [иконка] название ........ значение, под ней тонкая полоска слева направо. */
    private static void drawRow(GuiGraphicsExtractor g, Minecraft mc, int x, int w, int rowY, ItemStack ic,
                                String name, int nameColor, String value, int valueColor,
                                float frac, int barColor, int smear) {
        int tx = x + PAD;
        if (ic != null) {
            g.item(ic, x + PAD, rowY);
            tx = x + PAD + 20;
        }
        g.text(mc.font, name, tx, rowY + 4, nameColor, true);

        int vx = x + w - PAD - mc.font.width(value);
        if (smear != 0) {
            g.text(mc.font, value, vx, rowY + 4 + smear, withAlpha(valueColor, 0x44), false);
        }
        g.text(mc.font, value, vx, rowY + 4, valueColor, true);

        int barW = (int) ((w - 2 * PAD) * Math.max(0f, Math.min(1f, frac)));
        if (barW > 0) {
            g.fill(x + PAD, rowY + 19, x + PAD + barW, rowY + 21, barColor);
        }
    }

    private static int panelHeight(int rows) {
        return HEADER_H + 5 + rows * ROW_H;
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
        boolean rolling = Math.abs(total - shown) >= 1f;
        int inv = Math.round(shown);
        int chest = DiamondTracker.chestTotal;
        int ender = DiamondTracker.enderTotal;

        int rows = 1 + (chest >= 0 ? 1 : 0) + (ender >= 0 ? 1 : 0);
        int sum = inv + Math.max(0, chest) + Math.max(0, ender);

        // ширина по самому длинному содержимому
        int nameW = mc.font.width("Инвентарь");
        int valW = mc.font.width(String.valueOf(inv));
        if (chest >= 0) {
            nameW = Math.max(nameW, mc.font.width("Сундук"));
            valW = Math.max(valW, mc.font.width(String.valueOf(chest)));
        }
        if (ender >= 0) {
            nameW = Math.max(nameW, mc.font.width("Эндер-сундук"));
            valW = Math.max(valW, mc.font.width(String.valueOf(ender)));
        }
        int rowsW = PAD + 20 + nameW + 14 + valW + PAD;
        int headW = PAD + 20 + mc.font.width("Алмазы") + 14 + mc.font.width(String.valueOf(sum)) + PAD;
        int w = Math.max(118, Math.max(rowsW, headW));
        int h = panelHeight(rows);

        int x = cfg.hudX;
        int y = cfg.hudY;

        panelBox(g, x, y, w, h, BORDER);
        drawHeader(g, mc, x, y, w, String.valueOf(sum));

        // цвет значения/полоски: сиреневый, при изменении суммы мигает зелёным/красным
        int invColor = LAV;
        if (DiamondTracker.flashUntilMs > now) {
            invColor = DiamondTracker.flashDir > 0 ? GREEN : RED;
        }
        int smear = rolling ? (total > shown ? 3 : -3) : 0;
        float base = Math.max(1, sum);

        int rowY = y + HEADER_H + 5;
        drawRow(g, mc, x, w, rowY, icon, "Инвентарь", WHITE, String.valueOf(inv), invColor, inv / base, invColor, smear);
        rowY += ROW_H;
        if (chest >= 0) {
            drawRow(g, mc, x, w, rowY, chestIcon, "Сундук", WHITE, String.valueOf(chest), LAV, chest / base, LAV, 0);
            rowY += ROW_H;
        }
        if (ender >= 0) {
            drawRow(g, mc, x, w, rowY, enderIcon, "Эндер-сундук", WHITE, String.valueOf(ender), LAV, ender / base, LAV, 0);
        }
    }

    // ------------------------------------------------------------------ тосты

    private static String mmss(long ms) {
        long s = Math.max(0L, (ms + 999) / 1000);
        long m = s / 60;
        long r = s % 60;
        return (m < 10 ? "0" : "") + m + ":" + (r < 10 ? "0" : "") + r;
    }

    private static void drawToasts(GuiGraphicsExtractor g, Minecraft mc, DiamondConfig cfg, long now) {
        long duration = cfg.toastDurationTicks * 50L;
        DiamondTracker.TOASTS.removeIf(t -> now - t.createdMs > duration);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int w = 128;
        int h = panelHeight(1);
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

            // «motion blur»: пока плашка едет, за ней тянется затухающий шлейф
            if (k < 1f) {
                int dir = age < duration / 2 ? -1 : 1; // при появлении шлейф сверху, при уходе снизу
                for (int j = 3; j >= 1; j--) {
                    int a = (int) (0x30 * (1f - k) * (4 - j) / 3f);
                    if (a > 0) roundRect(g, x, y + dir * j * 5, w, h, withAlpha(0xB4B4C8, a));
                }
            }

            panelBox(g, x, y, w, h, withAlpha(accent, 0x60));
            drawHeader(g, mc, x, y, w, String.valueOf(t.total));

            String title = (gain ? "+" : "-") + Math.abs(t.delta) + " " + plural(t.delta);
            long leftMs = Math.max(0L, duration - age);
            float left = Math.max(0f, 1f - age / (float) duration);
            drawRow(g, mc, x, w, y + HEADER_H + 5, null, title, accent, mmss(leftMs), LAV, left, accent, 0);

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
