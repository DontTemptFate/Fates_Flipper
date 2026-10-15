package io.github.donttemptfate.fatesflipper.core;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Auction ids and the one command the mod ever sends because of server data.
 *
 * <p>Safety rule: data from the viewer is untrusted. The only command built from it is
 * {@code /viewauction <id>}, and only when the id is exactly 32 lowercase hex characters.
 */
public final class AuctionIds {
	private static final Pattern HEX32 = Pattern.compile("^[0-9a-f]{32}$");

	private AuctionIds() {}

	/** True when {@code id} is exactly 32 lowercase hex characters. */
	public static boolean isValid(String id) {
		return id != null && HEX32.matcher(id).matches();
	}

	/**
	 * Accepts an id with or without dashes and in any case; returns the canonical 32-hex form, or
	 * {@code null} when it isn't a valid id.
	 */
	public static String normalize(String raw) {
		if (raw == null) return null;
		String s = raw.trim().replace("-", "").toLowerCase(Locale.ROOT);
		return isValid(s) ? s : null;
	}

	/** The command text (without the leading slash) for opening an auction. */
	public static String viewAuctionCommand(String id) {
		if (!isValid(id)) throw new IllegalArgumentException("not an auction id");
		return "viewauction " + id;
	}

	/**
	 * True when a server-supplied command is exactly {@code /viewauction <expectedId>} (slash
	 * optional). Anything else is refused.
	 */
	public static boolean isExpectedViewCommand(String command, String expectedId) {
		if (command == null || !isValid(expectedId)) return false;
		String c = command.trim();
		if (c.startsWith("/")) c = c.substring(1);
		return c.equals("viewauction " + expectedId);
	}
}
