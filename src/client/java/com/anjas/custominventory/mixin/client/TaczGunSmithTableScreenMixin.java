package com.anjas.custominventory.mixin.client;

import com.anjas.custominventory.client.TaczCraftingClientBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional TACZ client workbench ingredient-count hook. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.gui.GunSmithTableScreen", remap = false)
public abstract class TaczGunSmithTableScreenMixin {
    @Inject(method = "init", at = @At("TAIL"), require = 0, remap = false)
    private void custominventory$refreshPagedIngredientCounts(CallbackInfo ci) {
        TaczCraftingClientBridge.refresh(this);
    }
}
