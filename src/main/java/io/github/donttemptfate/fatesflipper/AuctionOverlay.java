package io.github.donttemptfate.fatesflipper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.donttemptfate.fatesflipper.core.AuctionWindow;
import io.github.donttemptfate.fatesflipper.core.AuctionWindow.ButtonState;
import io.github.donttemptfate.fatescommon.text.Coins;
import io.github.donttemptfate.fatesflipper.core.FlipEvent;
import io.github.donttemptfate.fatesflipper.core.Json;
import io.github.donttemptfate.fatescommon.text.LegacyText;
import io.github.donttemptfate.fatesflipper.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import io.github.donttemptfate.fatesflipper.compat.Compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws on top of Hypixel's own auction windows when the auction was opened from a flip:
 * dims the filler panes, outlines the item and the Buy / Confirm button, and shows a side panel.
 *
 * <p>Visual only. Every click still goes to Hypixel's real slots; nothing here clicks. Cursor
 * assist (opt-in, off by default) only moves the cursor, once per window.
 */
public final class AuctionOverlay {
	private AuctionOverlay() {}

	enum Kind { AUCTION_VIEW, CONFIRM }

	/** How long after opening an auction cursor assist may still act. */
	static final long CURSOR_WINDOW_MS = 8_000;
	/** The mouse counts as "moving" if it moved within this time. */
	static final long MOUSE_STILL_MS = 250;

	private static double lastMouseX = Double.NaN;
	private static double lastMouseY = Double.NaN;
	private static long lastMouseMoveAtMs;

	/** Called every client tick: remembers when the player last moved the mouse. */
	public static void trackMouse(Minecraft mc) {
		double x = mc.mouseHandler.xpos();
		double y = mc.mouseHandler.ypos();
		if (x != lastMouseX || y != lastMouseY) {
			if (!Double.isNaN(lastMouseX)) lastMouseMoveAtMs = System.currentTimeMillis();
			lastMouseX = x;
			lastMouseY = y;
		}
	}

	/** Per window: survives a resize (which runs init again on the same screen). */
	private static final java.util.Map<Screen, State> STATES = new java.util.WeakHashMap<>();
	/** The opened auction whose auction view / confirm window has already been used. */
	private static FlipService.Opened viewUsedFor;
	private static FlipService.Opened confirmUsedFor;
	/** The most recent auction view window, flip or not: a confirm window belongs to it. */
	private static State lastView;

	/** A window that isn't the opened flip's has no outline, panel, cursor assist or close key. */
	static final long DECIDE_WITHIN_MS = 3_000;
	/** Confirm must follow the flip's auction view within this time. */
	static final long CONFIRM_WITHIN_MS = 20_000;

	public static void register(FlipService service) {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			// mouse wheel scrolls a long Fates item tooltip (any container window)
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, hAmount, vAmount) ->
					!io.github.donttemptfate.fatesflipper.ui.ItemTooltips.scroll(vAmount));
			State state = STATES.get(screen);
			if (state == null) {
				Kind kind = kindOf(screen);
				if (kind == null) return;
				state = newState(service, kind, client);
				STATES.put(screen, state);
			}
			if (state.opened == null) return; // not a flip window: leave it completely alone
			State st = state;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
				try {
					decide(acs, st);
					if (st.match != Boolean.TRUE || !service.config().auctionOverlay) return;
					draw(service, acs, st, g, client.font);
				} catch (RuntimeException e) {
					// drawing must never crash the game
				}
			});
			ScreenEvents.afterTick(screen).register(s -> {
				try {
					decide(acs, st);
					if (st.match == Boolean.TRUE) cursorAssist(service, client, acs, st);
				} catch (RuntimeException e) {
					st.cursorDone = true;
				}
			});
			// Press an open-flip key again to close this flip's window (like Esc). One press, one action.
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
				try {
					if (st.match != Boolean.TRUE || !service.config().pressAgainToClose) return true;
					if (!matches(Keys.openBest, event) && !matches(Keys.openSafest, event)) return true;
					service.closeFlipWindow(s);
					return false;
				} catch (RuntimeException e) {
					return true;
				}
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				try {
					// left / right clicks are always the player's own. Named constants, not numbers:
					// 26.3 (SDL3) numbers mouse buttons differently from 26.2 (GLFW)
					int b = event.button();
					if (b == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT
							|| b == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) return true;
					if (st.match != Boolean.TRUE || !service.config().pressAgainToClose) return true;
					if (!matchesMouse(Keys.openBest, event) && !matchesMouse(Keys.openSafest, event)) return true;
					service.closeFlipWindow(s);
					return false;
				} catch (RuntimeException e) {
					return true;
				}
			});
		});
	}

	private static boolean matches(KeyMapping key, net.minecraft.client.input.KeyEvent event) {
		return key != null && !key.isUnbound() && key.matches(event);
	}

	private static boolean matchesMouse(KeyMapping key, net.minecraft.client.input.MouseButtonEvent event) {
		return key != null && !key.isUnbound() && key.matchesMouse(event);
	}

	/**
	 * A new window: a candidate when it may be the flip just opened (checked against the flip in
	 * {@link #decide} once Hypixel has filled it in), otherwise a "foreign" state that does nothing.
	 */
	private static State newState(FlipService service, Kind kind, Minecraft client) {
		long now = System.currentTimeMillis();
		double mx = client.mouseHandler.xpos();
		double my = client.mouseHandler.ypos();
		FlipService.Opened opened = service.lastOpened();
		if (kind == Kind.AUCTION_VIEW) {
			State st = opened == null || opened.flip() == null || opened == viewUsedFor
					? State.foreign(kind, now, mx, my)
					: new State(kind, opened, now, mx, my);
			lastView = st;
			return st;
		}
		State view = lastView;
		if (opened != null && view != null && view.opened == opened && view.match == Boolean.TRUE
				&& opened != confirmUsedFor && now - view.matchedAtMs <= CONFIRM_WITHIN_MS) {
			confirmUsedFor = opened;
			State st = new State(kind, opened, now, mx, my);
			st.match = Boolean.TRUE;
			st.matchedAtMs = now;
			return st;
		}
		return State.foreign(kind, now, mx, my);
	}

	/** Decides once whether an auction view is the flip that was opened (price on the button, else the item's name). */
	static void decide(AbstractContainerScreen<?> screen, State state) {
		if (state.match != null || state.opened == null) return;
		FlipEvent f = state.opened.flip();
		Slot button = button(screen);
		long price = button == null ? -1 : AuctionWindow.price(lore(button.getItem()));
		Slot item = itemSlot(screen);
		Boolean m = f == null ? Boolean.FALSE : AuctionWindow.matches(f.price(), f.name(), price, item == null ? "" : name(item.getItem()));
		long now = System.currentTimeMillis();
		if (m == null && now - state.openedScreenAtMs > DECIDE_WITHIN_MS) m = Boolean.FALSE;
		if (m == null) return;
		state.match = m;
		if (m) {
			state.matchedAtMs = now;
			viewUsedFor = state.opened;
		} else {
			FatesFlipperClient.LOG.info("auction window isn't the opened flip {}; leaving it alone", state.opened.auctionId());
		}
	}

	/** For the in-game test: TRUE when this window was recognised as the opened flip's. */
	public static Boolean debugMatch(Screen screen) {
		State st = screen == null ? null : STATES.get(screen);
		return st == null ? null : st.match;
	}

	static Kind kindOf(Screen screen) {
		String title = LegacyText.strip(screen.getTitle().getString()).trim();
		if (title.startsWith("Confirm Purchase")) return Kind.CONFIRM;
		if (title.contains("Auction View")) return Kind.AUCTION_VIEW; // "BIN Auction View"
		return null;
	}

	static final class State {
		final Kind kind;
		/** The flip this window may belong to; null = not a flip window. */
		final FlipService.Opened opened;
		final long openedScreenAtMs;
		final double mouseX0;
		final double mouseY0;
		boolean cursorDone;
		/** null until decided; TRUE = this is the opened flip's window. */
		Boolean match;
		long matchedAtMs;

		State(Kind kind, FlipService.Opened opened, long at, double mx, double my) {
			this.kind = kind;
			this.opened = opened;
			this.openedScreenAtMs = at;
			this.mouseX0 = mx;
			this.mouseY0 = my;
		}

		static State foreign(Kind kind, long at, double mx, double my) {
			State s = new State(kind, null, at, mx, my);
			s.match = Boolean.FALSE;
			s.cursorDone = true;
			return s;
		}
	}

	// ---- slot helpers ------------------------------------------------------------------------

	/** The window's own slots (not the player's inventory, which is the last 36). */
	static List<Slot> containerSlots(AbstractContainerScreen<?> screen) {
		List<Slot> all = screen.getMenu().slots;
		int n = Math.max(0, all.size() - 36);
		return all.subList(0, n);
	}

	static String name(ItemStack stack) {
		return stack.isEmpty() ? "" : LegacyText.strip(stack.getHoverName().getString()).trim();
	}

	static boolean isFiller(ItemStack stack) {
		return !stack.isEmpty() && name(stack).isEmpty();
	}

	/** The item's own lore lines as plain text. */
	static List<String> lore(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return List.of();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return List.of();
		List<String> out = new ArrayList<>();
		for (Component c : lore.lines()) out.add(LegacyText.strip(c.getString()).trim());
		return out;
	}

	/** The auction view's middle button ("Buy Item Right Now" / "Collect Auction"), else slot 31 if it isn't filler. */
	static Slot button(AbstractContainerScreen<?> screen) {
		List<Slot> slots = containerSlots(screen);
		for (Slot slot : slots) {
			if (AuctionWindow.isButtonName(name(slot.getItem()))) return slot;
		}
		if (slots.size() > 31) {
			ItemStack s = slots.get(31).getItem();
			if (!s.isEmpty() && !isFiller(s)) return slots.get(31);
		}
		return null;
	}

	/** The Confirm button in the confirm window. */
	static Slot confirmButton(AbstractContainerScreen<?> screen) {
		for (Slot slot : containerSlots(screen)) {
			if (name(slot.getItem()).toLowerCase(Locale.ROOT).startsWith("confirm")) return slot;
		}
		return null;
	}

	static ButtonState buttonState(Slot button) {
		return button == null ? null : AuctionWindow.classify(name(button.getItem()), lore(button.getItem()));
	}

	/** The item for sale: top centre of the auction view (slot 13). */
	static Slot itemSlot(AbstractContainerScreen<?> screen) {
		List<Slot> slots = containerSlots(screen);
		if (slots.size() > 13 && !slots.get(13).getItem().isEmpty()) return slots.get(13);
		return null;
	}

	// ---- drawing -----------------------------------------------------------------------------

	private static final int GOLD = 0xFFFFC23D;
	private static final int GREEN = 0xFF55FF77;
	private static final int ORANGE = 0xFFFFA040;
	private static final int RED = 0xFFFF5555;
	private static final int YELLOW = 0xFFFFE066;
	private static final int GREY = 0xFFAAAAAA;
	private static final int DIM = 0xA0000000;

	static void draw(FlipService service, AbstractContainerScreen<?> screen, State state, GuiGraphicsExtractor g, Font font) {
		AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
		int left = acc.fatesflipper$getLeftPos();
		int top = acc.fatesflipper$getTopPos();

		Slot target = state.kind == Kind.AUCTION_VIEW ? button(screen) : confirmButton(screen);
		ButtonState bs = state.kind == Kind.AUCTION_VIEW ? buttonState(target) : null;
		Slot item = state.kind == Kind.AUCTION_VIEW ? itemSlot(screen) : null;
		for (Slot slot : containerSlots(screen)) {
			if (slot == target || slot == item) continue;
			if (isFiller(slot.getItem())) g.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, DIM);
		}
		if (item != null) box(g, left + item.x, top + item.y, GOLD);
		if (target != null) box(g, left + target.x, top + target.y, state.kind == Kind.CONFIRM ? GREEN : color(bs));

		panel(service, screen, state, g, font, left, top, acc.fatesflipper$getImageWidth(), target, bs);
	}

	static int color(ButtonState bs) {
		if (bs == null) return GREY;
		return switch (bs) {
			case BUYABLE -> GREEN;
			case CANT_AFFORD -> ORANGE;
			case SOLD -> RED;
			case NOT_YET, UNKNOWN -> YELLOW;
		};
	}

	/** The panel's last line: what the button means, in words (not only colour). */
	static String stateText(ButtonState bs, Slot button, boolean closeKey) {
		if (bs == null) return io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.waiting_button");
		String close = closeKey ? " · " + io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.key_closes") : "";
		return switch (bs) {
			case BUYABLE -> io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.buy");
			case CANT_AFFORD -> io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.cant_afford");
			case SOLD -> io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.sold") + close;
			case NOT_YET -> io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.not_yet");
			case UNKNOWN -> io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.button", name(button.getItem()));
		};
	}

	private static void box(GuiGraphicsExtractor g, int x, int y, int color) {
		g.outline(x - 1, y - 1, 18, 18, color);
		g.outline(x - 2, y - 2, 20, 20, color);
	}

	private record Line(String text, int color) {}

	private static void panel(FlipService service, AbstractContainerScreen<?> screen, State state, GuiGraphicsExtractor g,
			Font font, int left, int top, int imageWidth, Slot target, ButtonState bs) {
		if (service.config().fatesLook) {
			fatesPanel(service, screen, state, g, left, top, imageWidth, target, bs);
			return;
		}
		classicPanel(service, screen, state, g, font, left, top, imageWidth, target, bs);
	}

	/** The side panel in the Fates look (design 2.1), shrunk to fit beside the window, never over it. */
	private static void fatesPanel(FlipService service, AbstractContainerScreen<?> screen, State state, GuiGraphicsExtractor g,
			int left, int top, int imageWidth, Slot target, ButtonState bs) {
		var card = io.github.donttemptfate.fatesflipper.ui.Cards.auctionPanel(state.opened.flip(), service.lastOpenedPrice(),
				state.kind == Kind.CONFIRM, bs, target == null ? "" : name(target.getItem()),
				service.config().cursorAssist, service.config().pressAgainToClose);
		int spaceRight = screen.width - (left + imageWidth) - 8;
		int spaceLeft = left - 8;
		// Left of the window by default: Hypixel's item tooltips open to the right of the cursor, so a
		// panel on the right would sit over them (Fate, 2026-10-17). Right only when the left is too small.
		boolean right = spaceLeft < card.width() * 0.8f && spaceRight > spaceLeft;
		int avail = right ? spaceRight : spaceLeft;
		if ((float) avail / card.width() < 0.8f) {
			card = io.github.donttemptfate.fatesflipper.ui.Cards.auctionPanel(state.opened.flip(), service.lastOpenedPrice(),
					state.kind == Kind.CONFIRM, bs, target == null ? "" : name(target.getItem()),
					service.config().cursorAssist, service.config().pressAgainToClose, true);
		}
		int w = card.width();
		int h = card.height();
		float scale = Math.min(1f, Math.min((float) avail / w, (screen.height - 8f) / h));
		if (scale < 0.45f) return; // no room beside the window: show nothing rather than cover it
		int sw = Math.round(w * scale);
		int sh = Math.round(h * scale);
		int x = right ? left + imageWidth + 6 : left - 6 - sw;
		int y = Math.max(4, Math.min(top, screen.height - sh - 4));
		card.draw(g, x, y, scale);
	}

	private static void classicPanel(FlipService service, AbstractContainerScreen<?> screen, State state, GuiGraphicsExtractor g,
			Font font, int left, int top, int imageWidth, Slot target, ButtonState bs) {
		List<Line> lines = new ArrayList<>();
		FlipEvent f = state.opened.flip();
		lines.add(new Line(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.head"), GOLD));
		if (f != null) {
			lines.add(new Line(LegacyText.strip(f.name()), 0xFFFFFFFF));
			lines.add(new Line(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.price", Coins.format(f.price())), 0xFFFFFF77));
			if (!Double.isNaN(f.resale())) lines.add(new Line(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.resale", Coins.format(f.resale())), 0xFFFFFF77));
			if (!Double.isNaN(f.safeProfit())) {
				String pct = Double.isNaN(f.safePct()) ? "" : " (" + Coins.percent(f.safePct()) + ")";
				lines.add(new Line(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.profit", (f.safeProfit() >= 0 ? "+" : "") + Coins.format(f.safeProfit()) + pct),
						f.safeProfit() >= 0 ? GREEN : 0xFFFF5555));
			}
			lines.add(new Line((f.confidence() == null ? "?" : f.confidence()) + (f.salesUsed() > 0 ? " · " + io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.sales", f.salesUsed()) : ""), 0xFFCCCCCC));
			StringBuilder meta = new StringBuilder();
			if (!Double.isNaN(f.volumePerDay())) meta.append(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.per_day", String.format(Locale.ROOT, "%.1f", f.volumePerDay())));
			if (f.firstSeenAt() > 0) meta.append(meta.length() > 0 ? " · " : "").append(Coins.age(System.currentTimeMillis() - f.firstSeenAt()));
			if (meta.length() > 0) lines.add(new Line(meta.toString(), 0xFFAAAAAA));
			if (f.dupe() != null) lines.add(new Line(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.dupe", f.dupe().toUpperCase(Locale.ROOT)), 0xFFFF5555));
		}
		JsonObject price = service.lastOpenedPrice();
		JsonArray sales = Json.arr(price, "recent_sales");
		if (sales != null && !sales.isEmpty()) {
			lines.add(new Line(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.recent"), 0xFFAAAAAA));
			int shown = 0;
			for (JsonElement e : sales) {
				if (shown >= 4 || !e.isJsonObject()) break;
				JsonObject s = e.getAsJsonObject();
				double p = Json.num(s, "price", Double.NaN);
				long ts = Json.lng(s, "ts", 0);
				boolean ignored = Json.bool(s, "ignored", false);
				String t = " " + Coins.format(p) + (ts > 0 ? " · " + Coins.age(System.currentTimeMillis() - ts) : "") + (ignored ? " ×" : "");
				lines.add(new Line(t, ignored ? 0xFF777777 : 0xFFDDDDDD));
				shown++;
			}
		} else if (price == null && f == null) {
			lines.add(new Line(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.loading"), 0xFF777777));
		}
		if (state.kind == Kind.AUCTION_VIEW) {
			lines.add(new Line(stateText(bs, target, service.config().pressAgainToClose), color(bs)));
		} else {
			lines.add(new Line(target == null ? io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.waiting_confirm") : io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.panel.click_confirm"), GREEN));
		}

		// Never cover the window: use the wider free side, and shorten lines to fit it.
		int pad = 3;
		int spaceRight = screen.width - (left + imageWidth) - 6;
		int spaceLeft = left - 6;
		boolean right = spaceRight >= spaceLeft;
		int avail = Math.max(0, (right ? spaceRight : spaceLeft) - 2);
		int maxText = avail - pad * 2;
		if (maxText < 40) return; // no room at all beside the window: show nothing rather than cover it
		int w = 0;
		List<Line> fitted = new ArrayList<>(lines.size());
		for (Line l : lines) {
			String t = fit(font, l.text(), maxText);
			fitted.add(new Line(t, l.color()));
			w = Math.max(w, font.width(t));
		}
		w += pad * 2;
		int lineH = font.lineHeight + 1;
		int maxLines = Math.max(1, (screen.height - 4 - pad * 2) / lineH);
		if (fitted.size() > maxLines) fitted = fitted.subList(0, maxLines);
		int h = fitted.size() * lineH + pad * 2;
		int x = right ? left + imageWidth + 4 : left - 4 - w;
		int y = Math.max(2, Math.min(top, screen.height - h - 2));
		g.fill(x, y, x + w, y + h, 0xD0101018);
		g.outline(x, y, w, h, 0xFF3A3A55);
		int ty = y + pad;
		for (Line l : fitted) {
			g.text(font, l.text(), x + pad, ty, l.color(), true);
			ty += lineH;
		}
	}

	/** Shortens text with "…" until it fits {@code maxWidth} pixels. */
	static String fit(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) return text;
		String t = text;
		while (t.length() > 1 && font.width(t + "…") > maxWidth) t = t.substring(0, t.length() - 1);
		return t + "…";
	}

	// ---- cursor assist -----------------------------------------------------------------------

	/**
	 * Places the cursor once on Buy (auction view) or Confirm (confirm window). Only when the
	 * player turned it on, only for the window of the flip just opened (checked against it), only
	 * onto a buyable (green) Buy button, and never if the player has moved the mouse since the
	 * window opened. No jitter, no clicks.
	 */
	static void cursorAssist(FlipService service, Minecraft mc, AbstractContainerScreen<?> screen, State state) {
		if (state.cursorDone || !service.config().cursorAssist) return;
		long now = System.currentTimeMillis();
		if (now - state.opened.atMs() > CURSOR_WINDOW_MS + (state.kind == Kind.CONFIRM ? 10_000 : 0)) {
			state.cursorDone = true;
			return;
		}
		double mx = mc.mouseHandler.xpos();
		double my = mc.mouseHandler.ypos();
		if (mx != state.mouseX0 || my != state.mouseY0 || now - lastMouseMoveAtMs < MOUSE_STILL_MS && lastMouseMoveAtMs > state.openedScreenAtMs) {
			state.cursorDone = true; // the player is using the mouse: leave it alone
			return;
		}
		Slot target;
		if (state.kind == Kind.AUCTION_VIEW) {
			target = button(screen);
			if (target == null) return; // the button hasn't arrived yet; try next tick
			ButtonState bs = buttonState(target);
			if (bs != ButtonState.BUYABLE) {
				if (bs == ButtonState.SOLD || bs == ButtonState.CANT_AFFORD) state.cursorDone = true;
				return; // only ever onto a buyable (green) button
			}
		} else {
			target = confirmButton(screen);
			if (target == null) return;
		}
		AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
		double guiX = acc.fatesflipper$getLeftPos() + target.x + 8;
		double guiY = acc.fatesflipper$getTopPos() + target.y + 8;
		var window = mc.getWindow();
		double sx = (double) window.getScreenWidth() / window.getGuiScaledWidth();
		double sy = (double) window.getScreenHeight() / window.getGuiScaledHeight();
		Compat.setCursor(mc, guiX * sx, guiY * sy);
		state.cursorDone = true;
	}
}
