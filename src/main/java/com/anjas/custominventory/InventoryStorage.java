package com.anjas.custominventory;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative storage for eight 27-slot inventory pages plus one alternate hotbar.
 * The vanilla inventory is only the materialized active page. Every page change is transactional.
 */
public final class InventoryStorage {
    public static final int PAGE_COUNT = 8;
    public static final int PAGE_SIZE = 27;
    public static final int MAIN_START = 9;

    @SuppressWarnings("unchecked")
    private static final AttachmentType<List<ItemStack>>[] PAGES = new AttachmentType[PAGE_COUNT];
    public static AttachmentType<List<ItemStack>> ALT_HOTBAR;
    public static AttachmentType<Integer> ACTIVE_PAGE;
    public static AttachmentType<Boolean> BROWSING;

    private InventoryStorage() {}

    public static void register() {
        ALT_HOTBAR = persistentStacks("alt_hotbar");
        for (int i = 0; i < PAGE_COUNT; i++) {
            PAGES[i] = persistentStacks("inventory_page_" + (i + 1));
        }
        ACTIVE_PAGE = AttachmentRegistry.createPersistent(id("active_inventory_page"), Codec.intRange(0, PAGE_COUNT - 1));
        BROWSING = AttachmentRegistry.createDefaulted(id("inventory_browsing"), () -> false);
    }

    private static AttachmentType<List<ItemStack>> persistentStacks(String path) {
        return AttachmentRegistry.createPersistent(id(path), ItemStack.OPTIONAL_CODEC.listOf());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(CustomHotbarInventory.MOD_ID, path);
    }

    private static AttachmentTarget target(ServerPlayer player) {
        return (AttachmentTarget) player;
    }

    public static int active(ServerPlayer player) {
        Integer value = target(player).getAttachedOrElse(ACTIVE_PAGE, 0);
        return value == null || value < 0 || value >= PAGE_COUNT ? 0 : value;
    }

    public static boolean isBrowsing(ServerPlayer player) {
        return Boolean.TRUE.equals(target(player).getAttachedOrElse(BROWSING, false));
    }

    public static void setBrowsing(ServerPlayer player, boolean browsing) {
        target(player).setAttached(BROWSING, browsing);
    }

    public static List<ItemStack> read(ServerPlayer player, int page) {
        validatePage(page);
        List<ItemStack> raw = target(player).getAttachedOrElse(PAGES[page], List.of());
        return normalizedCopy(raw, PAGE_SIZE);
    }

    public static void write(ServerPlayer player, int page, List<ItemStack> stacks) {
        validatePage(page);
        target(player).setAttached(PAGES[page], List.copyOf(normalizedCopy(stacks, PAGE_SIZE)));
    }

    public static void snapshotLive(ServerPlayer player) {
        write(player, active(player), liveCopy(player));
    }

    public static List<ItemStack> liveCopy(ServerPlayer player) {
        Inventory inv = player.getInventory();
        ArrayList<ItemStack> out = new ArrayList<>(PAGE_SIZE);
        for (int i = 0; i < PAGE_SIZE; i++) {
            out.add(inv.getItem(MAIN_START + i).copy());
        }
        return out;
    }

    /**
     * Atomic page transaction: snapshot current live page, load target, update active page, full-sync.
     * Calling this for the current page is intentionally safe and simply refreshes/snapshots it.
     */
    public static void switchPage(ServerPlayer player, int targetPage) {
        validatePage(targetPage);
        int current = active(player);
        List<ItemStack> currentLive = liveCopy(player);
        List<ItemStack> next = current == targetPage ? currentLive : read(player, targetPage);

        write(player, current, currentLive);
        loadLive(player, next);
        target(player).setAttached(ACTIVE_PAGE, targetPage);
        sync(player);
    }

    public static void cycle(ServerPlayer player) {
        switchPage(player, (active(player) + 1) % PAGE_COUNT);
    }

    /**
     * Background overflow routing used only while the player is not browsing the inventory GUI and
     * is not inside another container. It never discards items: if no universal-capacity page exists,
     * it leaves the current page materialized and vanilla pickup simply waits on the ground.
     */
    public static void routeOverflow(ServerPlayer player) {
        if (isBrowsing(player)) return;
        if (player.containerMenu != player.inventoryMenu) return;

        List<ItemStack> live = liveCopy(player);
        if (hasEmptySlot(live)) return;

        int current = active(player);
        for (int page = 0; page < PAGE_COUNT; page++) {
            if (page == current) continue;
            if (hasEmptySlot(read(player, page))) {
                switchPage(player, page);
                return;
            }
        }
    }

    public static boolean hasEmptySlot(List<ItemStack> page) {
        for (int i = 0; i < PAGE_SIZE; i++) {
            ItemStack stack = i < page.size() && page.get(i) != null ? page.get(i) : ItemStack.EMPTY;
            if (stack.isEmpty()) return true;
        }
        return false;
    }

    public static void loadLive(ServerPlayer player, List<ItemStack> page) {
        Inventory inv = player.getInventory();
        List<ItemStack> safe = normalizedCopy(page, PAGE_SIZE);
        for (int i = 0; i < PAGE_SIZE; i++) {
            inv.setItem(MAIN_START + i, safe.get(i).copy());
        }
        inv.setChanged();
    }

    public static void clearStoredPages(ServerPlayer player) {
        for (int page = 0; page < PAGE_COUNT; page++) {
            write(player, page, List.of());
        }
        target(player).setAttached(ACTIVE_PAGE, 0);
        setBrowsing(player, false);
    }

    public static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.containerMenu.broadcastFullState();
    }

    private static List<ItemStack> normalizedCopy(List<ItemStack> source, int size) {
        ArrayList<ItemStack> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = i < source.size() && source.get(i) != null ? source.get(i) : ItemStack.EMPTY;
            out.add(stack.copy());
        }
        return out;
    }

    private static void validatePage(int page) {
        if (page < 0 || page >= PAGE_COUNT) {
            throw new IllegalArgumentException("page=" + page);
        }
    }
}
