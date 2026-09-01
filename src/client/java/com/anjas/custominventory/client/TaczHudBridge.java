package com.anjas.custominventory.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/** Client-only optional TACZ bridge for reserve-ammo HUD counting. */
public final class TaczHudBridge {
    private static final int HUD_MAX = 9999;
    private static volatile Access access;
    private static volatile boolean attempted;

    private TaczHudBridge() {}

    public static int countHiddenAmmo(ItemStack gun) {
        Access a = access();
        if (a == null || gun == null || gun.isEmpty()) return 0;
        int total = 0;
        try {
            for (ItemStack stack : HiddenRecipeContentsClient.snapshot()) {
                if (stack == null || stack.isEmpty()) continue;
                Object item = stack.getItem();
                if (a.ammoClass.isInstance(item)
                        && Boolean.TRUE.equals(a.ammoMatches.invoke(item, gun, stack))) {
                    total += stack.getCount();
                } else if (a.ammoBoxClass.isInstance(item)
                        && Boolean.TRUE.equals(a.boxMatches.invoke(item, gun, stack))) {
                    if (Boolean.TRUE.equals(a.boxCreative.invoke(item, stack))
                            || Boolean.TRUE.equals(a.boxAllCreative.invoke(item, stack))) {
                        return HUD_MAX;
                    }
                    total += Math.max(0, ((Number) a.boxCount.invoke(item, stack)).intValue());
                }
                if (total >= HUD_MAX) return HUD_MAX;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0;
        }
        return Math.min(total, HUD_MAX);
    }

    private static Access access() {
        if (attempted) return access;
        synchronized (TaczHudBridge.class) {
            if (attempted) return access;
            attempted = true;
            if (!FabricLoader.getInstance().isModLoaded("tacz")) return null;
            try {
                Class<?> ammo = Class.forName("com.tacz.guns.api.item.IAmmo");
                Class<?> box = Class.forName("com.tacz.guns.api.item.IAmmoBox");
                access = new Access(
                        ammo,
                        box,
                        ammo.getMethod("isAmmoOfGun", ItemStack.class, ItemStack.class),
                        box.getMethod("isAmmoBoxOfGun", ItemStack.class, ItemStack.class),
                        box.getMethod("getAmmoCount", ItemStack.class),
                        box.getMethod("isCreative", ItemStack.class),
                        box.getMethod("isAllTypeCreative", ItemStack.class)
                );
            } catch (ReflectiveOperationException | LinkageError ignored) {
                access = null;
            }
            return access;
        }
    }

    private record Access(Class<?> ammoClass, Class<?> ammoBoxClass, Method ammoMatches,
                          Method boxMatches, Method boxCount, Method boxCreative,
                          Method boxAllCreative) {}
}
