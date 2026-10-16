package com.diamondeconomy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Конфиг лежит в .minecraft/config/diamondeconomy.json. Изменения подхватываются после перезапуска игры. */
public class DiamondConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static DiamondConfig instance = new DiamondConfig();

    // --- HUD ---
    public boolean showHud = true;
    public int hudX = 6;
    public int hudY = 6;
    /** Размер панели слева сверху (меняется в режиме настройки, клавиша K по умолчанию). */
    public float hudScale = 1.0f;

    // --- Тосты ---
    public boolean showToasts = true;
    /** Минимальное суммарное изменение (в алмазах), чтобы показать тост. */
    public int minToastDiamonds = 5;
    /** Сколько тиков без изменений ждать, прежде чем показать итоговый тост (склеивает серии). */
    public int toastMergeTicks = 20;
    /** Сколько тиков тост висит на экране. */
    public int toastDurationTicks = 70;
    /** true: после закрытия сундука/инвентаря/торговли изменения не считаются сделкой. */
    public boolean silentAfterScreen = false;

    // --- Тултипы ---
    public boolean showTooltips = true;
    /** Цена в алмазах за 1 предмет: "minecraft:iron_ingot": 0.5 */
    public Map<String, Double> prices = new LinkedHashMap<>();

    public static DiamondConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("diamondeconomy.json");
    }

    public static void load() {
        Path p = path();
        boolean existed = Files.exists(p);
        if (existed) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                DiamondConfig loaded = GSON.fromJson(r, DiamondConfig.class);
                if (loaded != null) instance = loaded;
            } catch (Exception e) {
                System.err.println("[diamondeconomy] Не удалось прочитать конфиг: " + e);
            }
        }
        if (instance.prices == null) instance.prices = new LinkedHashMap<>();
        if (!existed) {
            instance.prices.put("minecraft:diamond", 1.0);
            instance.prices.put("minecraft:diamond_block", 9.0);
            instance.prices.put("minecraft:iron_ingot", 0.5);
            instance.prices.put("minecraft:gold_ingot", 1.0);
            instance.prices.put("minecraft:emerald", 2.0);
        }
        save();
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
            GSON.toJson(instance, w);
        } catch (Exception e) {
            System.err.println("[diamondeconomy] Не удалось сохранить конфиг: " + e);
        }
    }
}
