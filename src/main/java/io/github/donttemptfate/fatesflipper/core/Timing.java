package io.github.donttemptfate.fatesflipper.core;

/** Small timing helpers: a command cooldown and the reconnect backoff. */
public final class Timing {
	private Timing() {}

	/** Allows one action per {@code periodMs}. Not thread-safe; use from the game thread. */
	public static final class Cooldown {
		private final long periodMs;
		private long lastMs = Long.MIN_VALUE / 2;

		public Cooldown(long periodMs) {
			this.periodMs = periodMs;
		}

		/** True (and starts the cooldown) when the action may run now. */
		public boolean tryAcquire(long nowMs) {
			if (nowMs - lastMs < periodMs) return false;
			lastMs = nowMs;
			return true;
		}

		public long remainingMs(long nowMs) {
			return Math.max(0, periodMs - (nowMs - lastMs));
		}
	}

	/**
	 * Reconnect delays: 1 s, 2 s, 5 s, 10 s, then every 30 s. {@link #reset()} after a connection
	 * that stayed up long enough.
	 */
	public static final class Backoff {
		private static final long[] STEPS_MS = {1_000, 2_000, 5_000, 10_000, 30_000};
		private int attempt;

		public long nextDelayMs() {
			long d = STEPS_MS[Math.min(attempt, STEPS_MS.length - 1)];
			attempt++;
			return d;
		}

		public void reset() {
			attempt = 0;
		}

		public int attempts() {
			return attempt;
		}
	}

	/**
	 * Simple token bucket: at most {@code perSecond} actions a second on average, bursts up to
	 * {@code burst}. Thread-safe.
	 */
	public static final class RateLimiter {
		private final double perMs;
		private final double burst;
		private double tokens;
		private long lastMs;

		public RateLimiter(double perSecond, double burst, long nowMs) {
			this.perMs = perSecond / 1000.0;
			this.burst = burst;
			this.tokens = burst;
			this.lastMs = nowMs;
		}

		public synchronized boolean tryAcquire(long nowMs) {
			if (nowMs > lastMs) {
				tokens = Math.min(burst, tokens + (nowMs - lastMs) * perMs);
				lastMs = nowMs;
			}
			if (tokens >= 1.0) {
				tokens -= 1.0;
				return true;
			}
			return false;
		}
	}
}
