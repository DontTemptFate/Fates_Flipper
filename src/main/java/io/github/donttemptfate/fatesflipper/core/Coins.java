package io.github.donttemptfate.fatesflipper.core;

import java.util.Locale;

/** Short coin amounts: 950, 12.5k, 1.23M, 4.1B. */
public final class Coins {
	private Coins() {}

	public static String format(double coins) {
		if (Double.isNaN(coins) || Double.isInfinite(coins)) return "?";
		double a = Math.abs(coins);
		String sign = coins < 0 ? "-" : "";
		if (a >= 1e9) return sign + trim(a / 1e9) + "B";
		if (a >= 1e6) return sign + trim(a / 1e6) + "M";
		if (a >= 1e3) return sign + trim(a / 1e3) + "k";
		return sign + String.format(Locale.ROOT, "%.0f", a);
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
