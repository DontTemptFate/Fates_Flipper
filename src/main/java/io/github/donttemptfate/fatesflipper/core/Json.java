package io.github.donttemptfate.fatesflipper.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Defensive readers for viewer JSON: a missing or wrongly typed field never throws. */
public final class Json {
	private Json() {}

	public static JsonObject parseObject(String text) {
		try {
			JsonElement e = JsonParser.parseString(text);
			return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (Exception ex) {
			return null;
		}
	}

	public static JsonObject obj(JsonObject o, String key) {
		if (o == null) return null;
		JsonElement e = o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	public static JsonArray arr(JsonObject o, String key) {
		if (o == null) return null;
		JsonElement e = o.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
	}

	public static String str(JsonObject o, String key) {
		if (o == null) return null;
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return null;
		try {
			return e.getAsString();
		} catch (Exception ex) {
			return null;
		}
	}

	public static double num(JsonObject o, String key, double fallback) {
		if (o == null) return fallback;
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull() || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
		try {
			return e.getAsDouble();
		} catch (Exception ex) {
			return fallback;
		}
	}

	public static long lng(JsonObject o, String key, long fallback) {
		double d = num(o, key, Double.NaN);
		return Double.isNaN(d) ? fallback : (long) d;
	}

	public static boolean bool(JsonObject o, String key, boolean fallback) {
		if (o == null) return fallback;
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull() || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) return fallback;
		return e.getAsBoolean();
	}

	/** True when the key exists and is a JSON boolean. */
	public static boolean hasBool(JsonObject o, String key) {
		if (o == null) return false;
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean();
	}
}
