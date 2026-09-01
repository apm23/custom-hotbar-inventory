package com.anjas.custominventory.client;

import com.anjas.custominventory.CustomHotbarInventory;
import com.anjas.custominventory.InputDebounce;
import com.anjas.custominventory.ModPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

public final class CustomHotbarInventoryClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY=KeyMapping.Category.register(CustomHotbarInventory.id("controls"));
    private static final long CYCLE_DEBOUNCE_NANOS=180_000_000L;
    private final KeyMapping cycleInventory=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.custom_hotbar_inventory.cycle_inventory",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,CATEGORY));
    private final KeyMapping swapHotbar=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.custom_hotbar_inventory.swap_hotbar",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_B,CATEGORY));
    private final KeyMapping sortAll=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.custom_hotbar_inventory.sort_all",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_N,CATEGORY));
    private final KeyMapping mergeAll=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.custom_hotbar_inventory.merge_all",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_M,CATEGORY));
    private final InputDebounce hotbarDebounce=new InputDebounce(CYCLE_DEBOUNCE_NANOS);
    private final InputDebounce inventoryDebounce=new InputDebounce(CYCLE_DEBOUNCE_NANOS);
    private final List<Button> pageButtons=new ArrayList<>(8);
    private int visiblePage=0;

    @Override public void onInitializeClient(){
        CustomHotbarInventory.LOGGER.info("Custom Hotbar Inventory client initialized");
        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.PageState.TYPE,(payload,context)->context.client().execute(()->{visiblePage=Math.max(0,Math.min(7,payload.page()));refreshPageButtons();}));
        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.HiddenRecipeContents.TYPE,(payload,context)->context.client().execute(()->{
            HiddenRecipeContentsClient.replace(payload.stacks());
            if(context.client().player!=null)context.client().player.getInventory().setChanged();
        }));
        ScreenEvents.AFTER_INIT.register((client,screen,width,height)->{
            installGuiInput(screen);
            if(!isManagedContainer(screen))return;
            pageButtons.clear();
            if(ClientPlayNetworking.canSend(ModPayloads.BrowseOpen.TYPE))ClientPlayNetworking.send(new ModPayloads.BrowseOpen());
            if(screen instanceof InventoryScreen){addPageButtons(screen,width,height);refreshPageButtons();}
            ScreenEvents.remove(screen).register(removed->{pageButtons.clear();if(ClientPlayNetworking.canSend(ModPayloads.BrowseClose.TYPE))ClientPlayNetworking.send(new ModPayloads.BrowseClose());});
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(client.gui.screen()==null){consumeHotbarOutsideGui();drain(cycleInventory);drain(sortAll);drain(mergeAll);}else{drain(swapHotbar);drain(cycleInventory);drain(sortAll);drain(mergeAll);}});
    }
    private void installGuiInput(Screen screen){ScreenKeyboardEvents.allowKeyPress(screen).register((s,event)->!handleGuiInput(s,InputConstants.getKey(event)));ScreenMouseEvents.allowMouseClick(screen).register((s,event)->!handleGuiInput(s,InputConstants.Type.MOUSE.getOrCreate(event.button())));}
    private boolean handleGuiInput(Screen screen,InputConstants.Key input){
        if(matches(swapHotbar,input)){if(hotbarDebounce.tryAcquire(System.nanoTime()))sendIfPossible(new ModPayloads.SwapHotbar(),ModPayloads.SwapHotbar.TYPE);return true;}
        if(!isManagedContainer(screen))return false;
        if(matches(cycleInventory,input)){if(inventoryDebounce.tryAcquire(System.nanoTime()))sendIfPossible(new ModPayloads.CyclePage(),ModPayloads.CyclePage.TYPE);return true;}
        if(screen instanceof InventoryScreen && matches(sortAll,input)){sendIfPossible(new ModPayloads.SortAll(),ModPayloads.SortAll.TYPE);return true;}
        if(screen instanceof InventoryScreen && matches(mergeAll,input)){sendIfPossible(new ModPayloads.MergeAll(),ModPayloads.MergeAll.TYPE);return true;}
        return false;
    }
    private void consumeHotbarOutsideGui(){boolean clicked=false;while(swapHotbar.consumeClick())clicked=true;if(clicked&&hotbarDebounce.tryAcquire(System.nanoTime()))sendIfPossible(new ModPayloads.SwapHotbar(),ModPayloads.SwapHotbar.TYPE);}
    private static boolean matches(KeyMapping m,InputConstants.Key input){return KeyMappingHelper.getBoundKeyOf(m).equals(input);} private static void drain(KeyMapping m){while(m.consumeClick()){} }
    private static boolean isManagedContainer(Screen s){return s instanceof AbstractContainerScreen<?>;}
    private void addPageButtons(Screen screen,int width,int height){final int guiLeft=(width-176)/2,guiTop=(height-166)/2;final int bw=9,bh=9,gap=1;final int x0=guiLeft+125,y0=guiTop+65;for(int page=0;page<8;page++){final int target=page;int col=page%4,row=page/4;Button b=Button.builder(Component.literal(Integer.toString(page+1)),ignored->sendPage(target)).bounds(x0+col*(bw+gap),y0+row*(bh+gap),bw,bh).build();pageButtons.add(b);Screens.getWidgets(screen).add(b);}}
    private void refreshPageButtons(){for(int i=0;i<pageButtons.size();i++){Button b=pageButtons.get(i);b.active=i!=visiblePage;b.setMessage(Component.literal(Integer.toString(i+1)));}}
    private static void sendPage(int page){sendIfPossible(payloadForPage(page),typeForPage(page));}
    private static void sendIfPossible(net.minecraft.network.protocol.common.custom.CustomPacketPayload p,net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> t){if(ClientPlayNetworking.canSend(t))ClientPlayNetworking.send(p);}
    private static net.minecraft.network.protocol.common.custom.CustomPacketPayload payloadForPage(int p){return switch(p){case 0->new ModPayloads.DirectPage.P1();case 1->new ModPayloads.DirectPage.P2();case 2->new ModPayloads.DirectPage.P3();case 3->new ModPayloads.DirectPage.P4();case 4->new ModPayloads.DirectPage.P5();case 5->new ModPayloads.DirectPage.P6();case 6->new ModPayloads.DirectPage.P7();case 7->new ModPayloads.DirectPage.P8();default->throw new IllegalArgumentException("page="+p);};}
    private static net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> typeForPage(int p){return switch(p){case 0->ModPayloads.DirectPage.P1.TYPE;case 1->ModPayloads.DirectPage.P2.TYPE;case 2->ModPayloads.DirectPage.P3.TYPE;case 3->ModPayloads.DirectPage.P4.TYPE;case 4->ModPayloads.DirectPage.P5.TYPE;case 5->ModPayloads.DirectPage.P6.TYPE;case 6->ModPayloads.DirectPage.P7.TYPE;case 7->ModPayloads.DirectPage.P8.TYPE;default->throw new IllegalArgumentException("page="+p);};}
}
