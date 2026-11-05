package fr.bloup.partyfinder.client.party;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A chat message as plain text (for regexes) that remembers each character's style, legacy § codes included, so a
 * matched part (e.g. "[MVP+] Name") can be reused with Hypixel's own colors.
 */
final class StyledText {
    private final StringBuilder plain = new StringBuilder();
    private final List<Style> styles = new ArrayList<>();

    private StyledText() {
    }

    static StyledText of(Component message) {
        StyledText text = new StyledText();
        message.visit((style, content) -> {
            Style current = style;
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '§' && i + 1 < content.length()) {
                    ChatFormatting format = ChatFormatting.getByCode(content.charAt(i + 1));
                    if (format != null) {
                        current = format == ChatFormatting.RESET ? style : current.applyLegacyFormat(format);
                        i++;
                        continue;
                    }
                }
                text.plain.append(c);
                text.styles.add(current);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return text;
    }

    String plain() {
        return plain.toString();
    }

    /**
     * The characters start..end with their colors and formats (not their click or hover actions); null when that part
     * has no color at all, so the caller can draw it itself.
     */
    MutableComponent slice(int start, int end) {
        MutableComponent result = Component.empty();
        boolean colored = false;
        int from = start;
        while (from < end) {
            Style style = clean(styles.get(from));
            int to = from + 1;
            while (to < end && Objects.equals(clean(styles.get(to)), style)) to++;
            if (style.getColor() != null) colored = true;
            result.append(Component.literal(plain.substring(from, to)).withStyle(style));
            from = to;
        }
        return colored ? result : null;
    }

    private static Style clean(Style style) {
        return Style.EMPTY.withColor(style.getColor()).withBold(style.isBold()).withItalic(style.isItalic())
                .withUnderlined(style.isUnderlined()).withStrikethrough(style.isStrikethrough()).withObfuscated(style.isObfuscated());
    }
}
