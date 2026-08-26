package com.anjas.custominventory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CustomHotbarInventory implements ModInitializer {
    public static final String MOD_ID = "custom_hotbar_inventory";
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override public void onInitialize() {
        InventoryStorage.register();
        register(ModPayloads.CyclePage.TYPE, ModPayloads.CyclePage.CODEC); register(ModPayloads.SwapHotbar.TYPE, ModPayloads.SwapHotbar.CODEC);
        register(ModPayloads.SortAll.TYPE, ModPayloads.SortAll.CODEC); register(ModPayloads.MergeAll.TYPE, ModPayloads.MergeAll.CODEC);
        register(ModPayloads.DirectPage.P1.TYPE, ModPayloads.DirectPage.P1.CODEC); register(ModPayloads.DirectPage.P2.TYPE, ModPayloads.DirectPage.P2.CODEC);
        register(ModPayloads.DirectPage.P3.TYPE, ModPayloads.DirectPage.P3.CODEC); register(ModPayloads.DirectPage.P4.TYPE, ModPayloads.DirectPage.P4.CODEC);
        register(ModPayloads.DirectPage.P5.TYPE, ModPayloads.DirectPage.P5.CODEC); register(ModPayloads.DirectPage.P6.TYPE, ModPayloads.DirectPage.P6.CODEC);
        register(ModPayloads.DirectPage.P7.TYPE, ModPayloads.DirectPage.P7.CODEC); register(ModPayloads.DirectPage.P8.TYPE, ModPayloads.DirectPage.P8.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.CyclePage.TYPE, (p,c) -> InventoryStorage.cycle(c.player()));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.SwapHotbar.TYPE, (p,c) -> swapHotbar(c.player()));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.SortAll.TYPE, (p,c) -> InventoryAlgorithms.sortAll(c.player()));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.MergeAll.TYPE, (p,c) -> InventoryAlgorithms.mergeAll(c.player()));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P1.TYPE, (p,c) -> InventoryStorage.switchPage(c.player(),0));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P2.TYPE, (p,c) -> InventoryStorage.switchPage(c.player(),1));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P3.TYPE, (p,c) -> InventoryStorage.switchPage(c.player(),2));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P4.TYPE, (p,c) -> InventoryStorage.switchPage(c.player(),3));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P5.TYPE, (p,c) -> InventoryStorage.switchPage(c.player(),4));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P6.TYPE, (p,c) -> InventoryStorage.switchPage(c.player(),5));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P7.TYPE, (p,c) -> InventoryStorage.switchPage(c.player(),6));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P8.TYPE, (p,c) -> InventoryStorage.switchPage(c.player(),7));
    }

    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void register(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> type,
            net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf,T> codec) {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
    }

    private static void swapHotbar(ServerPlayer player) {
        Inventory inv = player.getInventory();
        var target = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player;
        List<ItemStack> stored = target.getAttachedOrElse(InventoryStorage.ALT_HOTBAR, List.of());
        ArrayList<ItemStack> current = new ArrayList<>(9);
        for (int i=0;i<9;i++) current.add(inv.getItem(i).copy());
        for (int i=0;i<9;i++) { ItemStack stack=i<stored.size()&&stored.get(i)!=null?stored.get(i):ItemStack.EMPTY; inv.setItem(i,stack.copy()); }
        target.setAttached(InventoryStorage.ALT_HOTBAR, List.copyOf(current));
        InventoryStorage.sync(player);
    }
}
