package com.diamondeconomy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Считает алмазы в инвентаре, в открытом сундуке и в эндер-сундуке; превращает резкие изменения в тосты. */
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

    /** Алмазы в инвентаре (блок = 9). */
    public static int total = 0;
    /** Алмазы в открытом сундуке/бочке/шалкере; -1, если ничего такого не открыто. */
    public static int chestTotal = -1;
    /** Алмазы в эндер-сундуке; -1, пока ты его ни разу не открывал. Запоминается между запусками. */
    public static int enderTotal = -1;

    public static final List<Toast> TOASTS = new ArrayList<>();
    /** Для подсветки счётчика: до какого момента (мс) мигать и в какую сторону. */
    public static long flashUntilMs = 0;
    public static int flashDir = 0;

    private static boolean ready = false;
    private static int baseline = 0;
    private static int pending = 0;
    private static int quietTicks = 0;
    private static boolean screenWasOpen = false;
    private static boolean screenWasStorage = false;
    private static boolean enderOpen = false;

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Integer>>() {}.getType();

    private DiamondTracker() {}

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            ready = false;
            pending = 0;
            quietTicks = 0;
            screenWasOpen = false;
            screenWasStorage = false;
            enderOpen = false;
            total = 0;
            chestTotal = -1;
            enderTotal = -1;
            TOASTS.clear();
            return;
        }

        DiamondConfig cfg = DiamondConfig.get();
        int now = count(player);
        total = now;

        // --- сундуки: считаем содержимое открытого окна (без слотов игрока) ---
        chestTotal = -1;
        boolean storage = false;
        boolean ender = false;
        var screen = mc.gui.screen();
        if (screen instanceof AbstractContainerScreen<?> cs) {
            AbstractContainerMenu menu = cs.getMenu();
            if (menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu) {
                storage = true;
                int c = countContainer(menu);
                if (isEnderTitle(cs.getTitle())) {
                    ender = true;
                    enderTotal = c;
                } else {
                    chestTotal = c;
                }
            }
        }
        // эндер-сундук закрыли: запоминаем итог на диск
        if (enderOpen && !ender) saveEnder(mc);
        enderOpen = ender;

        if (!ready) {
            baseline = now;
            ready = true;
            if (!ender) enderTotal = loadEnder(mc);
            return;
        }

        // Пока открыт любой контейнер, изменения не фиксируем: ждём закрытия окна
        if (screen instanceof AbstractContainerScreen<?>) {
            screenWasOpen = true;
            if (storage) screenWasStorage = true;
            return;
        }
        if (screenWasOpen) {
            screenWasOpen = false;
            // перекладывание в сундук/из сундука это не сделка, тост не нужен
            boolean silent = cfg.silentAfterScreen || screenWasStorage;
            screenWasStorage = false;
            if (silent) {
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

    // ------------------------------------------------------------- подсчёт

    private static int value(ItemStack s) {
        if (s.isEmpty()) return 0;
        if (s.is(Items.DIAMOND)) return s.getCount();
        if (s.is(Items.DIAMOND_BLOCK)) return s.getCount() * 9;
        return 0;
    }

    private static int count(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            n += value(inv.getItem(i));
        }
        return n;
    }

    /** Алмазы только в слотах самого контейнера (слоты инвентаря игрока пропускаем). */
    private static int countContainer(AbstractContainerMenu menu) {
        int n = 0;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) continue;
            n += value(slot.getItem());
        }
        return n;
    }

    /** Заголовок окна совпадает с названием эндер-сундука на текущем языке игры. */
    private static boolean isEnderTitle(Component title) {
        return title.getString().equals(Component.translatable("container.enderchest").getString());
    }

    // ------------------------------------------------- память об эндер-сундуке

    private static Path enderPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("diamondeconomy-ender.json");
    }

    /** Ключ: адрес сервера или "singleplayer", чтобы у разных серверов были свои числа. */
    private static String enderKey(Minecraft mc) {
        var server = mc.getCurrentServer();
        return server != null ? server.ip : "singleplayer";
    }

    private static Map<String, Integer> readEnderMap() {
        Path p = enderPath();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                Map<String, Integer> m = GSON.fromJson(r, MAP_TYPE);
                if (m != null) return m;
            } catch (Exception e) {
                System.err.println("[diamondeconomy] Не удалось прочитать данные эндер-сундука: " + e);
            }
        }
        return new HashMap<>();
    }

    private static int loadEnder(Minecraft mc) {
        Integer v = readEnderMap().get(enderKey(mc));
        return v == null ? -1 : v;
    }

    private static void saveEnder(Minecraft mc) {
        if (enderTotal < 0) return;
        Map<String, Integer> m = readEnderMap();
        m.put(enderKey(mc), enderTotal);
        try (Writer w = Files.newBufferedWriter(enderPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(m, MAP_TYPE, w);
        } catch (Exception e) {
            System.err.println("[diamondeconomy] Не удалось сохранить данные эндер-сундука: " + e);
        }
    }
}
