package com.anjas.custominventory.mixin;

import com.anjas.custominventory.TaczCraftingCompat;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional TACZ server crafting hook. */
@Pseudo
@Mixin(targets = "com.tacz.guns.inventory.GunSmithTableMenu", remap = false)
public abstract class TaczGunSmithTableMenuMixin {
    @Inject(method = "doCraft", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void custominventory$craftAcrossPages(Identifier recipeId, Player player, CallbackInfo ci) {
        if (TaczCraftingCompat.handleCraft(this, recipeId, player)) ci.cancel();
    }
}
