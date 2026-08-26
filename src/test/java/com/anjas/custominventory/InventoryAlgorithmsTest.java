package com.anjas.custominventory;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JVM invariants only. Minecraft registry-dependent ItemStack tests run from the mod startup
 * self-test after vanilla registries/components are fully bound.
 */
final class InventoryAlgorithmsTest {
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
    void verticalSlotRejectsInvalidIndices() {
        assertThrows(IllegalArgumentException.class, () -> InventoryAlgorithms.verticalSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> InventoryAlgorithms.verticalSlot(27));
    }
}
