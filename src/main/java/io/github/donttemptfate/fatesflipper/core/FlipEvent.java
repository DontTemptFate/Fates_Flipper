package io.github.donttemptfate.fatesflipper.core;

import com.google.gson.JsonObject;

/**
 * One flip from the viewer (FlipEvent, viewer contract §4.3), reduced to what the mod shows.
 * Built with {@link #parse}; returns {@code null} unless the auction id is valid.
 */
public record FlipEvent(
		String auctionId,
		String name,
		String rarity,
		String summary,
		double price,
		double profit,
		double safeProfit,
		double safePct,
		double resale,
		double safeResale,
		String confidence,
		double volumePerDay,
		int salesUsed,
		String dupe,
		long firstSeenAt,
		double expectedProfit,
		double chance,
		String chat,
		boolean commandMatches,
		String profileName,
		/** Why confidence isn't HIGH ("wide prices", "few sales", …; viewer v0.28.1), or null. */
		String confReason) {

	public static FlipEvent parse(JsonObject flip) {
		if (flip == null) return null;
		JsonObject card = Json.obj(flip, "card");
		if (card == null) return null;
		String id = AuctionIds.normalize(Json.str(card, "uuid"));
		if (id == null) return null;
		JsonObject est = Json.obj(card, "estimate");
		JsonObject exact = Json.obj(est, "exact");
		JsonObject score = Json.obj(flip, "score");
		if (score == null) score = Json.obj(card, "score");
		JsonObject profile = Json.obj(flip, "profile");
		String command = Json.str(flip, "command");
		boolean matches = command == null || AuctionIds.isExpectedViewCommand(command, id);
		String name = Json.str(card, "name");
		return new FlipEvent(
				id,
				name == null ? "Unknown item" : LegacyText.sanitize(name),
				Json.str(card, "rarity"),
				Json.str(card, "summary"),
				Json.num(card, "price", Double.NaN),
				Json.num(card, "profit", Double.NaN),
				Json.num(card, "safe_profit", Double.NaN),
				Json.num(card, "safe_pct", Double.NaN),
				Json.num(est, "resale", Double.NaN),
				Json.num(card, "safe_resale", Double.NaN),
				clean(Json.str(est, "confidence")),
				Json.num(est, "volume_per_day", Double.NaN),
				(int) Json.num(exact, "n_used", 0),
				clean(Json.str(card, "dupe")),
				Json.lng(card, "first_seen_at", 0),
				Json.num(score, "exp", Double.NaN),
				Json.num(score, "chance", Double.NaN),
				Json.str(flip, "chat"),
				matches,
				Json.str(profile, "name"),
				clean(Json.str(est, "conf_reason")));
	}

	private static String clean(String s) {
		if (s == null) return null;
		String t = LegacyText.strip(s);
		return t.length() > 24 ? t.substring(0, 24) : t;
	}

	/** A plain chat line for when the viewer sent no formatted {@code chat} text. */
	public String fallbackChat() {
		StringBuilder sb = new StringBuilder("§6FLIP §f").append(name).append(" §7for §e").append(Coins.format(price));
		if (!Double.isNaN(safeProfit)) sb.append(" §7cautious profit §a+").append(Coins.format(safeProfit));
		else if (!Double.isNaN(profit)) sb.append(" §7profit §a+").append(Coins.format(profit));
		if (confidence != null) sb.append(" §8").append(confidence);
		if (dupe != null) sb.append(" §c[").append(dupe.toUpperCase(java.util.Locale.ROOT)).append("]");
		return sb.toString();
	}
}
