package com.anjas.custominventory;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Optional TACZ gun-smith-table compatibility for the paged inventory. */
public final class TaczCraftingCompat {
    private static final String TACZ_MOD_ID = "tacz";

    private TaczCraftingCompat() {}

    /**
     * Replaces TACZ's normal workbench craft path when TACZ is present.
     * Returns true when the call belonged to a server player and was handled here.
     */
    public static boolean handleCraft(Object menu, Identifier recipeId, Player player) {
        if (!FabricLoader.getInstance().isModLoaded(TACZ_MOD_ID) || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        try {
            Method getRecipe = menu.getClass().getDeclaredMethod("getRecipe", Identifier.class);
            getRecipe.setAccessible(true);
            Object recipe = getRecipe.invoke(menu, recipeId);
            if (recipe == null) return true;

            Method getInputs = recipe.getClass().getMethod("getInputs");
            Method getOutput = recipe.getClass().getMethod("getOutput");
            @SuppressWarnings("unchecked")
            List<Object> inputs = (List<Object>) getInputs.invoke(recipe);
            ItemStack output = ((ItemStack) getOutput.invoke(recipe)).copy();
            if (output.isEmpty()) return true;

            PagedView view = new PagedView(serverPlayer);
            if (!serverPlayer.isCreative() && !reserveAndConsume(view, inputs)) {
                return true;
            }

            ItemStack leftover = insert(view, output);
            view.commit();
            InventoryStorage.sync(serverPlayer);
            CustomHotbarInventory.sendHiddenRecipeState(serverPlayer);

            // Preserve TACZ's old fallback semantics only when every virtual page is truly full.
            if (!leftover.isEmpty()) {
                ItemEntity entity = new ItemEntity(serverPlayer.level(), serverPlayer.getX(), serverPlayer.getY() + 0.5, serverPlayer.getZ(), leftover);
                entity.setPickUpDelay(0);
                serverPlayer.level().addFreshEntity(entity);
            }

            serverPlayer.inventoryMenu.broadcastFullState();
            sendCraftRefresh(menu, serverPlayer);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            CustomHotbarInventory.LOGGER.warn("TACZ workbench compatibility failed; falling back to TACZ's native crafting path", e);
            return false;
        }
    }

    private static boolean reserveAndConsume(PagedView view, List<Object> inputs) throws ReflectiveOperationException {
        int[] reserved = new int[view.size()];
        ArrayList<Ingredient> ingredients = new ArrayList<>(inputs.size());
        ArrayList<Integer> neededCounts = new ArrayList<>(inputs.size());

        // First pass is read-only: either every ingredient can be satisfied or nothing is changed.
        for (Object input : inputs) {
            Method getIngredient = input.getClass().getMethod("getIngredient");
            Method getCount = input.getClass().getMethod("getCount");
            Ingredient ingredient = (Ingredient) getIngredient.invoke(input);
            int needed = Math.max(0, ((Number) getCount.invoke(input)).intValue());
            if (ingredient == null) return false;
            ingredients.add(ingredient);
            neededCounts.add(needed);

            int remaining = needed;
            for (int slot : view.scanOrder()) {
                if (remaining <= 0) break;
                ItemStack stack = view.get(slot);
                if (stack.isEmpty() || !ingredient.test(stack)) continue;
                int available = Math.max(0, stack.getCount() - reserved[slot]);
                int take = Math.min(available, remaining);
                if (take > 0) {
                    reserved[slot] += take;
                    remaining -= take;
                }
            }
            if (remaining > 0) return false;
        }

        for (int slot = 0; slot < reserved.length; slot++) {
            int take = reserved[slot];
            if (take <= 0) continue;
            ItemStack stack = view.get(slot);
            stack.shrink(take);
            if (stack.isEmpty()) view.set(slot, ItemStack.EMPTY);
        }
        return true;
    }

    private static ItemStack insert(PagedView view, ItemStack input) {
        ItemStack remaining = input.copy();

        // Merge first, preferring the visible hotbar/current page before hidden pages.
        for (int slot : view.scanOrder()) {
            if (remaining.isEmpty()) break;
            ItemStack existing = view.get(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) continue;
            int room = existing.getMaxStackSize() - existing.getCount();
            if (room <= 0) continue;
            int moved = Math.min(room, remaining.getCount());
            existing.grow(moved);
            remaining.shrink(moved);
        }

        // Then use empty slots anywhere in the 8-page virtual inventory.
        for (int slot : view.scanOrder()) {
            if (remaining.isEmpty()) break;
            if (!view.get(slot).isEmpty()) continue;
            int moved = Math.min(remaining.getMaxStackSize(), remaining.getCount());
            ItemStack placed = remaining.copy();
            placed.setCount(moved);
            view.set(slot, placed);
            remaining.shrink(moved);
        }
        return remaining;
    }

    private static void sendCraftRefresh(Object menu, ServerPlayer player) {
        try {
            Field containerId = net.minecraft.world.inventory.AbstractContainerMenu.class.getField("containerId");
            int id = containerId.getInt(menu);
            Class<?> messageClass = Class.forName("com.tacz.guns.network.message.ServerMessageCraft");
            Constructor<?> ctor = messageClass.getConstructor(int.class);
            Object message = ctor.newInstance(id);
            if (!(message instanceof CustomPacketPayload payload)) return;
            Class<?> handler = Class.forName("com.tacz.guns.network.NetworkHandler");
            Method send = handler.getMethod("sendToClientPlayer", CustomPacketPayload.class, ServerPlayer.class);
            send.invoke(null, payload, player);
        } catch (ReflectiveOperationException | LinkageError e) {
            CustomHotbarInventory.LOGGER.debug("Could not send TACZ workbench refresh packet", e);
        }
    }

    /** Hotbar + current page + the seven hidden pages, with hidden-page writes committed atomically. */
    private static final class PagedView {
        private final ServerPlayer player;
        private final int activePage;
        private final List<List<ItemStack>> pages = new ArrayList<>(InventoryStorage.PAGE_COUNT);
        private final int[] scanOrder;

        private PagedView(ServerPlayer player) {
            this.player = player;
            InventoryStorage.snapshotLive(player);
            this.activePage = InventoryStorage.active(player);
            for (int page = 0; page < InventoryStorage.PAGE_COUNT; page++) {
                pages.add(new ArrayList<>(InventoryStorage.read(player, page)));
            }

            this.scanOrder = new int[size()];
            int cursor = 0;
            for (int hotbar = 0; hotbar < 9; hotbar++) scanOrder[cursor++] = hotbar;
            for (int i = 0; i < InventoryStorage.PAGE_SIZE; i++) scanOrder[cursor++] = pageSlot(activePage, i);
            for (int page = 0; page < InventoryStorage.PAGE_COUNT; page++) {
                if (page == activePage) continue;
                for (int i = 0; i < InventoryStorage.PAGE_SIZE; i++) scanOrder[cursor++] = pageSlot(page, i);
            }
        }

        private int size() { return 9 + InventoryStorage.PAGE_COUNT * InventoryStorage.PAGE_SIZE; }
        private int[] scanOrder() { return scanOrder; }
        private int pageSlot(int page, int index) { return 9 + page * InventoryStorage.PAGE_SIZE + index; }

        private ItemStack get(int slot) {
            if (slot < 0 || slot >= size()) return ItemStack.EMPTY;
            if (slot < 9) return player.getInventory().getItem(slot);
            int linear = slot - 9;
            int page = linear / InventoryStorage.PAGE_SIZE;
            int index = linear % InventoryStorage.PAGE_SIZE;
            if (page == activePage) return player.getInventory().getItem(InventoryStorage.MAIN_START + index);
            return pages.get(page).get(index);
        }

        private void set(int slot, ItemStack stack) {
            if (slot < 0 || slot >= size()) return;
            if (slot < 9) {
                player.getInventory().setItem(slot, stack);
                return;
            }
            int linear = slot - 9;
            int page = linear / InventoryStorage.PAGE_SIZE;
            int index = linear % InventoryStorage.PAGE_SIZE;
            if (page == activePage) player.getInventory().setItem(InventoryStorage.MAIN_START + index, stack);
            else pages.get(page).set(index, stack);
        }

        private void commit() {
            for (int page = 0; page < InventoryStorage.PAGE_COUNT; page++) {
                if (page != activePage) InventoryStorage.write(player, page, pages.get(page));
            }
            InventoryStorage.snapshotLive(player);
            player.getInventory().setChanged();
        }
    }
}
