package io.github.donttemptfate.fatesflipper.mixin;

import io.github.donttemptfate.fatesflipper.ui.ItemTooltips;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SkyBlock item tooltips in the Fates look; everything else (and any failure) stays vanilla. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	protected Slot hoveredSlot;

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
	private void fatesflipper$itemTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
		if (!self.getMenu().getCarried().isEmpty()) return;
		Slot slot = this.hoveredSlot;
		if (slot == null || !slot.hasItem()) return;
		if (ItemTooltips.show(g, slot.getItem(), mouseX, mouseY)) ci.cancel();
	}
}
