package com.anjas.custominventory;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Optional TACZ 26.2 compatibility using its public AmmoSource API without a hard dependency. */
public final class TaczAmmoCompat {
    private static final String TACZ_MOD_ID = "tacz";
    private static final String REGISTRY = "com.tacz.guns.api.item.ammo.AmmoSourceRegistry";
    private static final String PROVIDER = "com.tacz.guns.api.item.ammo.AmmoSourceProvider";
    private static final String SOURCE = "com.tacz.guns.api.item.ammo.AmmoSource";
    private static final String HANDLER = "cn.sh1rocu.tacz.util.itemhandler.IItemHandler";

    private TaczAmmoCompat() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(TACZ_MOD_ID)) return;
        try {
            Class<?> registryClass = Class.forName(REGISTRY);
            Class<?> providerClass = Class.forName(PROVIDER);
            Class<?> sourceClass = Class.forName(SOURCE);
            Class<?> handlerClass = Class.forName(HANDLER);
            Method hasAmmo = registryClass.getMethod("hasAmmo", handlerClass, ItemStack.class);
            Method consumeAmmo = registryClass.getMethod("consumeAmmo", handlerClass, ItemStack.class, int.class);

            Object source = Proxy.newProxyInstance(sourceClass.getClassLoader(), new Class<?>[]{sourceClass}, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("hasAmmo")) {
                    Player player = args[0] instanceof Player p ? p : null;
                    ItemStack gun = (ItemStack) args[1];
                    if (player == null) return false;
                    Object handler = player instanceof ServerPlayer sp ? serverHandler(sp, handlerClass) : clientHandler(player, handlerClass);
                    return handler != null && Boolean.TRUE.equals(hasAmmo.invoke(null, handler, gun));
                }
                if (name.equals("consumeAmmo")) {
                    if (!(args[0] instanceof ServerPlayer player)) return 0;
                    ItemStack gun = (ItemStack) args[1];
                    int requested = (Integer) args[2];
                    ServerAmmoView view = new ServerAmmoView(player);
                    Object handler = proxyHandler(handlerClass, view);
                    int consumed = ((Number) consumeAmmo.invoke(null, handler, gun, requested)).intValue();
                    if (consumed > 0 || view.mutated) {
                        view.commit();
                        InventoryStorage.sync(player);
                        CustomHotbarInventory.sendHiddenRecipeState(player);
                    }
                    return consumed;
                }
                if (name.equals("toString")) return "CustomHotbarInventoryTaczAmmoSource";
                if (name.equals("hashCode")) return System.identityHashCode(proxy);
                if (name.equals("equals")) return proxy == args[0];
                return null;
            });

            Object provider = Proxy.newProxyInstance(providerClass.getClassLoader(), new Class<?>[]{providerClass}, (proxy, method, args) -> {
                if (method.getName().equals("findAmmoSource")) return args[0] instanceof Player ? source : null;
                if (method.getName().equals("toString")) return "CustomHotbarInventoryTaczAmmoProvider";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                return null;
            });

            Event event = (Event) registryClass.getField("EVENT").get(null);
            event.register(provider);
            CustomHotbarInventory.LOGGER.info("TACZ multi-page ammunition compatibility enabled");
        } catch (ReflectiveOperationException | LinkageError e) {
            CustomHotbarInventory.LOGGER.warn("TACZ detected but AmmoSource API compatibility could not initialize", e);
        }
    }

    private static Object serverHandler(ServerPlayer player, Class<?> handlerClass) {
        return proxyHandler(handlerClass, new ServerAmmoView(player));
    }

    private static Object clientHandler(Player player, Class<?> handlerClass) {
        try {
            Class<?> cache = Class.forName("com.anjas.custominventory.client.HiddenRecipeContentsClient");
            @SuppressWarnings("unchecked")
            List<ItemStack> hidden = (List<ItemStack>) cache.getMethod("snapshot").invoke(null);
            ArrayList<ItemStack> all = new ArrayList<>(36 + hidden.size());
            for (int i = 0; i < 36; i++) all.add(player.getInventory().getItem(i));
            for (ItemStack stack : hidden) all.add(stack);
            return readOnlyHandler(handlerClass, all);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Object readOnlyHandler(Class<?> handlerClass, List<ItemStack> stacks) {
        return Proxy.newProxyInstance(handlerClass.getClassLoader(), new Class<?>[]{handlerClass}, (proxy, method, args) -> switch (method.getName()) {
            case "getSlots" -> stacks.size();
            case "getStackInSlot" -> stacks.get((Integer) args[0]);
            case "extractItem", "insertItem" -> ItemStack.EMPTY;
            case "getSlotLimit" -> 64;
            case "isItemValid" -> true;
            case "toString" -> "CustomHotbarInventoryReadOnlyAmmoHandler";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        });
    }

    private static Object proxyHandler(Class<?> handlerClass, ServerAmmoView view) {
        return Proxy.newProxyInstance(handlerClass.getClassLoader(), new Class<?>[]{handlerClass}, (proxy, method, args) -> switch (method.getName()) {
            case "getSlots" -> view.size();
            case "getStackInSlot" -> view.get((Integer) args[0]);
            case "extractItem" -> view.extract((Integer) args[0], (Integer) args[1], (Boolean) args[2]);
            case "insertItem" -> (ItemStack) args[1];
            case "getSlotLimit" -> 64;
            case "isItemValid" -> true;
            case "toString" -> "CustomHotbarInventoryServerAmmoHandler";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        });
    }

    /** Mutable view of current hotbar + all eight 27-slot pages. */
    private static final class ServerAmmoView {
        private final ServerPlayer player;
        private final int activePage;
        private final List<List<ItemStack>> pages = new ArrayList<>(InventoryStorage.PAGE_COUNT);
        private boolean mutated;

        private ServerAmmoView(ServerPlayer player) {
            this.player = player;
            InventoryStorage.snapshotLive(player);
            this.activePage = InventoryStorage.active(player);
            for (int page = 0; page < InventoryStorage.PAGE_COUNT; page++) pages.add(new ArrayList<>(InventoryStorage.read(player, page)));
        }

        private int size() { return 9 + InventoryStorage.PAGE_COUNT * InventoryStorage.PAGE_SIZE; }

        private ItemStack get(int slot) {
            if (slot < 0 || slot >= size()) return ItemStack.EMPTY;
            if (slot < 9) return player.getInventory().getItem(slot);
            int linear = slot - 9;
            int page = linear / InventoryStorage.PAGE_SIZE;
            int index = linear % InventoryStorage.PAGE_SIZE;
            if (page == activePage) return player.getInventory().getItem(InventoryStorage.MAIN_START + index);
            return pages.get(page).get(index);
        }

        private ItemStack extract(int slot, int amount, boolean simulate) {
            ItemStack stack = get(slot);
            if (stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            int take = Math.min(amount, stack.getCount());
            ItemStack out = stack.copyWithCount(take);
            if (simulate) return out;
            stack.shrink(take);
            mutated = true;
            return out;
        }

        private void commit() {
            InventoryStorage.snapshotLive(player);
            for (int page = 0; page < InventoryStorage.PAGE_COUNT; page++) {
                if (page != activePage) InventoryStorage.write(player, page, pages.get(page));
            }
            player.getInventory().setChanged();
        }
    }
}
