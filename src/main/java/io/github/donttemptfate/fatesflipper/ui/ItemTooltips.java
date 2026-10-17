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
		int key = System.identityHashCode(stack);
		if (key != shownKey) {
			shownKey = key;
			offset = 0;
		}
		((DeferredTooltip) g).fatesflipper$setDeferredTooltip(() -> {
			try {
				int ch = card.height();
				int cw = card.width();
				int viewH = h - 8;
				if (ch * 0.8f <= viewH || cw > w - 8) {
					maxOffset = 0;
					Cards.drawAtMouse(g, card, mouseX, mouseY, w, h);
				} else {
					// too tall even shrunk a little: show it full size and let the mouse wheel scroll it
					maxOffset = ch - viewH;
					offset = Math.max(0, Math.min(offset, maxOffset));
					int x = mouseX + 12;
					if (x + cw > w - 4) x = Math.max(4, mouseX - 12 - cw);
					card.drawScrolled(g, x, 4, viewH, offset);
				}
				shownAtMs = System.currentTimeMillis();
			} catch (RuntimeException e) {
				// a tooltip must never crash the game
			}
		});
		return true;
	}

	private static int shownKey;
	private static volatile int offset;
	private static volatile int maxOffset;
	private static volatile long shownAtMs;

	/** Mouse wheel over a long Fates tooltip: scrolls it; false = not ours to handle. */
	public static boolean scroll(double amount) {
		if (maxOffset <= 0 || System.currentTimeMillis() - shownAtMs > 250) return false;
		offset = (int) Math.max(0, Math.min(maxOffset, offset - Math.round(amount * 16)));
		return true;
	}
}
