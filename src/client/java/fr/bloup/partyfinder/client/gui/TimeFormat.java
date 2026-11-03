package fr.bloup.partyfinder.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class TimeFormat {
    private TimeFormat() {
    }

    static Component ago(long timestamp) {
        long minutes = Math.max(0, (System.currentTimeMillis() - timestamp) / 60_000);
        if (minutes < 1) return Component.translatable("partyfinder.listing.age.now");
        if (minutes < 60) return Component.translatable("partyfinder.listing.age.minutes", minutes);
        if (minutes < 60 * 24) return Component.translatable("partyfinder.listing.age.hours", minutes / 60);
        return Component.translatable("partyfinder.listing.age.days", minutes / (60 * 24));
    }

    static String clip(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        int ellipsis = font.width("...");
        if (maxWidth <= ellipsis) return "";
        return font.plainSubstrByWidth(text, maxWidth - ellipsis) + "...";
    }
}
