package com.diamondeconomy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Добавляет в описание предмета цену в алмазах из конфига. */
public final class PriceTooltip {
    private PriceTooltip() {}

    public static void onTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        DiamondConfig cfg = DiamondConfig.get();
        if (!cfg.showTooltips || stack.isEmpty()) return;

        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Double price = cfg.prices.get(id);
        if (price == null) return;

        String line = "Цена: " + fmt(price) + " алм.";
        int count = stack.getCount();
        if (count > 1) {
            line += "  |  x" + count + " = " + fmt(price * count) + " алм.";
        }
        lines.add(Component.literal(line).withStyle(ChatFormatting.AQUA));
    }

    private static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        // убираем лишние нули: 0.50 -> 0.5
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
