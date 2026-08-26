package com.anjas.custominventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class InventoryAlgorithms {
    private InventoryAlgorithms() {}

    public static void mergeAll(ServerPlayer player) {
        rewrite(player, merge(collect(player)), false);
    }

    public static void sortAll(ServerPlayer player) {
        List<ItemStack> stacks = merge(collect(player));
        stacks.sort(Comparator.comparingInt(InventoryAlgorithms::category)
                .thenComparing(s -> s.getItem().toString()));
        rewrite(player, stacks, true);
    }

    private static List<ItemStack> collect(ServerPlayer player) {
        InventoryStorage.snapshotLive(player);
        ArrayList<ItemStack> all = new ArrayList<>(InventoryStorage.PAGE_COUNT * InventoryStorage.PAGE_SIZE);
        for (int p = 0; p < InventoryStorage.PAGE_COUNT; p++) {
            for (ItemStack stack : InventoryStorage.read(player, p)) if (!stack.isEmpty()) all.add(stack.copy());
        }
        return all;
    }

    static List<ItemStack> merge(List<ItemStack> input) {
        ArrayList<ItemStack> out = new ArrayList<>();
        for (ItemStack original : input) {
            if (original == null || original.isEmpty()) continue;
            ItemStack remaining = original.copy();
            for (ItemStack existing : out) {
                if (remaining.isEmpty()) break;
                if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue;
                int room = existing.getMaxStackSize() - existing.getCount();
                if (room <= 0) continue;
                int moved = Math.min(room, remaining.getCount());
                existing.setCount(existing.getCount() + moved);
                remaining.setCount(remaining.getCount() - moved);
            }
            if (!remaining.isEmpty()) out.add(remaining);
        }
        return out;
    }

    private static void rewrite(ServerPlayer player, List<ItemStack> stacks, boolean vertical) {
        int cursor = 0;
        for (int page = 0; page < InventoryStorage.PAGE_COUNT; page++) {
            ArrayList<ItemStack> target = new ArrayList<>();
            for (int i = 0; i < InventoryStorage.PAGE_SIZE; i++) target.add(ItemStack.EMPTY);
            for (int logical = 0; logical < InventoryStorage.PAGE_SIZE && cursor < stacks.size(); logical++) {
                int slot = vertical ? verticalSlot(logical) : logical;
                target.set(slot, stacks.get(cursor++).copy());
            }
            InventoryStorage.write(player, page, target);
        }
        InventoryStorage.loadLive(player, InventoryStorage.read(player, InventoryStorage.active(player)));
        InventoryStorage.sync(player);
    }

    // top-to-bottom in each column, then next column
    static int verticalSlot(int logical) { return (logical % 3) * 9 + (logical / 3); }

    // Stable coarse categories. Unknown/modded items intentionally fall into GENERAL.
    private static int category(ItemStack stack) {
        String id = stack.getItem().toString().toLowerCase();
        if (id.contains("food") || id.contains("apple") || id.contains("bread") || id.contains("meat") || id.contains("stew")) return 0;
        if (id.contains("sword") || id.contains("axe") || id.contains("pickaxe") || id.contains("shovel") || id.contains("hoe") || id.contains("bow") || id.contains("crossbow") || id.contains("trident") || id.contains("helmet") || id.contains("chestplate") || id.contains("leggings") || id.contains("boots")) return 1;
        if (id.contains("ingot") || id.contains("diamond") || id.contains("emerald") || id.contains("netherite") || id.contains("raw_") || id.contains("ore")) return 2;
        if (id.contains("spawn_egg") || id.contains("rotten_flesh") || id.contains("bone") || id.contains("gunpowder") || id.contains("spider_eye") || id.contains("ender_pearl")) return 3;
        return 4;
    }
}
