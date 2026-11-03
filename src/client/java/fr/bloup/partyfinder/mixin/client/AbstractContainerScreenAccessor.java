package fr.bloup.partyfinder.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int partyfinder$getLeftPos();

    @Accessor("topPos")
    int partyfinder$getTopPos();

    @Accessor("imageWidth")
    int partyfinder$getImageWidth();
}
