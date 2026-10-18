package io.github.donttemptfate.fatesflipper.core;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Safe mode (roadmap P0, audit M10): a feature that keeps failing switches itself off for the rest of
 * the game session, instead of failing every frame. {@value #LIMIT} errors within
 * {@value #WINDOW_MS} ms trip it; the first error is logged with its stack trace and the player is told
 * once. {@code /ff safemode reset} turns everything back on. Nothing here ever turns a feature on that
 * the player switched off.
 */
public final class SafeMode {
	private SafeMode() {}

	public enum Feature {
		TOOLTIPS("tooltips"), PRICES("prices"), OVERLAY("overlay"), CURSOR("cursor"), CLOSE_KEY("closekey"),
		HOVER("hover"), HUD("hud"), BAZAAR("bazaar");

		public final String id;

		Feature(String id) {
			this.id = id;
		}
	}

	static final int LIMIT = 5;
	static final long WINDOW_MS = 60_000;

	private static final Map<Feature, ArrayDeque<Long>> ERRORS = new EnumMap<>(Feature.class);
	private static final Set<Feature> OFF = EnumSet.noneOf(Feature.class);
	private static final Set<Feature> LOGGED = EnumSet.noneOf(Feature.class);
	/** (feature, first error) → log it; (feature, null) → it was just switched off. */
	private static volatile BiConsumer<Feature, Throwable> listener = (f, e) -> {};

	public static void setListener(BiConsumer<Feature, Throwable> l) {
		listener = l == null ? (f, e) -> {} : l;
	}

	public static synchronized boolean off(Feature f) {
		return OFF.contains(f);
	}

	public static void error(Feature f, Throwable e) {
		error(f, e, System.currentTimeMillis());
	}

	/** Records one error; returns true if this one switched the feature off. */
	public static boolean error(Feature f, Throwable e, long nowMs) {
		boolean first;
		boolean tripped = false;
		synchronized (SafeMode.class) {
			if (OFF.contains(f)) return false;
			first = LOGGED.add(f);
			ArrayDeque<Long> q = ERRORS.computeIfAbsent(f, k -> new ArrayDeque<>());
			q.addLast(nowMs);
			while (!q.isEmpty() && nowMs - q.peekFirst() > WINDOW_MS) q.removeFirst();
			if (q.size() >= LIMIT) {
				OFF.add(f);
				q.clear();
				tripped = true;
			}
		}
		try {
			if (first) listener.accept(f, e);
			if (tripped) listener.accept(f, null);
		} catch (RuntimeException ignored) {
			// telling the player must never fail the caller
		}
		return tripped;
	}

	public static synchronized Set<Feature> switchedOff() {
		return EnumSet.copyOf(OFF.isEmpty() ? EnumSet.noneOf(Feature.class) : OFF);
	}

	public static synchronized void reset() {
		OFF.clear();
		ERRORS.clear();
		LOGGED.clear();
	}
}
