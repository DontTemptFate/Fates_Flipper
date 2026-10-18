package io.github.donttemptfate.fatesflipper.test;

import com.google.gson.JsonParser;
import io.github.donttemptfate.fatesflipper.PriceTooltips;
import io.github.donttemptfate.fatesflipper.core.FlipEvent;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatesflipper.ui.Cards;
import io.github.donttemptfate.fatesflipper.ui.ItemCard;
import io.github.donttemptfate.fatescommon.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Test-only screen: every Fates card side by side (and a font calibration strip) for screenshots. */
final class CardGallery extends Screen {
	private final int page;
	private final FlipEvent flip;
	private final ItemStack rich;
	private final ItemStack simple;

	CardGallery(int page, FlipEvent flip, ItemStack rich, ItemStack simple) {
		super(Component.literal("gallery"));
		this.page = page;
		this.flip = flip;
		this.rich = rich;
		this.simple = simple;
	}

	static final String FOUND = "{\"found\":true,\"confidence\":\"MEDIUM\",\"learning\":false,\"market\":23000000,\"cautious\":19200000,"
			+ "\"lowest_bin\":19900000,\"volume_per_day\":0.0,\"sales_used\":6,\"last_traded_at\":%d,\"estimate\":{\"conf_reason\":\"wide prices\"},"
			+ "\"dupe\":null}";

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.fill(0, 0, this.width, this.height, 0xFF15111C);
		if (page == 0) {
			// calibration: red = intended baseline, blue = intended cap top
			int y = 14;
			for (Ui.Role r : Ui.Role.values()) {
				int base = y + r.cap();
				g.fill(4, base, 200, base + 1, 0xFFFF0000);
				g.fill(4, base - r.cap(), 200, base - r.cap() + 1, 0xFF3355FF);
				new Card.Line(java.util.List.of(Card.seg(r.name() + " Hg 1.25M ▶◇→", r, Ui.WHITE)), java.util.List.of()).draw(g, 6, y, 190);
				y += r.height() + 6;
			}
			if (flip != null) Cards.flipHover(flip).draw(g, 210, 6, 1f);
		} else if (page == 1) {
			var v = new PriceTooltips.View(PriceTooltips.Kind.FOUND,
					JsonParser.parseString(String.format(FOUND, System.currentTimeMillis() - 241_000_000L)).getAsJsonObject());
			ItemCard.build(simple, v).draw(g, 6, 6, 1f);
			var none = new PriceTooltips.View(PriceTooltips.Kind.NO_EXACT, JsonParser.parseString("{\"found\":false}").getAsJsonObject());
			Card c = ItemCard.build(rich, none);
			c.draw(g, 220, 6, Math.min(1f, (this.height - 12f) / c.height()));
		} else {
			if (flip != null) {
				Cards.auctionPanel(flip, null, false, io.github.donttemptfate.fatesflipper.core.AuctionWindow.ButtonState.BUYABLE, "Buy Item Right Now", true, true)
						.draw(g, 6, 6, 1f);
			}
		}
	}
}
