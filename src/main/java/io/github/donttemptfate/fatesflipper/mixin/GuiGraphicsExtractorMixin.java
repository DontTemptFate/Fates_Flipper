package io.github.donttemptfate.fatesflipper.mixin;

import io.github.donttemptfate.fatesflipper.ui.FlipHovers;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flip alerts in chat show the Fates flip card on hover; other hovers are untouched. (Drawing a card
 * as the frame's tooltip is Fates Common's mixin, {@code DeferredTooltip}.)
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	@Inject(method = "componentHoverEffect", at = @At("HEAD"), cancellable = true)
	private void fatesflipper$flipHover(Font font, Style style, int x, int y, CallbackInfo ci) {
		if (FlipHovers.draw((GuiGraphicsExtractor) (Object) this, style, x, y)) ci.cancel();
	}
}
