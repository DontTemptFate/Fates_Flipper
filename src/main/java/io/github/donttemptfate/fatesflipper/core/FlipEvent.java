package io.github.donttemptfate.fatesflipper.core;

import io.github.donttemptfate.fatescommon.text.Coins;
import io.github.donttemptfate.fatescommon.text.LegacyText;

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
		String confReason,
		/** The seller's Minecraft name (card {@code seller_name}, checked), or null. */
		String seller,
		/** The item's lore as § text (card {@code lore}, at most 40 lines, cleaned), empty when not sent. */
		java.util.List<String> lore,
		/** The card's {@code item_id} / {@code category} (for mutes), checked; or null. */
		String itemId,
		String category) {

	static final java.util.regex.Pattern PLAYER = java.util.regex.Pattern.compile("^[A-Za-z0-9_]{1,16}$");

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
				clean(Json.str(est, "conf_reason")),
				player(Json.str(card, "seller_name")),
				lore(Json.arr(card, "lore")),
				key(Json.str(card, "item_id")),
				key(Json.str(card, "category")));
	}

	static final java.util.regex.Pattern KEY = java.util.regex.Pattern.compile("^[A-Za-z0-9_:;.\\-]{1,64}$");

	private static String key(String s) {
		return s != null && KEY.matcher(s).matches() ? s : null;
	}

	private static String player(String s) {
		return s != null && PLAYER.matcher(s).matches() ? s : null;
	}

	private static java.util.List<String> lore(com.google.gson.JsonArray a) {
		if (a == null) return java.util.List.of();
		java.util.List<String> out = new java.util.ArrayList<>();
		for (com.google.gson.JsonElement e : a) {
			if (out.size() >= 40) break;
			if (e.isJsonPrimitive()) out.add(LegacyText.sanitize(e.getAsString()));
		}
		return java.util.List.copyOf(out);
	}

	/** The short line for the profile setting "just profit": name and cautious profit. */
	public String profitChat() {
		double p = Double.isNaN(safeProfit) ? profit : safeProfit;
		StringBuilder sb = new StringBuilder("§6FLIP §f").append(name);
		if (!Double.isNaN(p)) sb.append(p >= 0 ? " §a+" : " §c").append(Coins.format(p));
		if (dupe != null) sb.append(" §c§lDUPED?!");
		return sb.toString();
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
		if (dupe != null) sb.append(" §c§lDUPED?!");
		return sb.toString();
	}
}
