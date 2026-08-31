package com.anjas.custominventory.mixin.client;

import com.anjas.custominventory.client.HiddenRecipeContentsClient;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Redirect(
        method={"initVisuals","updateStackedContents"},
        at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Inventory;fillStackedContents(Lnet/minecraft/world/entity/player/StackedItemContents;)V")
    )
    private void custominventory$includeHiddenPages(Inventory inventory, StackedItemContents contents){
        inventory.fillStackedContents(contents);
        HiddenRecipeContentsClient.accountInto(contents);
    }
}
