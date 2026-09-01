package com.anjas.custominventory;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
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
    private static final String AMMO = "com.tacz.guns.api.item.IAmmo";
    private static final String AMMO_BOX = "com.tacz.guns.api.item.IAmmoBox";
    private static final String DEFAULT_ASSETS = "com.tacz.guns.api.DefaultAssets";

    private TaczAmmoCompat() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(TACZ_MOD_ID)) return;
        try {
            Class<?> registryClass = Class.forName(REGISTRY);
            Class<?> providerClass = Class.forName(PROVIDER);
            Class<?> sourceClass = Class.forName(SOURCE);
            Class<?> ammoClass = Class.forName(AMMO);
            Class<?> ammoBoxClass = Class.forName(AMMO_BOX);
            Method ammoMatches = ammoClass.getMethod("isAmmoOfGun", ItemStack.class, ItemStack.class);
            Method boxMatches = ammoBoxClass.getMethod("isAmmoBoxOfGun", ItemStack.class, ItemStack.class);
            Method boxCount = ammoBoxClass.getMethod("getAmmoCount", ItemStack.class);
            Method setBoxCount = ammoBoxClass.getMethod("setAmmoCount", ItemStack.class, int.class);
            Method setBoxId = ammoBoxClass.getMethod("setAmmoId", ItemStack.class, Identifier.class);
            Identifier emptyAmmoId = (Identifier) Class.forName(DEFAULT_ASSETS).getField("EMPTY_AMMO_ID").get(null);

            Object source = Proxy.newProxyInstance(sourceClass.getClassLoader(), new Class<?>[]{sourceClass}, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("hasAmmo")) {
                    if (!(args[0] instanceof Player player)) return false;
                    ItemStack gun = (ItemStack) args[1];
                    if (player instanceof ServerPlayer sp) {
                        ServerAmmoView view = new ServerAmmoView(sp);
                        return hasCompatible(view.snapshot(), gun, ammoClass, ammoBoxClass, ammoMatches, boxMatches, boxCount);
                    }
                    return hasCompatible(clientSnapshot(player), gun, ammoClass, ammoBoxClass, ammoMatches, boxMatches, boxCount);
                }
                if (name.equals("consumeAmmo")) {
                    if (!(args[0] instanceof ServerPlayer player)) return 0;
                    ItemStack gun = (ItemStack) args[1];
                    int requested = Math.max(0, (Integer) args[2]);
                    if (requested == 0) return 0;
                    ServerAmmoView view = new ServerAmmoView(player);
                    int remaining = requested;
                    for (int slot = 0; slot < view.size() && remaining > 0; slot++) {
                        ItemStack stack = view.get(slot);
                        if (stack.isEmpty()) continue;
                        Object item = stack.getItem();
                        if (ammoClass.isInstance(item) && Boolean.TRUE.equals(ammoMatches.invoke(item, gun, stack))) {
                            int take = Math.min(remaining, stack.getCount());
                            if (take > 0) {
                                stack.shrink(take);
                                view.markMutated();
                                remaining -= take;
                            }
                            continue;
                        }
                        if (ammoBoxClass.isInstance(item) && Boolean.TRUE.equals(boxMatches.invoke(item, gun, stack))) {
                            int count = ((Number) boxCount.invoke(item, stack)).intValue();
                            int take = Math.min(Math.max(0, count), remaining);
                            if (take > 0) {
                                int after = count - take;
                                setBoxCount.invoke(item, stack, after);
                                if (after <= 0) setBoxId.invoke(item, stack, emptyAmmoId);
                                view.markMutated();
                                remaining -= take;
                            }
                        }
                    }
                    int consumed = requested - remaining;
                    if (view.mutated) {
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

    private static boolean hasCompatible(List<ItemStack> stacks, ItemStack gun, Class<?> ammoClass, Class<?> ammoBoxClass,
                                         Method ammoMatches, Method boxMatches, Method boxCount) throws ReflectiveOperationException {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            Object item = stack.getItem();
            if (ammoClass.isInstance(item) && Boolean.TRUE.equals(ammoMatches.invoke(item, gun, stack))) return true;
            if (ammoBoxClass.isInstance(item)
                    && Boolean.TRUE.equals(boxMatches.invoke(item, gun, stack))
                    && ((Number) boxCount.invoke(item, stack)).intValue() > 0) return true;
        }
        return false;
    }

    private static List<ItemStack> clientSnapshot(Player player) {
        ArrayList<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < 36; i++) all.add(player.getInventory().getItem(i));
        try {
            Class<?> cache = Class.forName("com.anjas.custominventory.client.HiddenRecipeContentsClient");
            @SuppressWarnings("unchecked")
            List<ItemStack> hidden = (List<ItemStack>) cache.getMethod("snapshot").invoke(null);
            all.addAll(hidden);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return all;
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
        private void markMutated() { mutated = true; }

        private ItemStack get(int slot) {
            if (slot < 0 || slot >= size()) return ItemStack.EMPTY;
            if (slot < 9) return player.getInventory().getItem(slot);
            int linear = slot - 9;
            int page = linear / InventoryStorage.PAGE_SIZE;
            int index = linear % InventoryStorage.PAGE_SIZE;
            if (page == activePage) return player.getInventory().getItem(InventoryStorage.MAIN_START + index);
            return pages.get(page).get(index);
        }

        private List<ItemStack> snapshot() {
            ArrayList<ItemStack> out = new ArrayList<>(size());
            for (int i = 0; i < size(); i++) out.add(get(i));
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
