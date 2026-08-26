package com.anjas.custominventory.client;

import com.anjas.custominventory.CustomHotbarInventory;
import com.anjas.custominventory.ModPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class CustomHotbarInventoryClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CustomHotbarInventory.id("controls"));

    private final KeyMapping cycleInventory = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.custom_hotbar_inventory.cycle_inventory",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY));

    private final KeyMapping swapHotbar = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.custom_hotbar_inventory.swap_hotbar",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY));

    private final KeyMapping sortAll = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.custom_hotbar_inventory.sort_all",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY));

    private final KeyMapping mergeAll = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.custom_hotbar_inventory.merge_all",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY));

    private int visiblePage = 0;

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (isPlayerInventoryScreen(screen)) {
                visiblePage = 0;
                sendPage(0);
                addPageButtons(screen, width, height);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (swapHotbar.consumeClick()) {
                if (client.player != null && ClientPlayNetworking.canSend(ModPayloads.SwapHotbar.TYPE)) {
                    ClientPlayNetworking.send(new ModPayloads.SwapHotbar());
                }
            }

            while (cycleInventory.consumeClick()) {
                if (client.player != null && isPlayerInventoryScreen(client.gui.screen()) && ClientPlayNetworking.canSend(ModPayloads.CyclePage.TYPE)) {
                    visiblePage = (visiblePage + 1) & 7;
                    ClientPlayNetworking.send(new ModPayloads.CyclePage());
                }
            }

            while (sortAll.consumeClick()) {
                if (client.player != null && isPlayerInventoryScreen(client.gui.screen()) && ClientPlayNetworking.canSend(ModPayloads.SortAll.TYPE)) {
                    ClientPlayNetworking.send(new ModPayloads.SortAll());
                }
            }

            while (mergeAll.consumeClick()) {
                if (client.player != null && isPlayerInventoryScreen(client.gui.screen()) && ClientPlayNetworking.canSend(ModPayloads.MergeAll.TYPE)) {
                    ClientPlayNetworking.send(new ModPayloads.MergeAll());
                }
            }
        });
    }

    private static boolean isPlayerInventoryScreen(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
    }

    private void addPageButtons(Screen screen, int width, int height) {
        final int buttonWidth = 18;
        final int buttonHeight = 18;
        final int gap = 1;
        final int totalWidth = 8 * buttonWidth + 7 * gap;
        final int x0 = width / 2 - totalWidth / 2;
        final int y = Math.max(4, (height - 166) / 2 - 21);

        for (int page = 0; page < 8; page++) {
            final int target = page;
            Button button = Button.builder(Component.literal(Integer.toString(page + 1)), b -> {
                visiblePage = target;
                sendPage(target);
            }).bounds(x0 + page * (buttonWidth + gap), y, buttonWidth, buttonHeight).build();
            Screens.getWidgets(screen).add(button);
        }
    }

    private static void sendPage(int page) {
        if (ClientPlayNetworking.canSend(typeForPage(page))) {
            ClientPlayNetworking.send(payloadForPage(page));
        }
    }

    private static net.minecraft.network.protocol.common.custom.CustomPacketPayload payloadForPage(int page) {
        return switch (page) {
            case 0 -> new ModPayloads.DirectPage.P1();
            case 1 -> new ModPayloads.DirectPage.P2();
            case 2 -> new ModPayloads.DirectPage.P3();
            case 3 -> new ModPayloads.DirectPage.P4();
            case 4 -> new ModPayloads.DirectPage.P5();
            case 5 -> new ModPayloads.DirectPage.P6();
            case 6 -> new ModPayloads.DirectPage.P7();
            case 7 -> new ModPayloads.DirectPage.P8();
            default -> throw new IllegalArgumentException("page=" + page);
        };
    }

    private static net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> typeForPage(int page) {
        return switch (page) {
            case 0 -> ModPayloads.DirectPage.P1.TYPE;
            case 1 -> ModPayloads.DirectPage.P2.TYPE;
            case 2 -> ModPayloads.DirectPage.P3.TYPE;
            case 3 -> ModPayloads.DirectPage.P4.TYPE;
            case 4 -> ModPayloads.DirectPage.P5.TYPE;
            case 5 -> ModPayloads.DirectPage.P6.TYPE;
            case 6 -> ModPayloads.DirectPage.P7.TYPE;
            case 7 -> ModPayloads.DirectPage.P8.TYPE;
            default -> throw new IllegalArgumentException("page=" + page);
        };
    }
}
