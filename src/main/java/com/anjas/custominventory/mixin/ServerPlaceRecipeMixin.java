package com.anjas.custominventory.mixin;

import com.anjas.custominventory.CustomHotbarInventory;
import com.anjas.custominventory.InventoryStorage;
import net.minecraft.core.Holder;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeMixin {
    @Shadow @Final private Inventory inventory;
    @Unique private boolean custominventory$hiddenDirty;

    @Redirect(
        method = "placeRecipe(Lnet/minecraft/recipebook/ServerPlaceRecipe$CraftingMenuAccess;IILjava/util/List;Ljava/util/List;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/crafting/RecipeHolder;ZZ)Lnet/minecraft/world/inventory/RecipeBookMenu$PostPlaceAction;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;fillStackedContents(Lnet/minecraft/world/entity/player/StackedItemContents;)V")
    )
    private static void custominventory$includeHiddenPages(Inventory inventory, StackedItemContents contents) {
        inventory.fillStackedContents(contents);
        if (!(inventory.player instanceof ServerPlayer player)) return;
        InventoryStorage.snapshotLive(player);
        int active = InventoryStorage.active(player);
        for (int page = 0; page < InventoryStorage.PAGE_COUNT; page++) {
            if (page == active) continue;
            for (ItemStack stack : InventoryStorage.read(player, page)) contents.accountSimpleStack(stack);
        }
    }

    @Inject(method = "moveItemToGrid", at = @At("HEAD"), cancellable = true)
    private void custominventory$takeFromHiddenPage(Slot targetSlot, Holder<Item> item, int count, CallbackInfoReturnable<Integer> cir) {
        ItemStack target = targetSlot.getItem();
        if (this.inventory.findSlotMatchingCraftingIngredient(item, target) != -1) return;
        if (!(this.inventory.player instanceof ServerPlayer player)) return;

        int active = InventoryStorage.active(player);
        for (int page = 0; page < InventoryStorage.PAGE_COUNT; page++) {
            if (page == active) continue;
            List<ItemStack> stored = new ArrayList<>(InventoryStorage.read(player, page));
            for (int slot = 0; slot < stored.size(); slot++) {
                ItemStack candidate = stored.get(slot);
                if (candidate.isEmpty() || !candidate.is(item) || !Inventory.isUsableForCrafting(candidate)) continue;
                if (!target.isEmpty() && !ItemStack.isSameItemSameComponents(target, candidate)) continue;

                int takenCount = Math.min(count, candidate.getCount());
                ItemStack taken = candidate.copyWithCount(takenCount);
                candidate.shrink(takenCount);
                stored.set(slot, candidate.isEmpty() ? ItemStack.EMPTY : candidate);
                InventoryStorage.write(player, page, stored);

                if (target.isEmpty()) targetSlot.set(taken);
                else target.grow(takenCount);

                this.custominventory$hiddenDirty = true;
                cir.setReturnValue(count - takenCount);
                return;
            }
        }
    }

    @Inject(method = "tryPlaceRecipe", at = @At("RETURN"))
    private void custominventory$flushHiddenChanges(RecipeHolder<?> recipe, StackedItemContents availableItems, CallbackInfoReturnable<RecipeBookMenu.PostPlaceAction> cir) {
        if (!this.custominventory$hiddenDirty) return;
        this.custominventory$hiddenDirty = false;
        if (!(this.inventory.player instanceof ServerPlayer player)) return;
        InventoryStorage.sync(player);
        CustomHotbarInventory.sendHiddenRecipeState(player);
    }
}
