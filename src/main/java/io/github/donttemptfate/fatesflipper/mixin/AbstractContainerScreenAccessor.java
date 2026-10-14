package io.github.donttemptfate.fatesflipper.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to where a container window sits on screen, for drawing overlays. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int fatesflipper$getLeftPos();

	@Accessor("topPos")
	int fatesflipper$getTopPos();

	@Accessor("imageWidth")
	int fatesflipper$getImageWidth();

	@Accessor("imageHeight")
	int fatesflipper$getImageHeight();
}
