package io.github.donttemptfate.fatesflipper.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mod version numbers: {@code MAJOR.MINOR.PATCH}, optionally {@code -beta.N} (a beta comes before its release). */
public final class Versions {
	private Versions() {}

	static final Pattern V = Pattern.compile("^v?(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})(?:-(?:beta|b)\\.?(\\d{1,4}))?(?:\\+.*)?$");

	/** True for a version string the mod understands. */
	public static boolean valid(String v) {
		return v != null && V.matcher(v.trim()).matches();
	}

	/**
	 * Negative if {@code a} is older than {@code b}, 0 if the same, positive if newer. Unreadable
	 * versions compare as equal, so a garbled server value never blocks anyone.
	 */
	public static int compare(String a, String b) {
		if (!valid(a) || !valid(b)) return 0;
		Matcher x = V.matcher(a.trim());
		Matcher y = V.matcher(b.trim());
		x.matches();
		y.matches();
		for (int i = 1; i <= 3; i++) {
			int c = Integer.compare(Integer.parseInt(x.group(i)), Integer.parseInt(y.group(i)));
			if (c != 0) return c;
		}
		// 0.3.4-beta.2 < 0.3.4
		int bx = x.group(4) == null ? Integer.MAX_VALUE : Integer.parseInt(x.group(4));
		int by = y.group(4) == null ? Integer.MAX_VALUE : Integer.parseInt(y.group(4));
		return Integer.compare(bx, by);
	}

	/** The mod's own version without the Minecraft suffix: "0.3.4+mc26.3" → "0.3.4". */
	public static String plain(String modVersion) {
		if (modVersion == null) return "";
		int plus = modVersion.indexOf('+');
		return plus < 0 ? modVersion : modVersion.substring(0, plus);
	}

	/** A download link from the server, shown as text only: https, short, no colour codes; else null. */
	public static String safeUrl(String url) {
		if (url == null) return null;
		String u = LegacyText.strip(url).trim();
		if (u.length() > 200 || !u.startsWith("https://") || u.chars().anyMatch(c -> c <= ' ' || c == '"')) return null;
		return u;
	}
}
