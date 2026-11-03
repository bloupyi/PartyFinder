package fr.bloup.partyfinder.client.activity;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record Activity(String id, String category, ItemStack icon, Identifier sprite, int color, int maxSize) {
    public Component name() {
        return Component.translatable("partyfinder.activity." + id);
    }

    public Component categoryName() {
        return Component.translatable("partyfinder.category." + category);
    }
}
