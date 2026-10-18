package io.github.donttemptfate.fatesflipper.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The mod's text, looked up by key (audit B-6). The English text lives in
 * {@code assets/fatesflipper/lang/en_us.json}; in game, the player's language (and resource packs) can
 * replace any line. Placeholders are {@code %s} / {@code %1$s} (Java format). Works without the game too
 * (unit tests read the same file), and a missing key shows the key itself, never crashes.
 */
public final class Tr {
	private Tr() {}

	static final String FILE = "assets/fatesflipper/lang/en_us.json";
	private static final Map<String, String> ENGLISH = load();
	/** Set by the game side: the current language's text for a key, or null when it has none. */
	private static volatile Function<String, String> lookup = k -> null;

	public static void setLookup(Function<String, String> f) {
		lookup = f == null ? k -> null : f;
	}

	public static String tr(String key, Object... args) {
		String s = null;
		try {
			s = lookup.apply(key);
		} catch (RuntimeException ignored) {
			// fall back to English
		}
		if (s == null || s.equals(key)) s = ENGLISH.getOrDefault(key, key);
		if (args.length == 0) return s;
		try {
			return String.format(Locale.ROOT, s, args);
		} catch (RuntimeException e) {
			return s; // a broken translation never breaks a message
		}
	}

	/** True when the English file has this key (for tests). */
	static boolean has(String key) {
		return ENGLISH.containsKey(key);
	}

	private static Map<String, String> load() {
		Map<String, String> m = new HashMap<>();
		try (InputStream in = Tr.class.getClassLoader().getResourceAsStream(FILE)) {
			if (in == null) return m;
			JsonObject o = com.google.gson.JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				if (e.getValue().isJsonPrimitive()) m.put(e.getKey(), e.getValue().getAsString());
			}
		} catch (Exception ignored) {
			// no file: keys show as themselves
		}
		return m;
	}
}
