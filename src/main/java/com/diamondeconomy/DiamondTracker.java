package com.diamondeconomy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Считает алмазы в инвентаре, в сундуках (запоминает каждый открытый сундук по координатам)
 * и в эндер-сундуке; превращает резкие изменения в тосты.
 */
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
    /** Алмазы в сундуке, который открыт прямо сейчас; -1, если ничего такого не открыто. */
    public static int chestTotal = -1;
    /** Сумма алмазов во всех запомненных сундуках (включая открытый сейчас). */
    public static int chestsSum = 0;
    /** Сколько сундуков запомнено. */
    public static int chestsCount = 0;
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

    // --- память о сундуках: ключ "измерение|x,y,z" -> алмазов внутри ---
    private static final Map<String, Integer> chests = new HashMap<>();
    private static String openKey = null;
    private static boolean chestWasOpen = false;
    private static int chestOpenTicks = 0;
    private static boolean chestsDirty = false;
    private static int validateTicks = 0;

    private static final Gson GSON = new Gson();
    private static final Type ENDER_TYPE = new TypeToken<Map<String, Integer>>() {}.getType();
    private static final Type CHESTS_TYPE = new TypeToken<Map<String, Map<String, Integer>>>() {}.getType();

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
            chestsSum = 0;
            chestsCount = 0;
            enderTotal = -1;
            chests.clear();
            openKey = null;
            chestWasOpen = false;
            chestOpenTicks = 0;
            chestsDirty = false;
            TOASTS.clear();
            return;
        }

        DiamondConfig cfg = DiamondConfig.get();
        int now = count(player);
        total = now;

        // --- контейнеры: считаем содержимое открытого окна (без слотов игрока) ---
        chestTotal = -1;
        boolean storage = false;
        boolean ender = false;
        boolean chestOpenNow = false;
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
                    chestOpenNow = true;
                    chestTotal = c;
                    if (!chestWasOpen) {
                        openKey = resolveKey(mc);
                        chestOpenTicks = 0;
                    }
                    chestOpenTicks++;
                    // первые тики содержимое может ещё не прийти с сервера, не затираем запись нулём
                    if (openKey != null && chestOpenTicks >= 3) {
                        if (c > 0) chests.put(openKey, c);
                        else chests.remove(openKey);
                        chestsDirty = true;
                    }
                }
            }
        }
        // эндер-сундук закрыли: запоминаем итог на диск
        if (enderOpen && !ender) saveEnder(mc);
        enderOpen = ender;
        // обычный сундук закрыли: запоминаем его содержимое на диск
        if (chestWasOpen && !chestOpenNow) {
            if (chestsDirty) saveChests(mc);
            openKey = null;
        }
        chestWasOpen = chestOpenNow;
        recomputeChests();

        if (!ready) {
            baseline = now;
            ready = true;
            if (!ender) enderTotal = loadEnder(mc);
            loadChests(mc);
            return;
        }

        // раз в пару секунд убираем из памяти сундуки, которых уже нет (сломаны)
        if (++validateTicks >= 40) {
            validateTicks = 0;
            validateChests(mc);
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

    // ------------------------------------------------- память о сундуках

    /**
     * Ключ сундука, на который ты смотришь в момент открытия окна. Двойной сундук всегда
     * сводится к одной половине, чтобы не считаться дважды. null, если блок определить не удалось.
     */
    private static String resolveKey(Minecraft mc) {
        if (mc.level == null) return null;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return null;
        BlockPos pos = hit.getBlockPos();
        BlockState st = mc.level.getBlockState(pos);
        if (st.getBlock() instanceof ChestBlock
                && st.hasProperty(ChestBlock.TYPE)
                && st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(st));
            if (other.asLong() < pos.asLong()) pos = other;
        }
        return String.valueOf(mc.level.dimension()) + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static void recomputeChests() {
        int sum = 0;
        for (int v : chests.values()) sum += v;
        int cnt = chests.size();
        // открытый сундук, который не удалось привязать к координатам, показываем хотя бы пока он открыт
        if (chestTotal >= 0 && openKey == null) {
            sum += chestTotal;
            cnt++;
        }
        chestsSum = sum;
        chestsCount = cnt;
    }

    /** Убирает из памяти сундуки, на месте которых уже нет сундука/бочки/шалкера. */
    private static void validateChests(Minecraft mc) {
        if (mc.level == null || chests.isEmpty()) return;
        String dim = String.valueOf(mc.level.dimension());
        boolean changed = false;
        Iterator<String> it = chests.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (key.equals(openKey)) continue;
            int bar = key.lastIndexOf('|');
            if (bar < 0 || !key.substring(0, bar).equals(dim)) continue;
            String[] p = key.substring(bar + 1).split(",");
            if (p.length != 3) continue;
            try {
                BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                if (!mc.level.hasChunkAt(pos)) continue;
                Block b = mc.level.getBlockState(pos).getBlock();
                if (!(b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock)) {
                    it.remove();
                    changed = true;
                }
            } catch (NumberFormatException e) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            recomputeChests();
            saveChests(mc);
        }
    }

    private static Path chestsPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("diamondeconomy-chests.json");
    }

    private static Map<String, Map<String, Integer>> readChestFile() {
        Path p = chestsPath();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                Map<String, Map<String, Integer>> m = GSON.fromJson(r, CHESTS_TYPE);
                if (m != null) return m;
            } catch (Exception e) {
                System.err.println("[diamondeconomy] Не удалось прочитать данные сундуков: " + e);
            }
        }
        return new HashMap<>();
    }

    private static void loadChests(Minecraft mc) {
        chests.clear();
        Map<String, Integer> m = readChestFile().get(serverKey(mc));
        if (m != null) chests.putAll(m);
        recomputeChests();
    }

    private static void saveChests(Minecraft mc) {
        Map<String, Map<String, Integer>> all = readChestFile();
        all.put(serverKey(mc), new HashMap<>(chests));
        try (Writer w = Files.newBufferedWriter(chestsPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(all, CHESTS_TYPE, w);
        } catch (Exception e) {
            System.err.println("[diamondeconomy] Не удалось сохранить данные сундуков: " + e);
        }
        chestsDirty = false;
    }

    // ------------------------------------------------- память об эндер-сундуке

    private static Path enderPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("diamondeconomy-ender.json");
    }

    /** Ключ: адрес сервера или "singleplayer", чтобы у разных серверов были свои числа. */
    private static String serverKey(Minecraft mc) {
        var server = mc.getCurrentServer();
        return server != null ? server.ip : "singleplayer";
    }

    private static Map<String, Integer> readEnderMap() {
        Path p = enderPath();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                Map<String, Integer> m = GSON.fromJson(r, ENDER_TYPE);
                if (m != null) return m;
            } catch (Exception e) {
                System.err.println("[diamondeconomy] Не удалось прочитать данные эндер-сундука: " + e);
            }
        }
        return new HashMap<>();
    }

    private static int loadEnder(Minecraft mc) {
        Integer v = readEnderMap().get(serverKey(mc));
        return v == null ? -1 : v;
    }

    private static void saveEnder(Minecraft mc) {
        if (enderTotal < 0) return;
        Map<String, Integer> m = readEnderMap();
        m.put(serverKey(mc), enderTotal);
        try (Writer w = Files.newBufferedWriter(enderPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(m, ENDER_TYPE, w);
        } catch (Exception e) {
            System.err.println("[diamondeconomy] Не удалось сохранить данные эндер-сундука: " + e);
        }
    }
}
