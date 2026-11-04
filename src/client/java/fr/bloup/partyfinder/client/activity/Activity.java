package fr.bloup.partyfinder.client.activity;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record Activity(
        String id,
        String category,
        ItemStack icon,
        Identifier sprite,
        int color,
        int maxSize,
        List<Option> options,
        Launch launch,
        List<String> stats,
        List<String> events,
        boolean transfer
) {
    public enum Launch { NONE, HOST, ALL }

    public record Option(String id, List<Value> values) {
        public Optional<Value> value(String valueId) {
            return values.stream().filter(v -> v.id().equals(valueId)).findFirst();
        }
    }

    public record Value(String id, List<String> commands) {
    }

    public Component name() {
        return Component.translatable("partyfinder.activity." + id);
    }

    public Component categoryName() {
        return Component.translatable("partyfinder.category." + category);
    }

    public static Component optionName(String optionId, String valueId) {
        return Component.translatable("partyfinder.option." + optionId + "." + valueId);
    }

    /** Activity name followed by its main option, e.g. "Dungeons F7". */
    public Component title(Map<String, String> selected) {
        if (options.isEmpty() || selected == null) return name();
        String value = selected.get(options.getFirst().id());
        if (value == null) return name();
        return name().copy().append(" ").append(optionName(options.getFirst().id(), value));
    }

    /** Commands to run once the party is full, from the selected option values. */
    public List<String> launchCommands(Map<String, String> selected) {
        if (selected == null) return List.of();
        return options.stream()
                .flatMap(option -> Optional.ofNullable(selected.get(option.id())).flatMap(option::value).stream())
                .flatMap(value -> value.commands().stream())
                .toList();
    }
}
