package com.anjas.custominventory.mixin.client;

import com.anjas.custominventory.client.TaczHudBridge;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional TACZ HUD extension; @Pseudo keeps TACZ an optional dependency. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.gui.overlay.GunHudOverlay", remap = false)
public abstract class TaczGunHudOverlayMixin {
    @Shadow(remap = false) private static int cacheInventoryAmmoCount;

    @Inject(method = "handleInventoryAmmo", at = @At("TAIL"), require = 0, remap = false)
    private static void custominventory$includeHiddenAmmo(ItemStack gun, Inventory inventory, CallbackInfo ci) {
        int hidden = TaczHudBridge.countHiddenAmmo(gun);
        if (hidden <= 0) return;
        cacheInventoryAmmoCount = Math.min(9999, cacheInventoryAmmoCount + hidden);
    }
}
