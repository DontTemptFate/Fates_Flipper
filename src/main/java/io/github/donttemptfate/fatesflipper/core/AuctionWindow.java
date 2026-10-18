package io.github.donttemptfate.fatesflipper.core;

import io.github.donttemptfate.fates.common.text.LegacyText;

import java.util.List;
import java.util.Locale;

/**
 * Reading Hypixel's auction windows (plain text only, unit-tested). Button names and lines checked
 * in game on 26.3 (Fate, 2026-10-17):
 * <ul>
 *   <li>buyable: "Buy Item Right Now", lines "Price: 499,999 coins", "Click to purchase!"</li>
 *   <li>can't afford: "Buy Item Right Now", lines "Price: …", "Cannot afford bid!"</li>
 *   <li>sold: "Collect Auction", line "Someone else purchased the item!"</li>
 * </ul>
 * Anything else is reported as {@link ButtonState#UNKNOWN} with its own name, never guessed.
 */
public final class AuctionWindow {
	private AuctionWindow() {}

	public enum ButtonState {
		/** Green: buy it now. */
		BUYABLE,
		/** Orange: not enough coins. */
		CANT_AFFORD,
		/** Red: someone else bought it, or it's gone. */
		SOLD,
		/** Yellow: a buy button that isn't buyable (yet), e.g. a new-listing wait. */
		NOT_YET,
		/** Yellow: a button we don't recognise; shown with its real name. */
		UNKNOWN
	}

	/** True for the names the middle button of an auction view can have. */
	public static boolean isButtonName(String name) {
		String n = lower(name);
		return n.startsWith("buy item") || n.startsWith("collect auction");
	}

	/** What the auction view's middle button says. {@code name} and {@code lore} are plain text. */
	public static ButtonState classify(String name, List<String> lore) {
		String n = lower(name);
		String all = lower(String.join("\n", lore == null ? List.of() : lore));
		if (n.startsWith("collect auction") || all.contains("someone else purchased") || all.contains("this auction has ended")) {
			return ButtonState.SOLD;
		}
		if (n.startsWith("buy item")) {
			if (all.contains("cannot afford") || all.contains("can't afford") || all.contains("not enough coins")) return ButtonState.CANT_AFFORD;
			if (all.contains("click to purchase")) return ButtonState.BUYABLE;
			return ButtonState.NOT_YET;
		}
		return ButtonState.UNKNOWN;
	}

	/** The coins on a "Price: 499,999 coins" line, or -1 when there's none. */
	public static long price(List<String> lore) {
		if (lore == null) return -1;
		for (String line : lore) {
			String l = line == null ? "" : line.trim();
			if (!lower(l).startsWith("price:")) continue;
			StringBuilder digits = new StringBuilder();
			boolean started = false;
			for (char c : l.substring(6).toCharArray()) {
				if (Character.isDigit(c)) {
					digits.append(c);
					started = true;
				} else if (c == ',' && started) {
					// thousands separator
				} else if (started) {
					break;
				}
			}
			if (digits.isEmpty() || digits.length() > 15) return -1;
			return Long.parseLong(digits.toString());
		}
		return -1;
	}

	/**
	 * Is this window the flip that was opened? {@code TRUE} / {@code FALSE}, or {@code null} while
	 * the window hasn't filled in yet. The price on the button decides when it's there (a BIN's
	 * price never changes); otherwise the item's name.
	 */
	public static Boolean matches(double flipPrice, String flipName, long buttonPrice, String itemName) {
		String a = normalize(flipName);
		String b = normalize(itemName);
		Boolean byName = a.isEmpty() || b.isEmpty() ? null : (a.contains(b) || b.contains(a));
		if (buttonPrice >= 0 && flipPrice > 0) {
			boolean byPrice = Math.abs(buttonPrice - flipPrice) <= Math.max(1.0, flipPrice * 0.001);
			// cheap items often share a price: when the item's name is readable it must match too
			// (audit F-R3); price alone only while the item slot is still empty
			if (!byPrice) return Boolean.FALSE;
			return byName == null ? Boolean.TRUE : byName;
		}
		return byName;
	}

	/** Lower case, letters and digits only, single spaces: "✪✪ Heroic Hyperion ✪" → "heroic hyperion". */
	public static String normalize(String s) {
		if (s == null) return "";
		String t = LegacyText.strip(s).toLowerCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder(t.length());
		boolean space = false;
		for (char c : t.toCharArray()) {
			if (Character.isLetterOrDigit(c)) {
				if (space && !sb.isEmpty()) sb.append(' ');
				sb.append(c);
				space = false;
			} else {
				space = true;
			}
		}
		return sb.toString();
	}

	private static String lower(String s) {
		return s == null ? "" : LegacyText.strip(s).trim().toLowerCase(Locale.ROOT);
	}
}
