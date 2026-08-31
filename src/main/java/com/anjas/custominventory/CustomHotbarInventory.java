package com.anjas.custominventory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

public final class CustomHotbarInventory implements ModInitializer {
    public static final String MOD_ID="custom_hotbar_inventory"; public static final Logger LOGGER=LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String path){return Identifier.fromNamespaceAndPath(MOD_ID,path);}
    @Override public void onInitialize(){InventoryStorage.register();registerPayloads();registerReceivers();registerLifecycle();LOGGER.info("Custom Hotbar Inventory initialized");}
    private static void registerPayloads(){
        register(ModPayloads.CyclePage.TYPE,ModPayloads.CyclePage.CODEC); register(ModPayloads.SwapHotbar.TYPE,ModPayloads.SwapHotbar.CODEC); register(ModPayloads.SortAll.TYPE,ModPayloads.SortAll.CODEC); register(ModPayloads.MergeAll.TYPE,ModPayloads.MergeAll.CODEC); register(ModPayloads.BrowseOpen.TYPE,ModPayloads.BrowseOpen.CODEC); register(ModPayloads.BrowseClose.TYPE,ModPayloads.BrowseClose.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ModPayloads.PageState.TYPE,ModPayloads.PageState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ModPayloads.HiddenRecipeContents.TYPE,ModPayloads.HiddenRecipeContents.CODEC);
        register(ModPayloads.DirectPage.P1.TYPE,ModPayloads.DirectPage.P1.CODEC);register(ModPayloads.DirectPage.P2.TYPE,ModPayloads.DirectPage.P2.CODEC);register(ModPayloads.DirectPage.P3.TYPE,ModPayloads.DirectPage.P3.CODEC);register(ModPayloads.DirectPage.P4.TYPE,ModPayloads.DirectPage.P4.CODEC);register(ModPayloads.DirectPage.P5.TYPE,ModPayloads.DirectPage.P5.CODEC);register(ModPayloads.DirectPage.P6.TYPE,ModPayloads.DirectPage.P6.CODEC);register(ModPayloads.DirectPage.P7.TYPE,ModPayloads.DirectPage.P7.CODEC);register(ModPayloads.DirectPage.P8.TYPE,ModPayloads.DirectPage.P8.CODEC);
    }
    private static void registerReceivers(){
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.BrowseOpen.TYPE,(payload,context)->{
            ServerPlayer p=context.player();
            InventoryStorage.setBrowsing(p,true);
            InventoryStorage.switchPage(p,0);
            sendPageState(p);
            sendHiddenRecipeState(p);
        });
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.BrowseClose.TYPE,(payload,context)->InventoryStorage.setBrowsing(context.player(),false));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.CyclePage.TYPE,(payload,context)->{ServerPlayer p=context.player();if(canManagePages(p)){InventoryStorage.cycle(p);sendPageState(p);sendHiddenRecipeState(p);}});
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.SwapHotbar.TYPE,(payload,context)->swapHotbar(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.SortAll.TYPE,(payload,context)->{ServerPlayer p=context.player();if(canManagePages(p)){InventoryAlgorithms.sortAll(p);sendHiddenRecipeState(p);}});
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.MergeAll.TYPE,(payload,context)->{ServerPlayer p=context.player();if(canManagePages(p)){InventoryAlgorithms.mergeAll(p);sendHiddenRecipeState(p);}});
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P1.TYPE,(payload,context)->direct(context.player(),0));ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P2.TYPE,(payload,context)->direct(context.player(),1));ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P3.TYPE,(payload,context)->direct(context.player(),2));ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P4.TYPE,(payload,context)->direct(context.player(),3));ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P5.TYPE,(payload,context)->direct(context.player(),4));ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P6.TYPE,(payload,context)->direct(context.player(),5));ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P7.TYPE,(payload,context)->direct(context.player(),6));ServerPlayNetworking.registerGlobalReceiver(ModPayloads.DirectPage.P8.TYPE,(payload,context)->direct(context.player(),7));
    }
    private static void registerLifecycle(){ServerLifecycleEvents.SERVER_STARTED.register(server->InventoryAlgorithms.runStartupSelfTests());ServerPlayerEvents.JOIN.register(p->{InventoryStorage.setBrowsing(p,false);InventoryStorage.migrateLegacy(p);});ServerPlayerEvents.LEAVE.register(p->{InventoryStorage.setBrowsing(p,false);InventoryStorage.snapshotLive(p);});ServerLivingEntityEvents.AFTER_DEATH.register((entity,ds)->{if(!(entity instanceof ServerPlayer p))return;InventoryStorage.setBrowsing(p,false);if(p.level().getGameRules().get(GameRules.KEEP_INVENTORY))InventoryStorage.snapshotLive(p);else InventoryStorage.dropHiddenOnDeath(p);});ServerPlayerEvents.COPY_FROM.register((oldP,newP,alive)->{boolean keep=oldP.level().getGameRules().get(GameRules.KEEP_INVENTORY);if(alive||keep)InventoryStorage.copyState(oldP,newP);else{InventoryStorage.clearStoredPages(newP);InventoryStorage.writeAltHotbar(newP,List.of());}});ServerTickEvents.END_SERVER_TICK.register(server->{for(ServerPlayer p:server.getPlayerList().getPlayers()){int before=InventoryStorage.active(p);InventoryStorage.routeOverflow(p);if(before!=InventoryStorage.active(p)){sendPageState(p);sendHiddenRecipeState(p);}}});}
    private static boolean canManagePages(ServerPlayer p){return InventoryStorage.isBrowsing(p)&&p.containerMenu.getCarried().isEmpty();}
    private static void direct(ServerPlayer p,int page){if(canManagePages(p)){InventoryStorage.switchPage(p,page);sendPageState(p);sendHiddenRecipeState(p);}}
    private static void sendPageState(ServerPlayer p){if(ServerPlayNetworking.canSend(p,ModPayloads.PageState.TYPE))ServerPlayNetworking.send(p,new ModPayloads.PageState(InventoryStorage.active(p)));}
    private static void sendHiddenRecipeState(ServerPlayer p){
        if(!ServerPlayNetworking.canSend(p,ModPayloads.HiddenRecipeContents.TYPE))return;
        int active=InventoryStorage.active(p);
        ArrayList<ItemStack> hidden=new ArrayList<>((InventoryStorage.PAGE_COUNT-1)*InventoryStorage.PAGE_SIZE);
        for(int page=0;page<InventoryStorage.PAGE_COUNT;page++)if(page!=active)for(ItemStack stack:InventoryStorage.read(p,page))hidden.add(stack.copy());
        ServerPlayNetworking.send(p,new ModPayloads.HiddenRecipeContents(hidden));
    }
    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void register(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> type,net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf,T> codec){PayloadTypeRegistry.serverboundPlay().register(type,codec);}
    private static void swapHotbar(ServerPlayer p){Inventory inv=p.getInventory();AttachmentTarget target=(AttachmentTarget)p;List<ItemStack> stored=target.getAttachedOrElse(InventoryStorage.ALT_HOTBAR,List.of());ArrayList<ItemStack> current=new ArrayList<>(9);for(int i=0;i<9;i++)current.add(inv.getItem(i).copy());for(int i=0;i<9;i++){ItemStack stack=i<stored.size()&&stored.get(i)!=null?stored.get(i):ItemStack.EMPTY;inv.setItem(i,stack.copy());}target.setAttached(InventoryStorage.ALT_HOTBAR,List.copyOf(current));InventoryStorage.sync(p);}
}
