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

/** Server-authoritative storage for eight 27-slot inventory pages plus one alternate hotbar. */
public final class InventoryStorage {
    public static final int PAGE_COUNT = 8;
    public static final int PAGE_SIZE = 27;
    public static final int MAIN_START = 9;
    private static final String LEGACY_NAMESPACE = "doubleinv";

    @SuppressWarnings("unchecked")
    private static final AttachmentType<List<ItemStack>>[] PAGES = new AttachmentType[PAGE_COUNT];
    @SuppressWarnings("unchecked")
    private static final AttachmentType<List<ItemStack>>[] LEGACY_PAGES = new AttachmentType[PAGE_COUNT];
    public static AttachmentType<List<ItemStack>> ALT_HOTBAR;
    private static AttachmentType<List<ItemStack>> LEGACY_ALT_HOTBAR;
    private static AttachmentType<List<ItemStack>> LEGACY_HOTBAR_PAGE_2;
    public static AttachmentType<Integer> ACTIVE_PAGE;
    private static AttachmentType<Integer> LEGACY_ACTIVE_PAGE;
    public static AttachmentType<Boolean> BROWSING;

    private InventoryStorage() {}

    public static void register() {
        ALT_HOTBAR = persistentStacks(CustomHotbarInventory.MOD_ID, "alt_hotbar");
        LEGACY_ALT_HOTBAR = persistentStacks(LEGACY_NAMESPACE, "alt_hotbar");
        LEGACY_HOTBAR_PAGE_2 = persistentStacks(LEGACY_NAMESPACE, "hotbar_page_2");
        for (int i = 0; i < PAGE_COUNT; i++) {
            PAGES[i] = persistentStacks(CustomHotbarInventory.MOD_ID, "inventory_page_" + (i + 1));
            LEGACY_PAGES[i] = persistentStacks(LEGACY_NAMESPACE, "inventory_page_" + (i + 1));
        }
        ACTIVE_PAGE = AttachmentRegistry.createPersistent(id(CustomHotbarInventory.MOD_ID, "active_inventory_page"), Codec.intRange(0, PAGE_COUNT - 1));
        LEGACY_ACTIVE_PAGE = AttachmentRegistry.createPersistent(id(LEGACY_NAMESPACE, "active_inventory_page"), Codec.intRange(0, PAGE_COUNT - 1));
        BROWSING = AttachmentRegistry.createDefaulted(id(CustomHotbarInventory.MOD_ID, "inventory_browsing"), () -> false);
    }

    private static AttachmentType<List<ItemStack>> persistentStacks(String namespace, String path) {
        return AttachmentRegistry.createPersistent(id(namespace, path), ItemStack.OPTIONAL_CODEC.listOf());
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static AttachmentTarget target(ServerPlayer player) { return (AttachmentTarget) player; }

    /**
     * Imports non-empty hidden data from the old doubleinv namespace only when the corresponding
     * new slot is empty. Legacy attachments stay registered as a fallback, so migration never
     * destroys the only surviving copy of old hidden inventory data.
     */
    public static void migrateLegacy(ServerPlayer player) {
        AttachmentTarget target = target(player);
        int active = active(player);
        boolean migrated = false;

        for (int page = 0; page < PAGE_COUNT; page++) {
            List<ItemStack> legacy = normalizedCopy(target.getAttachedOrElse(LEGACY_PAGES[page], List.of()), PAGE_SIZE);
            if (!hasAnyItem(legacy) || hasAnyItem(read(player, page))) continue;

            if (page == active && hasAnyItem(liveCopy(player))) {
                // Vanilla already materialized a live page. Preserve both copies rather than risk duplication/loss.
                continue;
            }
            write(player, page, legacy);
            if (page == active) loadLive(player, legacy);
            migrated = true;
        }

        if (!hasAnyItem(readAltHotbar(player))) {
            List<ItemStack> legacyAlt = normalizedCopy(target.getAttachedOrElse(LEGACY_ALT_HOTBAR, List.of()), 9);
            if (!hasAnyItem(legacyAlt)) {
                legacyAlt = normalizedCopy(target.getAttachedOrElse(LEGACY_HOTBAR_PAGE_2, List.of()), 9);
            }
            if (hasAnyItem(legacyAlt)) {
                writeAltHotbar(player, legacyAlt);
                migrated = true;
            }
        }

        // Read the legacy active attachment so old worlds deserialize cleanly, but do not force a
        // page switch here: the live vanilla inventory is authoritative at login.
        target.getAttachedOrElse(LEGACY_ACTIVE_PAGE, 0);
        if (migrated) {
            sync(player);
            CustomHotbarInventory.LOGGER.info("Safely imported legacy doubleinv hidden inventory data for {}", player.getGameProfile().name());
        }
    }

    public static int active(ServerPlayer player) {
        Integer value = target(player).getAttachedOrElse(ACTIVE_PAGE, 0);
        return value == null || value < 0 || value >= PAGE_COUNT ? 0 : value;
    }
    public static boolean isBrowsing(ServerPlayer player) { return Boolean.TRUE.equals(target(player).getAttachedOrElse(BROWSING, false)); }
    public static void setBrowsing(ServerPlayer player, boolean browsing) { target(player).setAttached(BROWSING, browsing); }

    public static List<ItemStack> read(ServerPlayer player, int page) {
        validatePage(page);
        return normalizedCopy(target(player).getAttachedOrElse(PAGES[page], List.of()), PAGE_SIZE);
    }
    public static void write(ServerPlayer player, int page, List<ItemStack> stacks) {
        validatePage(page);
        target(player).setAttached(PAGES[page], List.copyOf(normalizedCopy(stacks, PAGE_SIZE)));
    }
    public static List<ItemStack> readAltHotbar(ServerPlayer player) { return normalizedCopy(target(player).getAttachedOrElse(ALT_HOTBAR, List.of()), 9); }
    public static void writeAltHotbar(ServerPlayer player, List<ItemStack> stacks) { target(player).setAttached(ALT_HOTBAR, List.copyOf(normalizedCopy(stacks, 9))); }
    public static void snapshotLive(ServerPlayer player) { write(player, active(player), liveCopy(player)); }

    public static List<ItemStack> liveCopy(ServerPlayer player) {
        Inventory inv = player.getInventory();
        ArrayList<ItemStack> out = new ArrayList<>(PAGE_SIZE);
        for (int i = 0; i < PAGE_SIZE; i++) out.add(inv.getItem(MAIN_START + i).copy());
        return out;
    }

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

    public static void cycle(ServerPlayer player) { switchPage(player, (active(player) + 1) % PAGE_COUNT); }

    public static void routeOverflow(ServerPlayer player) {
        if (isBrowsing(player) || player.containerMenu != player.inventoryMenu) return;
        List<ItemStack> live = liveCopy(player);
        if (hasEmptySlot(live)) return;
        int current = active(player);
        for (int page = 0; page < PAGE_COUNT; page++) {
            if (page != current && hasEmptySlot(read(player, page))) {
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

    private static boolean hasAnyItem(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) if (stack != null && !stack.isEmpty()) return true;
        return false;
    }

    public static void loadLive(ServerPlayer player, List<ItemStack> page) {
        Inventory inv = player.getInventory();
        List<ItemStack> safe = normalizedCopy(page, PAGE_SIZE);
        for (int i = 0; i < PAGE_SIZE; i++) inv.setItem(MAIN_START + i, safe.get(i).copy());
        inv.setChanged();
    }

    public static void copyState(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        snapshotLive(oldPlayer);
        for (int page = 0; page < PAGE_COUNT; page++) write(newPlayer, page, read(oldPlayer, page));
        writeAltHotbar(newPlayer, readAltHotbar(oldPlayer));
        target(newPlayer).setAttached(ACTIVE_PAGE, active(oldPlayer));
        setBrowsing(newPlayer, false);
    }

    public static void dropHiddenOnDeath(ServerPlayer player) {
        int materialized = active(player);
        for (int page = 0; page < PAGE_COUNT; page++) {
            if (page == materialized) continue;
            for (ItemStack stack : read(player, page)) if (!stack.isEmpty()) player.drop(stack.copy(), true, false);
        }
        for (ItemStack stack : readAltHotbar(player)) if (!stack.isEmpty()) player.drop(stack.copy(), true, false);
        clearStoredPages(player);
        writeAltHotbar(player, List.of());
    }

    public static void clearStoredPages(ServerPlayer player) {
        for (int page = 0; page < PAGE_COUNT; page++) write(player, page, List.of());
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
        if (page < 0 || page >= PAGE_COUNT) throw new IllegalArgumentException("page=" + page);
    }
}
