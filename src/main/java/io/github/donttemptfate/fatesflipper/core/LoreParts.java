package io.github.donttemptfate.fatesflipper.core;

import java.util.List;
import java.util.Locale;

/**
 * Reads the parts of a SkyBlock item's lore that aren't the item itself (plain text, colour codes
 * already removed):
 * <ul>
 *   <li>Hypixel's auction listing lines in the Auctions Browser (a separator, then Seller, Buy it
 *       now / Starting bid, Ends in, Click to inspect…). "Ends in" changes every second, so these
 *       lines must never be part of a price lookup or its cache key;</li>
 *   <li>Bazaar products ("Common commodity", "Buy price: … / Sell price: …").</li>
 * </ul>
 */
public final class LoreParts {
	private LoreParts() {}

	static final String[] LISTING_PREFIXES = {
			"seller:", "buy it now:", "starting bid:", "top bid:", "bids:", "bidder:", "ends in:", "ended",
			"ending soon", "status:", "sold for:", "buyer:", "item price:", "click to inspect", "auctioneer:"};

	/**
	 * Index of the first line of Hypixel's listing footer (its separator when it has one), or
	 * {@code -1} when the lore has no listing lines.
	 */
	public static int listingStart(List<String> plain) {
		int firstListing = -1;
		for (int i = 0; i < plain.size(); i++) {
			if (isListingLine(plain.get(i))) {
				firstListing = i;
				break;
			}
		}
		if (firstListing < 0) return -1;
		// the separator right above the listing lines (blank lines may sit between)
		for (int i = firstListing - 1; i >= 0; i--) {
			String p = plain.get(i).trim();
			if (p.isEmpty()) continue;
			return isSeparator(p) ? i : trimBlanks(plain, firstListing);
		}
		return trimBlanks(plain, firstListing);
	}

	private static int trimBlanks(List<String> plain, int at) {
		while (at > 0 && plain.get(at - 1).trim().isEmpty()) at--;
		return at;
	}

	static boolean isListingLine(String line) {
		String p = line.trim().toLowerCase(Locale.ROOT);
		for (String prefix : LISTING_PREFIXES) if (p.startsWith(prefix)) return true;
		return false;
	}

	/** A line of only dashes / bars (Hypixel draws it as struck-through dashes). */
	public static boolean isSeparator(String line) {
		String p = line.trim();
		int dashes = 0;
		for (char c : p.toCharArray()) {
			if ("-━▬─⎯=_".indexOf(c) >= 0) dashes++;
			else if (c != ' ') return false;
		}
		return dashes >= 8;
	}

	/** A Bazaar product's menu item ("Common commodity", or both a "Buy price:" and a "Sell price:" line). */
	public static boolean isBazaarProduct(List<String> plain) {
		boolean buy = false;
		boolean sell = false;
		for (String line : plain) {
			String p = line.trim().toLowerCase(Locale.ROOT);
			if (p.endsWith(" commodity") || p.equals("commodity")) return true;
			if (p.startsWith("buy price:")) buy = true;
			if (p.startsWith("sell price:")) sell = true;
		}
		return buy && sell;
	}
}
