package io.github.donttemptfate.fatesflipper.ui;

import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.DeferredTooltip;

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
		// Hypixel resends items (e.g. every second for an auction's countdown): the same item keeps
		// its scroll position and never gets narrower while it's hovered
		String key = stableKey(stack);
		if (!key.equals(shownKey)) {
			shownKey = key;
			offset = 0;
			widest = 0;
			built = null;
		}
		Card card;
		try {
			card = card(service, stack);
		} catch (RuntimeException e) {
			return false;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		widest = Math.max(widest, card.contentWidth());
		card.atLeast(widest);
		((DeferredTooltip) g).fatescommon$setDeferredTooltip(() -> {
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

	private static String shownKey = "";
	private static Card built;
	private static ItemStack builtFor;
	private static PriceTooltips.View builtView;
	private static long builtAtMs;
	static final long REBUILD_MS = 250;

	/**
	 * The card for the hovered item, rebuilt only when the item or its price answer changes, or every
	 * 250 ms (other mods' lines may change; audit F-P1), not every frame.
	 */
	static Card card(FlipService service, ItemStack stack) {
		PriceTooltips.View view = PriceTooltips.view(service, stack);
		long now = System.currentTimeMillis();
		if (built != null && builtFor == stack && sameView(view, builtView) && now - builtAtMs < REBUILD_MS) return built;
		Card c = ItemCard.build(stack, view, otherModsLines(stack));
		built = c;
		builtFor = stack;
		builtView = view;
		builtAtMs = now;
		return c;
	}

	private static boolean sameView(PriceTooltips.View a, PriceTooltips.View b) {
		return b != null && a.kind() == b.kind() && a.data() == b.data();
	}

	/** What other mods add to this item's tooltip (the game's own tooltip list, minus ours and the item's own lines). */
	public static java.util.List<net.minecraft.network.chat.Component> otherModsLines(ItemStack stack) {
		PriceTooltips.QUIET.set(true);
		try {
			var full = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(net.minecraft.client.Minecraft.getInstance(), stack);
			return ItemCard.extraLines(full, stack);
		} catch (RuntimeException e) {
			return java.util.List.of(); // another mod's failure never breaks our tooltip
		} finally {
			PriceTooltips.QUIET.set(false);
		}
	}
	private static int widest;

	/** Same item → same key, ignoring Hypixel's listing lines (the countdown) and the stack object. */
	static String stableKey(ItemStack stack) {
		var data = ItemBytes.skyblockData(stack);
		return (data == null ? "" : data.getStringOr("uuid", "")) + "|" + ItemBytes.cacheKey(stack, data);
	}
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
