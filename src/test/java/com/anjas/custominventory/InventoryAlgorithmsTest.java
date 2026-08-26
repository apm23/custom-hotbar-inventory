package com.anjas.custominventory;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class InventoryAlgorithmsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.bootStrap();
    }

    @Test
    void verticalSlotIsPermutationAndColumnMajor() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < InventoryStorage.PAGE_SIZE; i++) {
            int slot = InventoryAlgorithms.verticalSlot(i);
            assertTrue(slot >= 0 && slot < InventoryStorage.PAGE_SIZE);
            assertTrue(seen.add(slot), "duplicate physical slot " + slot);
        }
        assertEquals(27, seen.size());
        assertEquals(0, InventoryAlgorithms.verticalSlot(0));
        assertEquals(9, InventoryAlgorithms.verticalSlot(1));
        assertEquals(18, InventoryAlgorithms.verticalSlot(2));
        assertEquals(1, InventoryAlgorithms.verticalSlot(3));
        assertEquals(26, InventoryAlgorithms.verticalSlot(26));
    }

    @Test
    void mergePreservesCountsAndStackLimit() {
        List<ItemStack> before = List.of(
                new ItemStack(Items.STONE, 30),
                new ItemStack(Items.STONE, 40),
                new ItemStack(Items.DIRT, 11)
        );
        List<ItemStack> after = InventoryAlgorithms.merge(before);

        InventoryAlgorithms.verifyConservation(before, after, "unit-test");
        int stone = after.stream().filter(s -> s.is(Items.STONE)).mapToInt(ItemStack::getCount).sum();
        int dirt = after.stream().filter(s -> s.is(Items.DIRT)).mapToInt(ItemStack::getCount).sum();
        assertEquals(70, stone);
        assertEquals(11, dirt);
        assertTrue(after.stream().allMatch(s -> s.getCount() <= s.getMaxStackSize()));
        assertEquals(3, after.size(), "70 stone requires two stacks plus one dirt stack");
    }

    @Test
    void differentComponentsDoNotMerge() {
        ItemStack pristine = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
        damaged.setDamageValue(1);

        List<ItemStack> after = InventoryAlgorithms.merge(List.of(pristine, damaged));
        assertEquals(2, after.size());
        assertFalse(ItemStack.isSameItemSameComponents(after.get(0), after.get(1)));
    }

    @Test
    void requestedCategoryOrderIsStable() {
        assertEquals(0, InventoryAlgorithms.category(new ItemStack(Items.BREAD)));
        assertEquals(1, InventoryAlgorithms.category(new ItemStack(Items.DIAMOND_PICKAXE)));
        assertEquals(2, InventoryAlgorithms.category(new ItemStack(Items.DIAMOND)));
        assertEquals(3, InventoryAlgorithms.category(new ItemStack(Items.REDSTONE)));
        assertEquals(4, InventoryAlgorithms.category(new ItemStack(Items.ROTTEN_FLESH)));
    }

    @Test
    void conservationCheckRejectsLossAndDuplication() {
        List<ItemStack> before = List.of(new ItemStack(Items.IRON_INGOT, 10));
        assertThrows(IllegalStateException.class,
                () -> InventoryAlgorithms.verifyConservation(before, List.of(new ItemStack(Items.IRON_INGOT, 9)), "loss"));
        assertThrows(IllegalStateException.class,
                () -> InventoryAlgorithms.verifyConservation(before, List.of(new ItemStack(Items.IRON_INGOT, 11)), "dupe"));
    }

    @Test
    void mergeDoesNotMutateInputStacks() {
        ItemStack a = new ItemStack(Items.COBBLESTONE, 32);
        ItemStack b = new ItemStack(Items.COBBLESTONE, 32);
        List<ItemStack> input = new ArrayList<>(List.of(a, b));
        InventoryAlgorithms.merge(input);
        assertEquals(32, a.getCount());
        assertEquals(32, b.getCount());
    }
}
