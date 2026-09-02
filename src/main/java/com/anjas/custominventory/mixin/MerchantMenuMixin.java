package com.anjas.custominventory.mixin;

import com.anjas.custominventory.CustomHotbarInventory;
import com.anjas.custominventory.InventoryStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces vanilla's incremental merchant autofill on the logical server with one transaction
 * across hotbar + all eight inventory pages. The full operation is simulated first so a missing
 * second cost can never partially consume/move the first cost. Once validated, payment slots are
 * filled like vanilla (up to one compatible stack), preserving post-trade change in the slot.
 */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {
    @Shadow @Final private Merchant trader;
    @Shadow @Final private MerchantContainer tradeContainer;

    @Inject(method = "tryMoveItems", at = @At("HEAD"), cancellable = true)
    private void custominventory$atomicPagedTradeFill(int newTradeIndex, CallbackInfo ci) {
        if (!(this.trader.getTradingPlayer() instanceof ServerPlayer player)) return;
        ci.cancel();

        if (newTradeIndex < 0 || newTradeIndex >= this.trader.getOffers().size()) return;

        InventoryStorage.snapshotLive(player);
        int activePage = InventoryStorage.active(player);
        List<ItemStack> working = custominventory$snapshotVirtualInventory(player);

        // First simulate returning anything already sitting in the two payment slots. If that
        // cannot fit, preserve the live menu exactly as it is instead of deleting/moving items.
        if (!custominventory$insertFully(working, this.tradeContainer.getItem(0).copy())) return;
        if (!custominventory$insertFully(working, this.tradeContainer.getItem(1).copy())) return;

        MerchantOffer offer = this.trader.getOffers().get(newTradeIndex);
        int requiredA = offer.getCostA().getCount(); // includes demand/reputation/special-price changes
        int requiredB = offer.getCostB().getCount();

        // Atomic preflight uses exact required amounts. Nothing live is touched here. This keeps
        // the previous safety fix: if A exists but B does not, repeated clicks consume/move zero.
        List<ItemStack> preflight = custominventory$copyStacks(working);
        if (custominventory$extractExact(preflight, offer.getItemCostA(), requiredA) == null) return;
        if (offer.getItemCostB().isPresent()
                && custominventory$extractExact(preflight, offer.getItemCostB().get(), requiredB) == null) return;

        // After validation, imitate vanilla autofill rather than extracting only the price. Vanilla
        // moves as much of the chosen compatible variant as fits in one payment stack. The trade
        // then shrinks only its price, leaving the remainder visibly in the payment slot as change.
        ItemStack paymentA = custominventory$extractPaymentStack(
                working, offer.getItemCostA(), requiredA);
        if (paymentA == null) return;

        ItemStack paymentB = ItemStack.EMPTY;
        if (offer.getItemCostB().isPresent()) {
            paymentB = custominventory$extractPaymentStack(
                    working, offer.getItemCostB().get(), requiredB);
            if (paymentB == null) return;
        }

        // All preconditions passed: this is the only point at which live state is mutated.
        custominventory$commitVirtualInventory(player, activePage, working);
        this.tradeContainer.setItem(0, paymentA);
        this.tradeContainer.setItem(1, paymentB);
        InventoryStorage.sync(player);
        CustomHotbarInventory.sendHiddenRecipeState(player);
    }

    /** hotbar (9) followed by page 1..8 (8 x 27). */
    @Unique
    private static List<ItemStack> custominventory$snapshotVirtualInventory(ServerPlayer player) {
        ArrayList<ItemStack> out = new ArrayList<>(9 + InventoryStorage.PAGE_COUNT * InventoryStorage.PAGE_SIZE);
        for (int slot = 0; slot < 9; slot++) out.add(player.getInventory().getItem(slot).copy());
        for (int page = 0; page < InventoryStorage.PAGE_COUNT; page++) {
            for (ItemStack stack : InventoryStorage.read(player, page)) out.add(stack.copy());
        }
        return out;
    }

    @Unique
    private static List<ItemStack> custominventory$copyStacks(List<ItemStack> source) {
        ArrayList<ItemStack> copy = new ArrayList<>(source.size());
        for (ItemStack stack : source) copy.add(stack.copy());
        return copy;
    }

    @Unique
    private static void custominventory$commitVirtualInventory(ServerPlayer player, int activePage, List<ItemStack> working) {
        for (int slot = 0; slot < 9; slot++) player.getInventory().setItem(slot, working.get(slot).copy());

        int offset = 9;
        for (int page = 0; page < InventoryStorage.PAGE_COUNT; page++) {
            ArrayList<ItemStack> pageStacks = new ArrayList<>(InventoryStorage.PAGE_SIZE);
            for (int index = 0; index < InventoryStorage.PAGE_SIZE; index++) {
                pageStacks.add(working.get(offset + page * InventoryStorage.PAGE_SIZE + index).copy());
            }
            if (page == activePage) InventoryStorage.loadLive(player, pageStacks);
            else InventoryStorage.write(player, page, pageStacks);
        }
        // Keep the attachment copy of the materialized page coherent as well.
        InventoryStorage.snapshotLive(player);
    }

    /** Simulates Inventory.placeItemBackInInventory semantics without touching live state. */
    @Unique
    private static boolean custominventory$insertFully(List<ItemStack> slots, ItemStack incoming) {
        if (incoming.isEmpty()) return true;

        for (ItemStack existing : slots) {
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, incoming)) continue;
            int space = existing.getMaxStackSize() - existing.getCount();
            if (space <= 0) continue;
            int moved = Math.min(space, incoming.getCount());
            existing.grow(moved);
            incoming.shrink(moved);
            if (incoming.isEmpty()) return true;
        }
        for (int i = 0; i < slots.size(); i++) {
            if (!slots.get(i).isEmpty()) continue;
            int moved = Math.min(incoming.getMaxStackSize(), incoming.getCount());
            slots.set(i, incoming.copyWithCount(moved));
            incoming.shrink(moved);
            if (incoming.isEmpty()) return true;
        }
        return false;
    }

    /** Extract exactly the required amount from one component-compatible variant for preflight. */
    @Unique
    private static ItemStack custominventory$extractExact(List<ItemStack> slots, ItemCost cost, int required) {
        if (required <= 0) return ItemStack.EMPTY;
        ItemStack representative = custominventory$findSatisfyingVariant(slots, cost, required);
        if (representative == null) return null;
        return custominventory$extractVariant(slots, cost, representative, required);
    }

    /**
     * Fill a merchant payment slot the vanilla way: once a satisfying variant is selected, move
     * as much of that variant as possible up to its normal max stack size. This deliberately moves
     * more than the price when available so MerchantResultSlot can leave the unspent remainder.
     */
    @Unique
    private static ItemStack custominventory$extractPaymentStack(List<ItemStack> slots, ItemCost cost, int required) {
        if (required <= 0) return ItemStack.EMPTY;
        ItemStack representative = custominventory$findSatisfyingVariant(slots, cost, required);
        if (representative == null) return null;

        int available = custominventory$countVariant(slots, cost, representative);
        int move = Math.min(representative.getMaxStackSize(), available);
        if (move < required) return null;
        return custominventory$extractVariant(slots, cost, representative, move);
    }

    @Unique
    private static ItemStack custominventory$findSatisfyingVariant(List<ItemStack> slots, ItemCost cost, int required) {
        for (ItemStack candidate : slots) {
            if (candidate.isEmpty() || !cost.test(candidate)) continue;
            if (custominventory$countVariant(slots, cost, candidate) >= required) return candidate.copy();
        }
        return null;
    }

    @Unique
    private static int custominventory$countVariant(List<ItemStack> slots, ItemCost cost, ItemStack representative) {
        int total = 0;
        for (ItemStack stack : slots) {
            if (!stack.isEmpty() && cost.test(stack)
                    && ItemStack.isSameItemSameComponents(representative, stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Unique
    private static ItemStack custominventory$extractVariant(
            List<ItemStack> slots, ItemCost cost, ItemStack representative, int amount) {
        int remaining = amount;
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty() || !cost.test(stack)
                    || !ItemStack.isSameItemSameComponents(representative, stack)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            if (stack.isEmpty()) slots.set(i, ItemStack.EMPTY);
        }
        return remaining == 0 ? representative.copyWithCount(amount) : null;
    }
}
