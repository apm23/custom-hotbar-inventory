package com.anjas.custominventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class InputDebounceTest {
    @Test
    void collapsesBounceButAllowsIntentionalLaterPress() {
        InputDebounce debounce = new InputDebounce(180_000_000L);
        assertTrue(debounce.tryAcquire(1_000_000_000L));
        assertFalse(debounce.tryAcquire(1_010_000_000L));
        assertFalse(debounce.tryAcquire(1_179_999_999L));
        assertTrue(debounce.tryAcquire(1_180_000_000L));
    }

    @Test
    void resetAllowsImmediateNextPress() {
        InputDebounce debounce = new InputDebounce(180_000_000L);
        assertTrue(debounce.tryAcquire(5_000L));
        assertFalse(debounce.tryAcquire(5_001L));
        debounce.reset();
        assertTrue(debounce.tryAcquire(5_001L));
    }

    @Test
    void rejectsNegativeInterval() {
        assertThrows(IllegalArgumentException.class, () -> new InputDebounce(-1));
    }
}
