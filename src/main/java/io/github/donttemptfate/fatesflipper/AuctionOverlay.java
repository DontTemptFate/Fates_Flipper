package io.github.donttemptfate.fatesflipper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.donttemptfate.fatesflipper.core.Coins;
import io.github.donttemptfate.fatesflipper.core.FlipEvent;
import io.github.donttemptfate.fatesflipper.core.Json;
import io.github.donttemptfate.fatesflipper.core.LegacyText;
import io.github.donttemptfate.fatesflipper.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

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

	public static void register(FlipService service) {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			Kind kind = kindOf(screen);
			if (kind == null) return;
			FlipService.Opened opened = service.lastOpened();
			if (opened == null) return; // only auctions opened from a flip
			State state = new State(kind, opened, System.currentTimeMillis(), client.mouseHandler.xpos(), client.mouseHandler.ypos());
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
				if (service.config().auctionOverlay) draw(service, acs, state, g, client.font);
			});
			ScreenEvents.afterTick(screen).register(s -> cursorAssist(service, client, acs, state));
		});
	}

	static Kind kindOf(Screen screen) {
		String title = LegacyText.strip(screen.getTitle().getString()).trim();
		if (title.startsWith("Confirm Purchase")) return Kind.CONFIRM;
		if (title.contains("Auction View")) return Kind.AUCTION_VIEW; // "BIN Auction View"
		return null;
	}

	static final class State {
		final Kind kind;
		final FlipService.Opened opened;
		final long openedScreenAtMs;
		final double mouseX0;
		final double mouseY0;
		boolean cursorDone;

		State(Kind kind, FlipService.Opened opened, long at, double mx, double my) {
			this.kind = kind;
			this.opened = opened;
			this.openedScreenAtMs = at;
			this.mouseX0 = mx;
			this.mouseY0 = my;
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

	/** The button to highlight: "Buy Item Right Now" in the auction view, "Confirm" in the confirm window. */
	static Slot target(AbstractContainerScreen<?> screen, Kind kind) {
		for (Slot slot : containerSlots(screen)) {
			String n = name(slot.getItem()).toLowerCase(Locale.ROOT);
			if (kind == Kind.AUCTION_VIEW && n.startsWith("buy item")) return slot;
			if (kind == Kind.CONFIRM && n.startsWith("confirm")) return slot;
		}
		return null;
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
	private static final int DIM = 0xA0000000;

	static void draw(FlipService service, AbstractContainerScreen<?> screen, State state, GuiGraphicsExtractor g, Font font) {
		AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
		int left = acc.fatesflipper$getLeftPos();
		int top = acc.fatesflipper$getTopPos();

		Slot target = target(screen, state.kind);
		Slot item = state.kind == Kind.AUCTION_VIEW ? itemSlot(screen) : null;
		for (Slot slot : containerSlots(screen)) {
			if (slot == target || slot == item) continue;
			if (isFiller(slot.getItem())) g.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, DIM);
		}
		if (item != null) box(g, left + item.x, top + item.y, GOLD);
		if (target != null) box(g, left + target.x, top + target.y, GREEN);

		panel(service, screen, state, g, font, left, top, acc.fatesflipper$getImageWidth(), target);
	}

	private static void box(GuiGraphicsExtractor g, int x, int y, int color) {
		g.outline(x - 1, y - 1, 18, 18, color);
		g.outline(x - 2, y - 2, 20, 20, color);
	}

	private record Line(String text, int color) {}

	private static void panel(FlipService service, AbstractContainerScreen<?> screen, State state, GuiGraphicsExtractor g,
			Font font, int left, int top, int imageWidth, Slot target) {
		List<Line> lines = new ArrayList<>();
		FlipEvent f = state.opened.flip();
		lines.add(new Line("Fates Flipper", GOLD));
		if (f != null) {
			lines.add(new Line(LegacyText.strip(f.name()), 0xFFFFFFFF));
			lines.add(new Line("Price " + Coins.format(f.price()), 0xFFFFFF77));
			if (!Double.isNaN(f.resale())) lines.add(new Line("Resale " + Coins.format(f.resale()), 0xFFFFFF77));
			if (!Double.isNaN(f.safeProfit())) {
				String pct = Double.isNaN(f.safePct()) ? "" : " (" + Coins.percent(f.safePct()) + ")";
				lines.add(new Line("Profit " + (f.safeProfit() >= 0 ? "+" : "") + Coins.format(f.safeProfit()) + pct,
						f.safeProfit() >= 0 ? GREEN : 0xFFFF5555));
			}
			lines.add(new Line((f.confidence() == null ? "?" : f.confidence()) + (f.salesUsed() > 0 ? " · " + f.salesUsed() + " sales" : ""), 0xFFCCCCCC));
			StringBuilder meta = new StringBuilder();
			if (!Double.isNaN(f.volumePerDay())) meta.append(String.format(Locale.ROOT, "%.1f/day", f.volumePerDay()));
			if (f.firstSeenAt() > 0) meta.append(meta.length() > 0 ? " · " : "").append(Coins.age(System.currentTimeMillis() - f.firstSeenAt()));
			if (meta.length() > 0) lines.add(new Line(meta.toString(), 0xFFAAAAAA));
			if (f.dupe() != null) lines.add(new Line("DUPE: " + f.dupe().toUpperCase(Locale.ROOT), 0xFFFF5555));
		}
		JsonObject price = service.lastOpenedPrice();
		JsonArray sales = Json.arr(price, "recent_sales");
		if (sales != null && !sales.isEmpty()) {
			lines.add(new Line("Recent sales", 0xFFAAAAAA));
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
			lines.add(new Line("Loading…", 0xFF777777));
		}
		if (state.kind == Kind.AUCTION_VIEW) {
			lines.add(target == null ? new Line("Waiting for Buy…", 0xFFAAAAAA) : new Line(name(target.getItem()), GREEN));
		} else {
			lines.add(new Line(target == null ? "Waiting for Confirm…" : "Click Confirm", GREEN));
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
	 * player turned it on, only for an auction opened from a flip a few seconds ago, and never if
	 * the player has moved the mouse since the window opened. No jitter, no clicks.
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
		Slot target = target(screen, state.kind);
		if (target == null) return; // the button hasn't arrived yet; try next tick
		AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
		double guiX = acc.fatesflipper$getLeftPos() + target.x + 8;
		double guiY = acc.fatesflipper$getTopPos() + target.y + 8;
		var window = mc.getWindow();
		double sx = (double) window.getScreenWidth() / window.getGuiScaledWidth();
		double sy = (double) window.getScreenHeight() / window.getGuiScaledHeight();
		GLFW.glfwSetCursorPos(window.handle(), guiX * sx, guiY * sy);
		state.cursorDone = true;
	}
}
