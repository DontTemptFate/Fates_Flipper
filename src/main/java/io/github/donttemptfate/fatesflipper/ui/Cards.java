package io.github.donttemptfate.fatesflipper.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.donttemptfate.fatesflipper.PriceTooltips;
import io.github.donttemptfate.fatesflipper.core.AuctionWindow.ButtonState;
import io.github.donttemptfate.fatesflipper.core.Coins;
import io.github.donttemptfate.fatesflipper.core.FlipEvent;
import io.github.donttemptfate.fatesflipper.core.Json;
import io.github.donttemptfate.fatesflipper.core.LegacyText;
import io.github.donttemptfate.fatesflipper.ui.Card.Grid;
import io.github.donttemptfate.fatesflipper.ui.Card.Hero;
import io.github.donttemptfate.fatesflipper.ui.Card.KeyValues;
import io.github.donttemptfate.fatesflipper.ui.Card.Line;
import io.github.donttemptfate.fatesflipper.ui.Card.Rule;
import io.github.donttemptfate.fatesflipper.ui.Card.SalesStrip;
import io.github.donttemptfate.fatesflipper.ui.Card.Seg;
import io.github.donttemptfate.fatesflipper.ui.Card.Status;
import io.github.donttemptfate.fatesflipper.ui.Ui.Role;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.github.donttemptfate.fatesflipper.ui.Card.seg;

/** The Fates cards: auction side panel, chat flip hover, and the Fates block of item tooltips. */
public final class Cards {
	private Cards() {}

	static String up(String s) {
		return s == null ? "" : s.toUpperCase(Locale.ROOT);
	}

	static int confColor(String conf) {
		if (conf == null) return Ui.GRAY;
		return switch (conf.toUpperCase(Locale.ROOT)) {
			case "HIGH" -> Ui.GREEN;
			case "MEDIUM" -> Ui.GOLD;
			case "LOW" -> Ui.RED;
			default -> Ui.GRAY;
		};
	}

	static int confBox(String conf) {
		if (conf == null) return Ui.DARK;
		return switch (conf.toUpperCase(Locale.ROOT)) {
			case "HIGH" -> Ui.BOX_GREEN;
			case "MEDIUM" -> Ui.BOX_GOLD;
			case "LOW" -> Ui.BOX_RED;
			default -> Ui.DARK;
		};
	}

	static String signed(double v) {
		return (v >= 0 ? "+" : "") + Coins.format(v);
	}

	// ---- auction window side panel ----------------------------------------------------------------

	/** Panel beside Hypixel's auction view / confirm window (design 2.1). */
	public static Card auctionPanel(FlipEvent f, JsonObject price, boolean confirm, ButtonState state, String buttonName,
			boolean cursorAssist, boolean closeKey) {
		return auctionPanel(f, price, confirm, state, buttonName, cursorAssist, closeKey, false);
	}

	/** {@code compact}: for small screens, only the essentials (no sales strip, two numbers). */
	public static Card auctionPanel(FlipEvent f, JsonObject price, boolean confirm, ButtonState state, String buttonName,
			boolean cursorAssist, boolean closeKey, boolean compact) {
		Card c = new Card(0.80f, compact ? 110 : 150, compact ? 140 : 196).gap(compact ? 3 : 4);
		String listed = f != null && f.firstSeenAt() > 0 ? "BIN · LISTED " + up(Coins.age(System.currentTimeMillis() - f.firstSeenAt())) : "BIN";
		c.add(new Line(List.of(seg("FATES FLIPPER", Role.LABEL, Ui.PINK)), List.of(seg(listed, Role.LABEL, Ui.GRAY))));
		if (f != null) {
			c.add(Line.of(seg(LegacyText.strip(f.name()), Role.TITLE, Ui.WHITE)));
			if (!Double.isNaN(f.safeProfit())) {
				int col = f.safeProfit() >= 0 ? Ui.GREEN : Ui.RED;
				Seg tag = Double.isNaN(f.safePct()) ? null : seg(Coins.percent(f.safePct()), Role.TAG, col);
				c.add(new Hero(List.of(seg(signed(f.safeProfit()), Role.HERO_L, col)), tag,
						f.safeProfit() >= 0 ? Ui.BOX_GREEN : Ui.BOX_RED, List.of(seg("PROFIT", Role.LABEL, Ui.GRAY))));
			}
			if (f.dupe() != null) c.add(Line.of(seg("DUPED?! · " + up(f.dupe()), Role.VALUE, Ui.RED)));
			if (compact) {
				Grid g2 = new Grid(2);
				g2.cell("PRICE", seg(Coins.format(f.price()), Role.VALUE, Ui.GOLD));
				if (!Double.isNaN(f.resale())) g2.cell("RESALE", seg(Coins.format(f.resale()), Role.VALUE, Ui.GOLD));
				c.add(g2);
				c.add(Line.of(seg(f.confidence() == null ? "?" : up(f.confidence()), Role.BODY_BOLD, confColor(f.confidence())),
						seg(f.salesUsed() > 0 ? " · " + f.salesUsed() + " sales" : "", Role.SMALL, Ui.GRAY)));
				c.space(1).add(status(confirm, state, buttonName, cursorAssist, closeKey));
				return c;
			}
			c.add(new Rule("FLIP", Ui.GRAY, null));
			Grid grid = new Grid(2);
			grid.cell("PRICE", seg(Coins.format(f.price()), Role.VALUE, Ui.GOLD));
			if (!Double.isNaN(f.resale())) grid.cell("RESALE", seg(Coins.format(f.resale()), Role.VALUE, Ui.GOLD));
			String conf = f.confidence() == null ? "?" : up(f.confidence());
			String confNote = f.confReason() != null ? " · " + f.confReason() : f.salesUsed() > 0 ? " · " + f.salesUsed() + " sales" : "";
			grid.cell("CONFIDENCE", seg(conf, Role.VALUE, confColor(f.confidence())), seg(confNote, Role.SMALL, Ui.GRAY));
			String lastSale = lastSaleAge(price);
			if (!Double.isNaN(f.volumePerDay())) {
				grid.cell("VOLUME", seg(String.format(Locale.ROOT, "%.0f/day", f.volumePerDay()), Role.VALUE, Ui.WHITE),
						seg(lastSale != null ? " · last " + lastSale : "", Role.SMALL, Ui.GRAY));
			}
			c.add(grid);
		}
		SalesStrip strip = sales(price);
		if (strip != null) {
			c.add(new Rule("RECENT SALES", Ui.GRAY, f != null && f.salesUsed() > 0 ? f.salesUsed() + " USED" : null));
			c.add(strip);
		}
		c.space(1).add(status(confirm, state, buttonName, cursorAssist, closeKey));
		return c;
	}

	static Status status(boolean confirm, ButtonState state, String buttonName, boolean cursorAssist, boolean closeKey) {
		if (confirm) return new Status("CLICK CONFIRM", Ui.GREEN, cursorAssist ? "CURSOR → CONFIRM" : null);
		if (state == null) return new Status("WAITING FOR BUY…", Ui.YELLOW, null);
		String close = closeKey ? "KEY CLOSES" : null;
		return switch (state) {
			case BUYABLE -> new Status("BUY NOW", Ui.GREEN, cursorAssist ? "CURSOR → BUY" : null);
			case CANT_AFFORD -> new Status("CAN'T AFFORD", Ui.ORANGE, close);
			case SOLD -> new Status("SOLD", Ui.RED, close);
			case NOT_YET -> new Status("NOT BUYABLE YET", Ui.YELLOW, null);
			case UNKNOWN -> new Status(up(buttonName), Ui.YELLOW, null);
		};
	}

	static String lastSaleAge(JsonObject price) {
		JsonArray sales = Json.arr(price, "recent_sales");
		if (sales == null || sales.isEmpty() || !sales.get(0).isJsonObject()) return null;
		long ts = Json.lng(sales.get(0).getAsJsonObject(), "ts", 0);
		return ts > 0 ? Coins.age(System.currentTimeMillis() - ts) : null;
	}

	static SalesStrip sales(JsonObject price) {
		JsonArray sales = Json.arr(price, "recent_sales");
		if (sales == null || sales.isEmpty()) return null;
		List<double[]> rows = new ArrayList<>();
		List<String> ages = new ArrayList<>();
		List<Boolean> ign = new ArrayList<>();
		for (JsonElement e : sales) {
			if (rows.size() >= 4 || !e.isJsonObject()) break;
			JsonObject s = e.getAsJsonObject();
			double p = Json.num(s, "price", Double.NaN);
			if (Double.isNaN(p)) continue;
			long ts = Json.lng(s, "ts", 0);
			rows.add(new double[] {p});
			ages.add(ts > 0 ? Coins.age(System.currentTimeMillis() - ts) : "");
			ign.add(Json.bool(s, "ignored", false));
		}
		if (rows.isEmpty()) return null;
		double[] p = new double[rows.size()];
		boolean[] ig = new boolean[rows.size()];
		for (int i = 0; i < p.length; i++) {
			p[i] = rows.get(i)[0];
			ig[i] = ign.get(i);
		}
		return new SalesStrip(p, ages.toArray(new String[0]), ig);
	}

	// ---- chat flip hover --------------------------------------------------------------------------

	/** The card shown when hovering a flip alert in chat (design 2.3). */
	public static Card flipHover(FlipEvent f) {
		Card c = new Card(0.94f, 150, 172).gap(3);
		String listed = f.firstSeenAt() > 0 ? "LISTED " + up(Coins.age(System.currentTimeMillis() - f.firstSeenAt())) : null;
		c.add(new Line(List.of(seg(LegacyText.strip(f.name()), Role.NAME, Ui.WHITE)),
				listed == null ? List.of() : List.of(seg(listed, Role.LABEL, Ui.DARK))));
		if (!Double.isNaN(f.safeProfit())) {
			int col = f.safeProfit() >= 0 ? Ui.GREEN : Ui.RED;
			Seg tag = Double.isNaN(f.safePct()) ? null : seg(Coins.percent(f.safePct()), Role.TAG, col);
			c.add(new Hero(List.of(seg(signed(f.safeProfit()), Role.HERO_M, col)), tag,
					f.safeProfit() >= 0 ? Ui.BOX_GREEN : Ui.BOX_RED, List.of(seg("CAUTIOUS", Role.LABEL, Ui.GRAY))));
		}
		if (f.dupe() != null) c.add(Line.of(seg("DUPED?! · " + up(f.dupe()), Role.BODY_BOLD, Ui.RED)));
		c.add(new Rule("FLIP", Ui.GRAY, null));
		KeyValues kv = new KeyValues(1);
		kv.row(seg("Price", Role.SMALL, Ui.GRAY), seg(Coins.format(f.price()), Role.BODY_BOLD, Ui.GOLD));
		if (!Double.isNaN(f.resale())) kv.row(seg("Estimated resale", Role.SMALL, Ui.GRAY), seg(Coins.format(f.resale()), Role.BODY_BOLD, Ui.GOLD));
		if (!Double.isNaN(f.expectedProfit())) kv.row(seg("Expected profit", Role.SMALL, Ui.GRAY), seg(Coins.format(f.expectedProfit()), Role.BODY_BOLD, Ui.GREEN));
		if (!Double.isNaN(f.chance())) kv.row(seg("Chance to hit target", Role.SMALL, Ui.GRAY), seg(Coins.percent(f.chance()), Role.BODY_BOLD, Ui.AQUA));
		c.add(kv);
		c.add(new Rule("MARKET", Ui.GRAY, null));
		StringBuilder meta = new StringBuilder(" conf");
		if (f.confReason() != null) meta.append(" · ").append(f.confReason());
		if (f.salesUsed() > 0) meta.append(" · ").append(f.salesUsed()).append(" sales");
		if (!Double.isNaN(f.volumePerDay())) meta.append(String.format(Locale.ROOT, " · %.0f/day", f.volumePerDay()));
		c.add(Line.of(seg(f.confidence() == null ? "?" : up(f.confidence()), Role.BODY_BOLD, confColor(f.confidence())),
				seg(meta.toString(), Role.SMALL, Ui.GRAY)));
		c.space(1).add(Line.of(seg("▶ CLICK TO OPEN THE AUCTION", Role.LABEL, Ui.YELLOW)));
		return c;
	}

	// ---- Fates block of item tooltips -------------------------------------------------------------

	/** Adds the FATES section for an item (design 2.2); nothing when prices are off. */
	public static void fatesBlock(Card c, PriceTooltips.View v) {
		if (v == null || v.kind() == PriceTooltips.Kind.HIDDEN) return;
		JsonObject d = v.data();
		switch (v.kind()) {
			case LOADING -> {
				c.space(2).add(new Rule("FATES", Ui.PINK, null));
				c.add(valueRow(seg("loading…", Role.SMALL, Ui.DARK)));
			}
			case FOUND -> found(c, d);
			case BAZAAR -> {
				c.space(2).add(new Rule("FATES", Ui.PINK, null));
				c.add(valueRow(seg("Bazaar item, see the Bazaar for prices", Role.SMALL, Ui.DARK)));
			}
			case NO_EXACT, NOT_TRADED -> {
				c.space(2).add(new Rule("FATES", Ui.PINK, null));
				if (!rough(c, d)) {
					c.add(valueRow(seg(v.kind() == PriceTooltips.Kind.NO_EXACT ? "no sales of this exact item yet"
							: "not traded since tracking started", Role.SMALL, Ui.DARK)));
				}
			}
			default -> {
			}
		}
	}

	static KeyValues valueRow(Seg value) {
		return new KeyValues(1).row(seg("Value", Role.SMALL, Ui.GRAY), value);
	}

	static boolean rough(Card c, JsonObject d) {
		JsonObject rough = Json.obj(d, "rough");
		double p = Json.num(rough, "price", Double.NaN);
		if (Double.isNaN(p)) return false;
		c.add(new KeyValues(1).row(seg("Rough value", Role.SMALL, Ui.GRAY), seg("~" + Coins.format(p), Role.BODY_BOLD, Ui.GOLD)));
		c.add(Line.of(seg("estimate from similar items, not a flip", Role.SMALL, Ui.DARK)));
		return true;
	}

	static void found(Card c, JsonObject d) {
		String conf = Json.str(d, "confidence");
		if (conf != null) conf = LegacyText.strip(conf);
		boolean learning = Json.bool(d, "learning", false);
		double market = Json.num(d, "market", Double.NaN);
		double cautious = Json.num(d, "cautious", Double.NaN);
		double lowest = Json.num(d, "lowest_bin", Double.NaN);
		double vol = Json.num(d, "volume_per_day", Double.NaN);
		int used = (int) Json.num(d, "sales_used", 0);
		long last = Json.lng(d, "last_traded_at", 0);
		String meta = last > 0 && System.currentTimeMillis() - last > 3_600_000L
				? "LAST TRADE " + up(Coins.age(System.currentTimeMillis() - last)) + " AGO" : null;
		c.space(2).add(new Rule("FATES", Ui.PINK, meta));
		if (learning || Double.isNaN(market)) {
			if (!rough(c, d)) {
				c.add(valueRow(seg("MIXED".equals(conf) ? "prices split, no estimate" : "learning (too few sales)", Role.SMALL, Ui.DARK)));
			}
			return;
		}
		List<Seg> num = new ArrayList<>();
		num.add(seg(Coins.format(market), Role.HERO_S, Ui.GOLD));
		num.add(seg("  VALUE", Role.LABEL, Ui.GRAY));
		if (!Double.isNaN(cautious)) {
			num.add(seg("   " + Coins.format(cautious), Role.VALUE, Ui.WHITE));
			num.add(seg("  SAFE", Role.LABEL, Ui.GRAY));
		}
		Seg tag = conf == null ? null : seg(up(conf), Role.TAG, confColor(conf));
		c.add(new Hero(num, tag, confBox(conf), null).tagFar());
		JsonObject est = Json.obj(d, "estimate");
		String reason = Json.str(est, "conf_reason");
		if (reason == null) reason = Json.str(d, "conf_reason");
		JsonObject dupe = Json.obj(d, "dupe");
		String level = Json.str(dupe, "level");
		if (level != null) c.add(Line.of(seg("DUPED?! · " + up(LegacyText.strip(level)), Role.BODY_BOLD, Ui.RED)));
		Grid g = new Grid(3);
		if (!Double.isNaN(lowest)) g.cell("LOWEST BIN", seg(Coins.format(lowest), Role.BODY_BOLD, Ui.WHITE));
		if (!Double.isNaN(vol)) g.cell("SOLD / DAY", seg(String.format(Locale.ROOT, "%.1f", vol), Role.BODY_BOLD, Ui.WHITE));
		if (used > 0) g.cell("SALES", seg(String.valueOf(used), Role.BODY_BOLD, Ui.WHITE));
		if (!g.isEmpty()) c.add(g);
		if (reason != null) c.add(Line.of(seg(up(conf) + " · " + LegacyText.strip(reason), Role.SMALL, Ui.DARK)));
	}

	// ---- placement --------------------------------------------------------------------------------

	/** Draws a card next to the mouse like a tooltip: flips sides and shrinks to stay on screen. */
	public static void drawAtMouse(GuiGraphicsExtractor g, Card card, int mouseX, int mouseY, int screenW, int screenH) {
		int w = card.width();
		int h = card.height();
		float s = Math.min(1f, Math.min((screenH - 8f) / h, (screenW - 8f) / w));
		s = Math.max(0.5f, s);
		int sw = Math.round(w * s);
		int sh = Math.round(h * s);
		int x = mouseX + 12;
		if (x + sw > screenW - 4) x = Math.max(4, mouseX - 12 - sw);
		int y = mouseY - 12;
		if (y + sh > screenH - 4) y = screenH - 4 - sh;
		if (y < 4) y = 4;
		card.draw(g, x, y, s);
	}
}
