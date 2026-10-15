package io.github.donttemptfate.fatesflipper.core;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Cache for hover prices. Tooltips are drawn every frame, so lookups must be cheap:
 * <ul>
 *   <li>one request per item at a time, answers kept 60 s (failures 15 s);</li>
 *   <li>at most a few new lookups a second (token bucket);</li>
 *   <li>at most 512 items remembered.</li>
 * </ul>
 */
public final class PriceCache {
	public enum State { LOADING, FOUND, NOT_FOUND, FAILED }

	public record Entry(State state, JsonObject data, String message, long atMs) {}

	static final long TTL_MS = 60_000;
	static final long FAIL_TTL_MS = 15_000;
	static final int MAX_ENTRIES = 512;

	private final Function<String, CompletableFuture<ViewerApi.Result>> fetch;
	private final LongSupplier clock;
	private final Timing.RateLimiter limiter;
	private final Map<String, Entry> map = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
			return size() > MAX_ENTRIES;
		}
	};

	public PriceCache(Function<String, CompletableFuture<ViewerApi.Result>> fetch, LongSupplier clock, double perSecond) {
		this.fetch = fetch;
		this.clock = clock;
		this.limiter = new Timing.RateLimiter(perSecond, Math.max(1, perSecond * 2), clock.getAsLong());
	}

	/**
	 * Returns what's known for {@code key} right now and starts a lookup if needed. Returns
	 * {@code null} when nothing is known and the rate limit says wait.
	 */
	public synchronized Entry get(String key) {
		long now = clock.getAsLong();
		Entry e = map.get(key);
		if (e != null) {
			long ttl = switch (e.state) {
				case LOADING -> Long.MAX_VALUE;
				case FAILED -> FAIL_TTL_MS;
				default -> TTL_MS;
			};
			if (now - e.atMs < ttl) return e;
		}
		if (!limiter.tryAcquire(now)) return e; // stale answer (or null) until a slot is free
		Entry loading = new Entry(State.LOADING, e == null ? null : e.data, null, now);
		map.put(key, loading);
		CompletableFuture<ViewerApi.Result> f;
		try {
			f = fetch.apply(key);
		} catch (RuntimeException ex) {
			map.put(key, new Entry(State.FAILED, null, "error", now));
			return map.get(key);
		}
		f.whenComplete((r, ex) -> complete(key, r, ex));
		return map.get(key);
	}

	private synchronized void complete(String key, ViewerApi.Result r, Throwable ex) {
		long now = clock.getAsLong();
		if (ex != null || r == null) {
			map.put(key, new Entry(State.FAILED, null, "error", now));
		} else if (r.ok()) {
			boolean found = Json.bool(r.json(), "found", false);
			map.put(key, new Entry(found ? State.FOUND : State.NOT_FOUND, r.json(), null, now));
		} else {
			map.put(key, new Entry(State.FAILED, null, r.message(), now));
		}
	}

	public synchronized void clear() {
		map.clear();
	}

	synchronized int size() {
		return map.size();
	}
}
