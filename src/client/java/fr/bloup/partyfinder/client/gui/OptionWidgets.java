package fr.bloup.partyfinder.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Vanilla option widgets bound to plain config fields; labels and tooltips come from "&lt;prefix&gt;&lt;key&gt;[.tooltip]". */
public final class OptionWidgets {
    public static final int WIDTH = 150;

    private final String prefix;

    public OptionWidgets(String prefix) {
        this.prefix = prefix;
    }

    public Component label(String key) {
        return Component.translatable(prefix + key);
    }

    public AbstractWidget toggle(String key, boolean value, Consumer<Boolean> onChange) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(value).create(0, 0, WIDTH, 20, label(key), (b, v) -> onChange.accept(v));
        button.setTooltip(Tooltip.create(Component.translatable(prefix + key + ".tooltip")));
        return button;
    }

    public <E extends Enum<E>> AbstractWidget choice(String key, E[] values, E value, Consumer<E> onChange) {
        CycleButton<E> button = CycleButton.builder((E v) -> Component.translatable(prefix + key + "." + v.name().toLowerCase(Locale.ROOT)), value)
                .withValues(values)
                .create(0, 0, WIDTH, 20, label(key), (b, v) -> onChange.accept(v));
        button.setTooltip(Tooltip.create(Component.translatable(prefix + key + ".tooltip")));
        return button;
    }

    public AbstractWidget slider(String key, int min, int max, int value, String unit, IntConsumer onChange) {
        Component label = label(key);
        AbstractSliderButton slider = new AbstractSliderButton(0, 0, WIDTH, 20, Component.empty(), (double) (value - min) / (max - min)) {
            {
                updateMessage();
            }

            private int current() {
                return (int) Math.round(Mth.lerp(this.value, min, max));
            }

            @Override
            protected void updateMessage() {
                setMessage(label.copy().append(": " + current() + unit));
            }

            @Override
            protected void applyValue() {
                onChange.accept(current());
            }
        };
        slider.setTooltip(Tooltip.create(Component.translatable(prefix + key + ".tooltip")));
        return slider;
    }
}
