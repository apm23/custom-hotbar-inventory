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

/** Server-authoritative storage. All page mutations go through this class. */
public final class InventoryStorage {
    public static final int PAGE_COUNT = 8;
    public static final int PAGE_SIZE = 27;
    public static final int MAIN_START = 9;

    @SuppressWarnings("unchecked")
    private static final AttachmentType<List<ItemStack>>[] PAGES = new AttachmentType[PAGE_COUNT];
    public static AttachmentType<List<ItemStack>> ALT_HOTBAR;
    public static AttachmentType<Integer> ACTIVE_PAGE;

    private InventoryStorage() {}

    public static void register() {
        ALT_HOTBAR = persistentStacks("alt_hotbar");
        for (int i = 0; i < PAGE_COUNT; i++) PAGES[i] = persistentStacks("inventory_page_" + (i + 1));
        ACTIVE_PAGE = AttachmentRegistry.createPersistent(id("active_inventory_page"), Codec.intRange(0, PAGE_COUNT - 1));
    }

    private static AttachmentType<List<ItemStack>> persistentStacks(String path) {
        return AttachmentRegistry.createPersistent(id(path), ItemStack.OPTIONAL_CODEC.listOf());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(CustomHotbarInventory.MOD_ID, path);
    }

    private static AttachmentTarget target(ServerPlayer player) { return (AttachmentTarget) player; }

    public static int active(ServerPlayer player) {
        Integer value = target(player).getAttachedOrElse(ACTIVE_PAGE, 0);
        return value == null || value < 0 || value >= PAGE_COUNT ? 0 : value;
    }

    public static List<ItemStack> read(ServerPlayer player, int page) {
        validatePage(page);
        List<ItemStack> raw = target(player).getAttachedOrElse(PAGES[page], List.of());
        ArrayList<ItemStack> out = new ArrayList<>(PAGE_SIZE);
        for (int i = 0; i < PAGE_SIZE; i++) {
            ItemStack stack = i < raw.size() && raw.get(i) != null ? raw.get(i) : ItemStack.EMPTY;
            out.add(stack.copy());
        }
        return out;
    }

    public static void write(ServerPlayer player, int page, List<ItemStack> stacks) {
        validatePage(page);
        ArrayList<ItemStack> copy = new ArrayList<>(PAGE_SIZE);
        for (int i = 0; i < PAGE_SIZE; i++) {
            ItemStack stack = i < stacks.size() && stacks.get(i) != null ? stacks.get(i) : ItemStack.EMPTY;
            copy.add(stack.copy());
        }
        target(player).setAttached(PAGES[page], List.copyOf(copy));
    }

    public static void snapshotLive(ServerPlayer player) {
        write(player, active(player), liveCopy(player));
    }

    public static List<ItemStack> liveCopy(ServerPlayer player) {
        Inventory inv = player.getInventory();
        ArrayList<ItemStack> out = new ArrayList<>(PAGE_SIZE);
        for (int i = 0; i < PAGE_SIZE; i++) out.add(inv.getItem(MAIN_START + i).copy());
        return out;
    }

    /** Atomic page transaction: save current -> load target -> set active -> full sync. */
    public static void switchPage(ServerPlayer player, int targetPage) {
        validatePage(targetPage);
        int current = active(player);
        List<ItemStack> currentLive = liveCopy(player);
        List<ItemStack> target = current == targetPage ? currentLive : read(player, targetPage);
        write(player, current, currentLive);
        loadLive(player, target);
        target(player).setAttached(ACTIVE_PAGE, targetPage);
        sync(player);
    }

    public static void cycle(ServerPlayer player) {
        switchPage(player, (active(player) + 1) % PAGE_COUNT);
    }

    public static void loadLive(ServerPlayer player, List<ItemStack> page) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < PAGE_SIZE; i++) {
            ItemStack stack = i < page.size() && page.get(i) != null ? page.get(i) : ItemStack.EMPTY;
            inv.setItem(MAIN_START + i, stack.copy());
        }
        inv.setChanged();
    }

    public static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.containerMenu.broadcastFullState();
    }

    private static void validatePage(int page) {
        if (page < 0 || page >= PAGE_COUNT) throw new IllegalArgumentException("page=" + page);
    }
}
