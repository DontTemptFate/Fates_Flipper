package io.github.donttemptfate.fatesflipper.core;

import com.google.gson.JsonObject;
import io.github.donttemptfate.fatescommon.text.LegacyText;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Item traits and creation date from a {@code /mod/price} answer (collector v0.9.20 via the viewer:
 * {@code traits}, {@code created_at}; handoff-collector-to-mod-v0.9.20). Display only, never part of a
 * flip decision; the mod never works these out itself. Every value is checked and shortened.
 */
public final class ItemTraits {
	private ItemTraits() {}

	/** One tooltip line: an optional colour square ({@code swatch} = RGB, or -1), the text, and its colour. */
	public record Line(int swatch, String text, int rgb, String badge) {}

	static final Pattern HEX = Pattern.compile("^[0-9A-Fa-f]{6}$");
	static final int GRAY = 0xAAAAAA;
	static final int BADGE = 0xFF55FF;

	public static List<Line> lines(JsonObject price, ZoneId zone) {
		List<Line> out = new ArrayList<>();
		if (price == null) return out;
		JsonObject t = Json.obj(price, "traits");
		if (t != null) {
			JsonObject c = Json.obj(t, "colour");
			String hex = Json.str(c, "hex");
			if (hex != null && HEX.matcher(hex).matches()) {
				String label = clean(Json.str(c, "label"));
				String text = "#" + hex.toUpperCase(Locale.ROOT) + (label == null ? "" : " · " + label);
				String badge = Json.bool(c, "collector", false) ? Tr.tr("fatesflipper.traits.collector")
						: Json.bool(c, "exotic", false) ? Tr.tr("fatesflipper.traits.exotic") : null;
				out.add(new Line(Integer.parseInt(hex, 16), text, GRAY, badge));
			}
			String dye = clean(Json.str(t, "dye"));
			if (dye != null) out.add(new Line(-1, Tr.tr("fatesflipper.traits.dyed", dye), GRAY, null));
			long edition = Json.lng(t, "edition", 0);
			long year = Json.lng(t, "year", 0);
			if (edition > 0 && year > 0) out.add(new Line(-1, Tr.tr("fatesflipper.traits.edition_year", edition, year), GRAY, null));
			else if (edition > 0) out.add(new Line(-1, Tr.tr("fatesflipper.traits.edition", edition), GRAY, null));
			else if (year > 0) out.add(new Line(-1, Tr.tr("fatesflipper.traits.year", year), GRAY, null));
			JsonObject rod = Json.obj(t, "rod");
			String hook = clean(Json.str(rod, "hook"));
			String line = clean(Json.str(rod, "line"));
			if (hook != null || line != null) {
				out.add(new Line(-1, hook != null && line != null ? hook + " · " + line : hook != null ? hook : line, GRAY, null));
			}
			if (Json.bool(t, "evolved", false)) out.add(new Line(-1, Tr.tr("fatesflipper.traits.evolved"), GRAY, null));
			if (Json.bool(t, "maxed", false)) out.add(new Line(-1, Tr.tr("fatesflipper.traits.maxed"), GRAY, null));
			long handles = Json.lng(t, "handles", 0);
			if (handles > 0 && handles < 100) out.add(new Line(-1, Tr.tr("fatesflipper.traits.handles", handles), GRAY, null));
		}
		long created = Json.lng(price, "created_at", 0);
		// 2019-06 (SkyBlock's start) to now + a day: anything else is ignored
		if (created > 1_559_347_200_000L && created < System.currentTimeMillis() + 86_400_000L) {
			String when = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH).format(Instant.ofEpochMilli(created).atZone(zone));
			out.add(new Line(-1, Tr.tr("fatesflipper.traits.made", when), GRAY, null));
		}
		return out;
	}

	private static String clean(String s) {
		if (s == null) return null;
		String t = LegacyText.strip(s).trim();
		if (t.isEmpty()) return null;
		return t.length() > 40 ? t.substring(0, 40) : t;
	}
}
