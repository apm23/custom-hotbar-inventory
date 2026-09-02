package com.anjas.custominventory.client;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Client-side TACZ workbench ingredient accounting across all paged inventory slots. */
public final class TaczCraftingClientBridge {
    private TaczCraftingClientBridge() {}

    public static void refresh(Object screen) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || screen == null) return;
        try {
            Field selectedRecipeField = findField(screen.getClass(), "selectedRecipe");
            Field countField = findField(screen.getClass(), "playerIngredientCount");
            Object recipe = selectedRecipeField.get(screen);
            if (recipe == null) {
                countField.set(screen, null);
                return;
            }

            @SuppressWarnings("unchecked")
            List<Object> ingredients = (List<Object>) recipe.getClass().getMethod("getInputs").invoke(recipe);
            Int2IntArrayMap counts = new Int2IntArrayMap(ingredients.size());

            ArrayList<ItemStack> all = new ArrayList<>();
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) all.add(stack);
            all.addAll(HiddenRecipeContentsClient.snapshot());

            for (int i = 0; i < ingredients.size(); i++) {
                Object input = ingredients.get(i);
                Method getIngredient = input.getClass().getMethod("getIngredient");
                Ingredient ingredient = (Ingredient) getIngredient.invoke(input);
                int count = 0;
                if (ingredient != null) {
                    for (ItemStack stack : all) {
                        if (stack != null && !stack.isEmpty() && ingredient.test(stack)) count += stack.getCount();
                    }
                }
                counts.put(i, count);
            }
            countField.set(screen, counts);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // TACZ is optional; its native visible-page count remains as the safe fallback.
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
