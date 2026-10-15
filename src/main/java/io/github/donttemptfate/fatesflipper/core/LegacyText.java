package io.github.donttemptfate.fatesflipper.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text with legacy Minecraft colour codes (§a, §l, …) into styled runs.
 *
 * <p>Only colours and formatting are understood. Nothing in the text can create click or hover
 * actions: those are added by the mod itself, never taken from server data.
 */
public final class LegacyText {
	private LegacyText() {}

	/** Longest text accepted from the server for one chat line. */
	public static final int MAX_CHARS = 600;

	/**
	 * One styled piece of text. {@code color} is the code character (0-9, a-f) or 0 for the
	 * default colour.
	 */
	public record Run(String text, char color, boolean bold, boolean italic, boolean underlined,
			boolean strikethrough, boolean obfuscated) {}

	public static List<Run> parse(String input) {
		List<Run> runs = new ArrayList<>();
		if (input == null || input.isEmpty()) return runs;
		String s = sanitize(input);
		StringBuilder cur = new StringBuilder();
		char color = 0;
		boolean bold = false, italic = false, underlined = false, strike = false, obf = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				char code = Character.toLowerCase(s.charAt(i + 1));
				boolean isColor = (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
				boolean isFormat = "klmnor".indexOf(code) >= 0;
				if (isColor || isFormat) {
					if (cur.length() > 0) {
						runs.add(new Run(cur.toString(), color, bold, italic, underlined, strike, obf));
						cur.setLength(0);
					}
					if (isColor) {
						color = code;
						bold = italic = underlined = strike = obf = false;
					} else {
						switch (code) {
							case 'k' -> obf = true;
							case 'l' -> bold = true;
							case 'm' -> strike = true;
							case 'n' -> underlined = true;
							case 'o' -> italic = true;
							case 'r' -> {
								color = 0;
								bold = italic = underlined = strike = obf = false;
							}
							default -> {
							}
						}
					}
					i++;
					continue;
				}
			}
			if (c == '§') continue; // stray section sign at the end
			cur.append(c);
		}
		if (cur.length() > 0) runs.add(new Run(cur.toString(), color, bold, italic, underlined, strike, obf));
		return runs;
	}

	/** Removes all colour codes. */
	public static String strip(String input) {
		StringBuilder sb = new StringBuilder();
		for (Run r : parse(input)) sb.append(r.text());
		return sb.toString();
	}

	/** One line, no control characters, bounded length. */
	static String sanitize(String input) {
		StringBuilder sb = new StringBuilder(Math.min(input.length(), MAX_CHARS));
		for (int i = 0; i < input.length() && sb.length() < MAX_CHARS; i++) {
			char c = input.charAt(i);
			if (c == '\n' || c == '\r' || c == '\t') sb.append(' ');
			else if (c < 0x20 || c == 0x7f) continue;
			else sb.append(c);
		}
		return sb.toString();
	}
}
