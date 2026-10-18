package io.github.donttemptfate.fatesflipper.core;

import java.util.Locale;

/** Short coin amounts: 950, 12.5k, 1.23M, 4.1B. */
public final class Coins {
	private Coins() {}

	public static String format(double coins) {
		if (Double.isNaN(coins) || Double.isInfinite(coins)) return "?";
		double a = Math.abs(coins);
		String sign = coins < 0 ? "-" : "";
		// round first, then pick the unit: 999,999.5 is "1M", not "1000k" (audit F-R7)
		double[] units = {1e9, 1e6, 1e3, 1};
		String[] names = {"B", "M", "k", ""};
		for (int i = 0; i < units.length; i++) {
			if (a < units[i] && i < units.length - 1) continue;
			String shown = i == units.length - 1 ? String.format(Locale.ROOT, "%.0f", a) : trim(a / units[i]);
			if (i > 0 && Double.parseDouble(shown) >= 1000) return sign + trim(a / units[i - 1]) + names[i - 1];
			return sign + shown + names[i];
		}
		return sign + "0";
	}

	private static String trim(double v) {
		String s;
		if (v >= 100) s = String.format(Locale.ROOT, "%.0f", v);
		else if (v >= 10) s = String.format(Locale.ROOT, "%.1f", v);
		else s = String.format(Locale.ROOT, "%.2f", v);
		if (s.contains(".")) {
			s = s.replaceAll("0+$", "");
			if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	/** "12s", "4m", "2h 5m". */
	public static String age(long ms) {
		if (ms < 0) ms = 0;
		long s = ms / 1000;
		if (s < 60) return s + "s";
		long m = s / 60;
		if (m < 60) return m + "m";
		long h = m / 60;
		return h + "h " + (m % 60) + "m";
	}

	/** "34%" from 0.34. */
	public static String percent(double fraction) {
		if (Double.isNaN(fraction)) return "?";
		return String.format(Locale.ROOT, "%.0f%%", fraction * 100);
	}
}
