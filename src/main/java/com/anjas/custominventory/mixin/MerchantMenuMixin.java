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
 * across hotbar + all eight inventory pages. Vanilla fills payment A and B independently, which
 * can leave a partial payment when the second cost is hidden or unavailable. We simulate the
 * entire operation first and commit only when both costs can be satisfied.
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
        ItemStack costAStack = offer.getCostA(); // includes demand/reputation/special-price changes
        ItemStack paymentA = custominventory$extractCost(
                working, offer.getItemCostA(), costAStack.getCount());
        if (paymentA == null) return;

        ItemStack paymentB = ItemStack.EMPTY;
        if (offer.getItemCostB().isPresent()) {
            ItemCost costB = offer.getItemCostB().get();
            paymentB = custominventory$extractCost(working, costB, offer.getCostB().getCount());
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

    /**
     * Simulates Inventory.placeItemBackInInventory semantics without touching live state.
     */
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

    /**
     * Finds one component-compatible variant that can satisfy the complete cost, then consumes it
     * from the working copy. Returning null means no mutation must be committed.
     */
    @Unique
    private static ItemStack custominventory$extractCost(List<ItemStack> slots, ItemCost cost, int required) {
        if (required <= 0) return ItemStack.EMPTY;

        ItemStack representative = null;
        for (ItemStack candidate : slots) {
            if (candidate.isEmpty() || !cost.test(candidate)) continue;
            int total = 0;
            for (ItemStack stack : slots) {
                if (!stack.isEmpty() && cost.test(stack) && ItemStack.isSameItemSameComponents(candidate, stack)) {
                    total += stack.getCount();
                    if (total >= required) break;
                }
            }
            if (total >= required) {
                representative = candidate.copyWithCount(required);
                break;
            }
        }
        if (representative == null) return null;

        int remaining = required;
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty() || !cost.test(stack) || !ItemStack.isSameItemSameComponents(representative, stack)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            if (stack.isEmpty()) slots.set(i, ItemStack.EMPTY);
        }
        return remaining == 0 ? representative : null;
    }
}
