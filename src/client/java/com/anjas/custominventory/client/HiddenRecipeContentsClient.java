package com.anjas.custominventory.client;

import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Client-only cache used solely to augment vanilla recipe-book availability checks. */
public final class HiddenRecipeContentsClient {
    private static volatile List<ItemStack> hiddenStacks=List.of();
    private HiddenRecipeContentsClient() {}
    public static void replace(List<ItemStack> stacks){hiddenStacks=stacks.stream().map(ItemStack::copy).toList();}
    public static void clear(){hiddenStacks=List.of();}
    public static void accountInto(StackedItemContents contents){for(ItemStack stack:hiddenStacks)contents.accountSimpleStack(stack);}
}
