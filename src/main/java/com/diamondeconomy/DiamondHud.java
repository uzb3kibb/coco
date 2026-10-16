package com.diamondeconomy;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * HUD в стиле «матового стекла»: светлая полупрозрачная панель с крупным скруглением, тёмная
 * плашка-шапка с бейджем-числом, строки со слайдером (круглая белая ручка). Всё плавно анимируется.
 */
public final class DiamondHud {
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xFFD3D9E2;
    private static final int ACCENT = 0xFF4DD0E1; // ледяной бирюзовый (цвет алмаза)

    private static final int GLASS_TOP = 0x78AEB6C2;
    private static final int GLASS_BOTTOM = 0x5C69727F;
    private static final int STRIP_TOP = 0x70141820;
    private static final int STRIP_BOTTOM = 0x600A0D12;
    private static final int BORDER = 0x66FFFFFF;
    private static final int TRACK = 0x38FFFFFF;

    private static final int R = 6;
    private static final int PAD = 5;
    private static final int HEAD_H = 20;
    private static final int ROW_H = 28;
    private static final int FOOT_H = 14;
    private static final long SLIDE_MS = 350;

    private static ItemStack icon;
    private static ItemStack chestIcon;
    private static ItemStack enderIcon;

    // ---- состояние анимаций ----
    private static long lastNs = 0;
    private static float dt = 0.016f;
    private static float slide = 0f;                       // появление панели 0..1
    private static float dispW = -1f;                      // размеры панели (плавно меняются)
    private static float dispH = -1f;
    private static float rSum = -1f, rInv = -1f, rChest = -1f, rEnder = -1f; // «накручиваемые» числа
    private static float fInv = 0f, fChest = 0f, fEnder = 0f;                // положение слайдеров 0..1
    private static boolean hadChest = false;
    private static boolean hadEnder = false;

    private DiamondHud() {}

    private static void resetAnim() {
        lastNs = 0;
        slide = 0f;
        dispW = -1f;
        dispH = -1f;
        rSum = -1f;
        rInv = -1f;
        rChest = -1f;
        rEnder = -1f;
        fInv = 0f;
        fChest = 0f;
        fEnder = 0f;
        hadChest = false;
        hadEnder = false;
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            resetAnim();
            return;
        }
        long nowNs = System.nanoTime();
        dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (nowNs - lastNs) / 1_000_000_000f);
        lastNs = nowNs;

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

    // ------------------------------------------------------------ анимация

    /** Плавное приближение к цели, не зависящее от частоты кадров. */
    private static float smooth(float cur, float target, float speed) {
        return cur + (target - cur) * (1f - (float) Math.exp(-speed * dt));
    }

    private static float snap(float v, float target) {
        return Math.abs(target - v) < 0.5f ? target : v;
    }

    /** «Накручивание» числа; первый кадр и большие скачки показываются сразу. */
    private static float roll(float cur, int target) {
        if (cur < 0 || Math.abs(target - cur) > 300) return target;
        return snap(smooth(cur, target, 12f), target);
    }

    private static int smearOf(float cur, int target) {
        float d = target - cur;
        return Math.abs(d) >= 1f ? (d > 0 ? 3 : -3) : 0;
    }

    private static float easeOut(float p) {
        float inv = 1f - p;
        return 1f - inv * inv * inv;
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
            float t = h > 1 ? i / (float) (h - 1) : 0f;
            g.fill(x + in, y + i, x + w - in, y + i + 1, lerpColor(top, bottom, t));
        }
    }

    private static void roundFill(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        roundGradient(g, x, y, w, h, r, c, c);
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

    /** «Матовое стекло»: светлая полупрозрачная панель с обводкой и бликом сверху. */
    private static void drawGlass(GuiGraphicsExtractor g, int x, int y, int w, int h, int border) {
        roundRing(g, x, y, w, h, R, border);
        roundGradient(g, x, y, w, h, R, GLASS_TOP, GLASS_BOTTOM);
        g.fill(x + R, y + 1, x + w - R, y + 2, 0x40FFFFFF);
    }

    /** Тёмная плашка-шапка: иконка и «Алмазы» слева, бейдж с числом справа. */
    private static void drawHeader(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, String badgeText) {
        int hx = x + PAD;
        int hy = y + PAD;
        roundGradient(g, hx, hy, w - 2 * PAD, HEAD_H, 4, STRIP_TOP, STRIP_BOTTOM);
        g.item(icon, hx + 3, hy + 2);
        g.text(mc.font, "Алмазы", hx + 24, hy + 6, WHITE, true);

        int bw = mc.font.width(badgeText) + 10;
        int bx = x + w - PAD - 4 - bw;
        roundFill(g, bx, hy + 3, bw, 14, 7, 0x40FFFFFF);
        g.text(mc.font, badgeText, bx + 5, hy + 6, WHITE, true);
    }

    /**
     * Строка: [иконка] название ... значение, под ней слайдер с круглой белой ручкой.
     * velPx показывает, куда и как быстро едет ручка: за ней тянется светлый «хвост» (motion blur).
     */
    private static void drawRow(GuiGraphicsExtractor g, Minecraft mc, int x, int w, int rowY, ItemStack ic,
                                String name, int nameColor, String value, int valueColor,
                                float frac, float velPx, int accent, int smear) {
        int tx = x + 8;
        if (ic != null) {
            g.item(ic, x + 8, rowY + 1);
            tx = x + 28;
        }
        g.text(mc.font, name, tx, rowY + 5, nameColor, true);

        int vx = x + w - 8 - mc.font.width(value);
        if (smear != 0) {
            g.text(mc.font, value, vx, rowY + 5 + smear, withAlpha(valueColor, 0x44), false);
        }
        g.text(mc.font, value, vx, rowY + 5, valueColor, true);

        int x0 = x + 8;
        int x1 = x + w - 8;
        int trackW = x1 - x0;
        int ty = rowY + 22;
        int fw = Math.round(trackW * Math.max(0f, Math.min(1f, frac)));

        g.fill(x0, ty, x1, ty + 3, TRACK);
        if (fw > 0) g.fill(x0, ty, x0 + fw, ty + 3, accent);

        int kx = x0 + fw;
        int len = Math.min(14, Math.round(Math.abs(velPx)));
        if (len > 1) {
            if (velPx > 0) g.fill(Math.max(x0, kx - len), ty, kx, ty + 3, 0x66FFFFFF);
            else g.fill(kx, ty, Math.min(x1, kx + len), ty + 3, 0x66FFFFFF);
        }
        roundFill(g, kx - 4, ty - 3, 9, 9, 4, withAlpha(accent, 0x40)); // свечение
        roundFill(g, kx - 3, ty - 2, 7, 7, 3, WHITE);                  // ручка
    }

    private static int panelHeight(int rows, boolean footer) {
        return PAD + HEAD_H + 5 + rows * ROW_H + (footer ? FOOT_H : 0) + 3;
    }

    // ---------------------------------------------------------------- счётчик

    private static void drawCounter(GuiGraphicsExtractor g, Minecraft mc, DiamondConfig cfg, long now) {
        int invT = DiamondTracker.total;
        boolean showChest = DiamondTracker.chestsCount > 0;
        int chestT = showChest ? DiamondTracker.chestsSum : 0;
        int enderT = DiamondTracker.enderTotal;
        boolean showEnder = enderT >= 0;
        int sumT = invT + chestT + Math.max(0, enderT);

        // новая строка появляется с числом сразу, а слайдер «вырастает» с нуля
        if (showChest && !hadChest) {
            rChest = chestT;
            fChest = 0f;
        }
        if (showEnder && !hadEnder) {
            rEnder = enderT;
            fEnder = 0f;
        }
        hadChest = showChest;
        hadEnder = showEnder;

        rInv = roll(rInv, invT);
        rSum = roll(rSum, sumT);
        if (showChest) rChest = roll(rChest, chestT);
        if (showEnder) rEnder = roll(rEnder, enderT);

        float base = Math.max(1, sumT);
        fInv = smooth(fInv, invT / base, 8f);
        if (showChest) fChest = smooth(fChest, chestT / base, 8f);
        if (showEnder) fEnder = smooth(fEnder, enderT / base, 8f);

        // целевые размеры панели
        int rows = 1 + (showChest ? 1 : 0) + (showEnder ? 1 : 0);
        String footerText = "Сундуков в памяти: " + DiamondTracker.chestsCount;
        int nameW = mc.font.width("Инвентарь");
        int valW = mc.font.width(String.valueOf(invT));
        if (showChest) {
            nameW = Math.max(nameW, mc.font.width("Сундуки"));
            valW = Math.max(valW, mc.font.width(String.valueOf(chestT)));
        }
        if (showEnder) {
            nameW = Math.max(nameW, mc.font.width("Эндер-сундук"));
            valW = Math.max(valW, mc.font.width(String.valueOf(enderT)));
        }
        int rowsW = 28 + nameW + 14 + valW + 8;
        int headW = PAD + 24 + mc.font.width("Алмазы") + 14 + mc.font.width(String.valueOf(sumT)) + 10 + 4 + PAD;
        int footW = showChest ? 16 + mc.font.width(footerText) + 8 : 0;
        int wT = Math.max(124, Math.max(rowsW, Math.max(headW, footW)));
        int hT = panelHeight(rows, showChest);

        if (dispW < 0) {
            dispW = wT;
            dispH = hT;
        }
        dispW = snap(smooth(dispW, wT, 14f), wT);
        dispH = snap(smooth(dispH, hT, 14f), hT);

        slide = Math.min(1f, slide + dt / 0.5f);
        float ease = easeOut(slide);

        int w = Math.round(dispW);
        int h = Math.round(dispH);
        int x = cfg.hudX - Math.round((w + 12) * (1f - ease)); // выезжает слева
        int y = cfg.hudY;

        // шлейф за выезжающей панелью
        if (slide < 1f) {
            for (int j = 3; j >= 1; j--) {
                int a = (int) (0x30 * (1f - ease) * (4 - j) / 3f);
                if (a > 0) roundFill(g, x - j * 5, y, w, h, R, withAlpha(0xDDE4F0, a));
            }
        }

        drawGlass(g, x, y, w, h, BORDER);
        drawHeader(g, mc, x, y, w, String.valueOf(Math.round(rSum)));

        boolean flash = DiamondTracker.flashUntilMs > now;
        int flashColor = DiamondTracker.flashDir > 0 ? GREEN : RED;
        int invAccent = flash ? flashColor : ACCENT;
        int invValue = flash ? flashColor : WHITE;
        int trackW = w - 16;

        int rowY = y + PAD + HEAD_H + 5;
        if (rowY + 26 <= y + h) {
            drawRow(g, mc, x, w, rowY, icon, "Инвентарь", WHITE, String.valueOf(Math.round(rInv)), invValue,
                    fInv, (invT / base - fInv) * trackW, invAccent, smearOf(rInv, invT));
        }
        rowY += ROW_H;
        if (showChest) {
            if (rowY + 26 <= y + h) {
                drawRow(g, mc, x, w, rowY, chestIcon, "Сундуки", WHITE, String.valueOf(Math.round(rChest)), WHITE,
                        fChest, (chestT / base - fChest) * trackW, ACCENT, smearOf(rChest, chestT));
            }
            rowY += ROW_H;
        }
        if (showEnder) {
            if (rowY + 26 <= y + h) {
                drawRow(g, mc, x, w, rowY, enderIcon, "Эндер-сундук", WHITE, String.valueOf(Math.round(rEnder)), WHITE,
                        fEnder, (enderT / base - fEnder) * trackW, ACCENT, smearOf(rEnder, enderT));
            }
            rowY += ROW_H;
        }
        if (showChest && rowY + 12 <= y + h) {
            roundFill(g, x + 8, rowY + 5, 4, 4, 2, ACCENT);
            g.text(mc.font, footerText, x + 16, rowY + 2, MUTED, true);
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
        int w = 130;
        int h = panelHeight(1, false);
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
                    if (a > 0) roundFill(g, x, y + dir * j * 5, w, h, R, withAlpha(0xDDE4F0, a));
                }
            }

            drawGlass(g, x, y, w, h, withAlpha(accent, 0x70));
            drawHeader(g, mc, x, y, w, String.valueOf(t.total));

            String title = (gain ? "+" : "-") + Math.abs(t.delta) + " " + plural(t.delta);
            long leftMs = Math.max(0L, duration - age);
            float left = Math.max(0f, 1f - age / (float) duration);
            drawRow(g, mc, x, w, y + PAD + HEAD_H + 5, null, title, accent, mmss(leftMs), WHITE,
                    left, -6f, accent, 0);

            index++;
        }
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
