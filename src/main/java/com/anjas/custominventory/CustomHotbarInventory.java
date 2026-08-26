package com.anjas.custominventory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class CustomHotbarInventory implements ModInitializer {
    public static final String MOD_ID = "custom_hotbar_inventory";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        InventoryStorage.register();
        InventoryAlgorithms.runStartupSelfTests();
        registerPayloads();
        registerReceivers();
        registerLifecycle();
        LOGGER.info("Custom Hotbar Inventory initialized");
    }

    private static void registerPayloads() {
        register(ModPayloads.CyclePage.TYPE, ModPayloads.CyclePage.CODEC);
        register(ModPayloads.SwapHotbar.TYPE, ModPayloads.SwapHotbar.CODEC);
        register(ModPayloads.SortAll.TYPE, ModPayloads.SortAll.CODEC);
        register(ModPayloads.MergeAll.TYPE, ModPayloads.MergeAll.CODEC);
        register(ModPayloads.BrowseOpen.TYPE, ModPayloads.BrowseOpen.CODEC);
        register(ModPayloads.BrowseClose.TYPE, ModPayloads.BrowseClose.CODEC);
        register(ModPayloads.DirectPage.P1.TYPE, ModPayloads.DirectPage.P1.CODEC);
        register(ModPayloads.DirectPage.P2.TYPE, ModPayloads.DirectPage.P2.CODEC);
        register(ModPayloads.DirectPage.P3.TYPE, ModPayloads.DirectPage.P3.CODEC);
        register(ModPayloads.DirectPage.P4.TYPE, ModPayloads.DirectPage.P4.CODEC);
        register(ModPayloads.DirectPage.P5.TYPE, ModPayloads.DirectPage.P5.CODEC);
        register(ModPayloads.DirectPage.P6.TYPE, ModPayloads.DirectPage.P6.CODEC);
        register(ModPayloads.DirectPage.P7.TYPE, ModPayloads.DirectPage.P7.CODEC);
        register(ModPayloads.DirectPage.P8.TYPE, ModPayloads.DirectPage.P8.CODEC);
    }

    private static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.BrowseOpen.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player.containerMenu == player.inventoryMenu) {
                InventoryStorage.setBrowsing(player, true);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.BrowseClose.TYPE, (payload, context) ->
                InventoryStorage.setBrowsing(context.player(), false));

        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.CyclePage.TYPE, (payload, context) -> {
            if (canManagePages(context.player())) InventoryStorage.cycle(context.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.SwapHotbar.TYPE, (payload, context) -> swapHotbar(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.SortAll.TYPE, (payload, context) -> {
            if (canManagePages(context.player())) InventoryAlgorithms.sortAll(context.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.MergeAll.TYPE, (payload, context) -> {
            if (canManagePages(context.player())) InventoryAlgorithms.mergeAll(context.player());
        });

        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P1.TYPE, (payload, context) -> direct(context.player(), 0));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P2.TYPE, (payload, context) -> direct(context.player(), 1));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P3.TYPE, (payload, context) -> direct(context.player(), 2));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P4.TYPE, (payload, context) -> direct(context.player(), 3));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P5.TYPE, (payload, context) -> direct(context.player(), 4));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P6.TYPE, (payload, context) -> direct(context.player(), 5));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P7.TYPE, (payload, context) -> direct(context.player(), 6));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P8.TYPE, (payload, context) -> direct(context.player(), 7));
    }

    private static void registerLifecycle() {
        ServerPlayerEvents.JOIN.register(player -> InventoryStorage.setBrowsing(player, false));
        ServerPlayerEvents.LEAVE.register(player -> {
            InventoryStorage.setBrowsing(player, false);
            InventoryStorage.snapshotLive(player);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                InventoryStorage.routeOverflow(player);
            }
        });
    }

    private static boolean canManagePages(ServerPlayer player) {
        return InventoryStorage.isBrowsing(player) && player.containerMenu == player.inventoryMenu;
    }

    private static void direct(ServerPlayer player, int page) {
        if (canManagePages(player)) {
            InventoryStorage.switchPage(player, page);
        }
    }

    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void register(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> type,
            net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
    }

    private static void swapHotbar(ServerPlayer player) {
        Inventory inv = player.getInventory();
        AttachmentTarget target = (AttachmentTarget) player;
        List<ItemStack> stored = target.getAttachedOrElse(InventoryStorage.ALT_HOTBAR, List.of());

        ArrayList<ItemStack> current = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            current.add(inv.getItem(i).copy());
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = i < stored.size() && stored.get(i) != null ? stored.get(i) : ItemStack.EMPTY;
            inv.setItem(i, stack.copy());
        }
        target.setAttached(InventoryStorage.ALT_HOTBAR, List.copyOf(current));
        InventoryStorage.sync(player);
    }
}
