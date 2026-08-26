package com.anjas.custominventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Global operations over all 8 x 27 inventory slots. */
public final class InventoryAlgorithms {
    private InventoryAlgorithms() {}

    public static void mergeAll(ServerPlayer player) {
        List<ItemStack> before = collect(player);
        List<ItemStack> merged = merge(before);
        verifyConservation(before, merged, "merge");
        rewrite(player, merged, false);
    }

    public static void sortAll(ServerPlayer player) {
        List<ItemStack> before = collect(player);
        List<ItemStack> sorted = merge(before);
        verifyConservation(before, sorted, "sort-merge");

        sorted.sort(Comparator
                .comparingInt(InventoryAlgorithms::category)
                .thenComparing(s -> s.getItem().toString())
                .thenComparingInt(ItemStack::getCount));

        verifyConservation(before, sorted, "sort");
        rewrite(player, sorted, true);
    }

    private static List<ItemStack> collect(ServerPlayer player) {
        InventoryStorage.snapshotLive(player);
        ArrayList<ItemStack> all = new ArrayList<>(InventoryStorage.PAGE_COUNT * InventoryStorage.PAGE_SIZE);
        for (int page = 0; page < InventoryStorage.PAGE_COUNT; page++) {
            for (ItemStack stack : InventoryStorage.read(player, page)) {
                if (!stack.isEmpty()) all.add(stack.copy());
            }
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
                existing.grow(moved);
                remaining.shrink(moved);
            }
            if (!remaining.isEmpty()) out.add(remaining);
        }
        return out;
    }

    private static void rewrite(ServerPlayer player, List<ItemStack> stacks, boolean vertical) {
        int capacity = InventoryStorage.PAGE_COUNT * InventoryStorage.PAGE_SIZE;
        if (stacks.size() > capacity) {
            throw new IllegalStateException("Refusing to rewrite " + stacks.size() + " stacks into " + capacity + " slots");
        }

        int cursor = 0;
        for (int page = 0; page < InventoryStorage.PAGE_COUNT; page++) {
            ArrayList<ItemStack> target = new ArrayList<>(InventoryStorage.PAGE_SIZE);
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

    /** top-to-bottom in each column, then move to the next column. */
    static int verticalSlot(int logical) {
        if (logical < 0 || logical >= InventoryStorage.PAGE_SIZE) {
            throw new IllegalArgumentException("logical=" + logical);
        }
        return (logical % 3) * 9 + (logical / 3);
    }

    /**
     * Requested order: food -> tools/weapons/armor -> ores/ingots/valuables -> general blocks/items -> mob items.
     * Unknown and modded items deliberately stay in GENERAL rather than being guessed into a special group.
     */
    static int category(ItemStack stack) {
        String id = stack.getItem().toString().toLowerCase();

        if (stack.has(DataComponents.FOOD)
                || containsAny(id, "apple", "bread", "stew", "soup", "cookie", "cake", "carrot", "potato", "beef", "porkchop", "chicken", "mutton", "rabbit", "salmon", "cod", "melon", "berry")) {
            return 0;
        }

        if (containsAny(id,
                "sword", "axe", "pickaxe", "shovel", "hoe", "bow", "crossbow", "trident", "mace", "shield",
                "helmet", "chestplate", "leggings", "boots", "elytra", "fishing_rod", "shears", "flint_and_steel")) {
            return 1;
        }

        if (containsAny(id,
                "ingot", "nugget", "diamond", "emerald", "netherite", "raw_", "_ore", "ore_",
                "ancient_debris", "amethyst_shard", "lapis_lazuli", "quartz")) {
            return 2;
        }

        if (containsAny(id,
                "spawn_egg", "rotten_flesh", "bone", "gunpowder", "spider_eye", "ender_pearl", "blaze_rod",
                "ghast_tear", "slime_ball", "magma_cream", "phantom_membrane", "shulker_shell", "prismarine_shard")) {
            return 4;
        }

        return 3;
    }

    private static boolean containsAny(String id, String... needles) {
        for (String needle : needles) {
            if (id.contains(needle)) return true;
        }
        return false;
    }

    static void verifyConservation(List<ItemStack> before, List<ItemStack> after, String operation) {
        for (ItemStack stack : before) {
            if (stack == null || stack.isEmpty()) continue;
            int expected = countMatching(before, stack);
            int actual = countMatching(after, stack);
            if (expected != actual) {
                throw new IllegalStateException(operation + " would change item count for " + stack.getItem() + ": " + expected + " -> " + actual);
            }
        }
        for (ItemStack stack : after) {
            if (stack == null || stack.isEmpty()) continue;
            int expected = countMatching(before, stack);
            int actual = countMatching(after, stack);
            if (expected != actual) {
                throw new IllegalStateException(operation + " introduced or changed " + stack.getItem() + ": " + expected + " -> " + actual);
            }
        }
    }

    private static int countMatching(List<ItemStack> stacks, ItemStack probe) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, probe)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
