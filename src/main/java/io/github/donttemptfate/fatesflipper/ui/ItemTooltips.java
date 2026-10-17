package io.github.donttemptfate.fatesflipper.ui;

import io.github.donttemptfate.fatesflipper.FatesFlipperClient;
import io.github.donttemptfate.fatesflipper.FlipService;
import io.github.donttemptfate.fatesflipper.ItemBytes;
import io.github.donttemptfate.fatesflipper.PriceTooltips;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Item tooltips for SkyBlock items in the Fates look (design 2.2 / 2.4). Called from the container
 * screen's tooltip step; returns false to leave everything else (and any failure) to vanilla.
 */
public final class ItemTooltips {
	private ItemTooltips() {}

	public static boolean show(GuiGraphicsExtractor g, ItemStack stack, int mouseX, int mouseY) {
		FlipService service = FatesFlipperClient.service();
		if (service == null || !service.config().fatesLook || !service.config().fatesTooltips) return false;
		if (stack == null || stack.isEmpty() || ItemBytes.skyblockData(stack) == null) return false;
		Card card;
		try {
			card = ItemCard.build(stack, PriceTooltips.view(service, stack));
		} catch (RuntimeException e) {
			return false;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		((DeferredTooltip) g).fatesflipper$setDeferredTooltip(() -> {
			try {
				Cards.drawAtMouse(g, card, mouseX, mouseY, w, h);
			} catch (RuntimeException e) {
				// a tooltip must never crash the game
			}
		});
		return true;
	}
}
