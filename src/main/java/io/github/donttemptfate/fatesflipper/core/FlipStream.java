package io.github.donttemptfate.fatesflipper.core;

import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * The one {@code /mod/flips} push stream per running game (viewer contract §4.3).
 *
 * <ul>
 *   <li>Reconnects with backoff 1 s, 2 s, 5 s, 10 s, then every 30 s; the backoff resets after a
 *       connection that lasted over a minute.</li>
 *   <li>No data or keep-alive for 45 s counts as dropped.</li>
 *   <li>401 / 403 stop it until the settings change or the player reconnects; 429 waits for
 *       {@code Retry-After}.</li>
 * </ul>
 * Callbacks run on the stream's own thread; the game side moves them to the game thread.
 */
public final class FlipStream {
	public enum State { OFF, WAITING, CONNECTING, CONNECTED, RETRYING, STOPPED }

	public interface Listener {
		void onState(State state, String detail);

		void onFlip(JsonObject flip);

		void onProfile(JsonObject profile);
	}

	static final long IDLE_LIMIT_MS = 45_000;
	static final long STABLE_AFTER_MS = 60_000;

	private final ViewerApi api;
	private final Listener listener;
	private final BooleanSupplier allowed;
	private final Object lock = new Object();
	private final ScheduledExecutorService watchdog;

	private Thread thread;
	private volatile boolean running;
	private volatile int generation;
	private volatile InputStream current;
	private volatile long lastActivityMs;
	private volatile State state = State.OFF;
	private volatile String detail = "";
	private volatile long connectedSinceMs;

	/**
	 * @param allowed whether the stream may run right now (e.g. "on Hypixel and alerts on").
	 */
	public FlipStream(ViewerApi api, Listener listener, BooleanSupplier allowed) {
		this.api = api;
		this.listener = listener;
		this.allowed = allowed;
		this.watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "FatesFlipper-stream-watchdog");
			t.setDaemon(true);
			return t;
		});
		this.watchdog.scheduleAtFixedRate(this::checkIdle, 5, 5, TimeUnit.SECONDS);
	}

	public State state() {
		return state;
	}

	public String detail() {
		return detail;
	}

	public long connectedSinceMs() {
		return state == State.CONNECTED ? connectedSinceMs : 0;
	}

	/** Starts the background thread (once). */
	public void start() {
		synchronized (lock) {
			if (thread != null) return;
			running = true;
			thread = new Thread(this::loop, "FatesFlipper-stream");
			thread.setDaemon(true);
			thread.start();
		}
	}

	/** Drops the current connection and tries again now (after settings changed, or /ff reconnect). */
	public void restart() {
		synchronized (lock) {
			generation++;
			lock.notifyAll();
		}
		closeCurrent();
	}

	/** Wakes the loop so it re-checks whether it's allowed to run (e.g. joined or left Hypixel). */
	public void poke() {
		synchronized (lock) {
			lock.notifyAll();
		}
		if (!allowed.getAsBoolean()) closeCurrent();
	}

	public void shutdown() {
		running = false;
		restart();
		watchdog.shutdownNow();
	}

	private void setState(State s, String d) {
		state = s;
		detail = d == null ? "" : d;
		try {
			listener.onState(s, detail);
		} catch (RuntimeException ignored) {
			// a broken listener must not kill the stream
		}
	}

	private void loop() {
		Timing.Backoff backoff = new Timing.Backoff();
		while (running) {
			int gen = generation;
			if (!allowed.getAsBoolean()) {
				if (state != State.OFF) setState(State.OFF, "");
				waitFor(gen, 0);
				continue;
			}
			ViewerApi.Target t = api.target();
			if (t.problem() != null) {
				setState(State.WAITING, t.problem());
				waitFor(gen, 0);
				continue;
			}
			long paused = api.pausedForMs(System.currentTimeMillis());
			if (paused > 0) {
				setState(State.RETRYING, "Server asked to slow down; retrying in " + (paused / 1000 + 1) + " s.");
				waitFor(gen, paused);
				continue;
			}
			Outcome o = connectOnce(t, gen);
			if (!running) break;
			switch (o.kind) {
				case STABLE_END -> {
					backoff.reset();
					long d = backoff.nextDelayMs();
					setState(State.RETRYING, "Connection dropped; reconnecting.");
					waitFor(gen, d);
				}
				case FAILED -> {
					long d = backoff.nextDelayMs();
					setState(State.RETRYING, o.message + " Retrying in " + (d / 1000) + " s.");
					waitFor(gen, d);
				}
				case RATE_LIMITED -> {
					setState(State.RETRYING, o.message);
					waitFor(gen, Math.max(1_000, api.pausedForMs(System.currentTimeMillis())));
				}
				case STOPPED -> {
					setState(State.STOPPED, o.message);
					waitForRestart(gen); // until settings change or /ff reconnect
					backoff.reset();
				}
				case RESTART -> backoff.reset();
			}
		}
		setState(State.OFF, "");
	}

	private enum Kind { STABLE_END, FAILED, RATE_LIMITED, STOPPED, RESTART }

	private record Outcome(Kind kind, String message) {}

	private Outcome connectOnce(ViewerApi.Target t, int gen) {
		setState(State.CONNECTING, "");
		HttpRequest req;
		try {
			req = api.request(t, "/mod/flips")
					.header("Accept", "text/event-stream")
					.timeout(Duration.ofSeconds(20))
					.GET()
					.build();
		} catch (IllegalArgumentException e) {
			return new Outcome(Kind.STOPPED, "Bad server address.");
		}
		HttpResponse<InputStream> resp;
		try {
			resp = api.http().send(req, HttpResponse.BodyHandlers.ofInputStream());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Outcome(Kind.RESTART, "");
		} catch (Exception e) {
			return new Outcome(Kind.FAILED, "Can't reach the server (" + ViewerApi.rootMessage(e) + ").");
		}
		int code = resp.statusCode();
		if (code != 200) {
			closeQuietly(resp.body());
			return switch (code) {
				case 401 -> new Outcome(Kind.STOPPED, "Mod token missing or revoked: make a new one in the web page, then /ff config.");
				case 403 -> new Outcome(Kind.STOPPED, ViewerApi.forbiddenMessage(null, null));
				case 429 -> {
					long ms = ViewerApi.retryAfterMs(resp);
					api.pauseFor(ms);
					yield new Outcome(Kind.RATE_LIMITED, "Too many connections for this account (3 games at most); retrying in " + (ms / 1000) + " s.");
				}
				default -> new Outcome(Kind.FAILED, "Server answered " + code + ".");
			};
		}
		long start = System.currentTimeMillis();
		connectedSinceMs = start;
		lastActivityMs = start;
		current = resp.body();
		setState(State.CONNECTED, "");
		SseParser parser = new SseParser();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
			String line;
			while (running && gen == generation && allowed.getAsBoolean() && (line = readLine(reader)) != null) {
				lastActivityMs = System.currentTimeMillis();
				SseParser.Event ev = parser.feed(line);
				if (ev != null) dispatch(ev);
			}
		} catch (Exception e) {
			// dropped, idle timeout, or restart: handled below
		} finally {
			current = null;
		}
		if (gen != generation || !allowed.getAsBoolean()) return new Outcome(Kind.RESTART, "");
		long lasted = System.currentTimeMillis() - start;
		return lasted >= STABLE_AFTER_MS ? new Outcome(Kind.STABLE_END, "") : new Outcome(Kind.FAILED, "Connection dropped.");
	}

	/** Longest line accepted from the stream; a longer one ends the connection. */
	static final int MAX_LINE_CHARS = 1024 * 1024;

	/** Like readLine, but refuses endless lines (no newline) instead of using up memory. */
	static String readLine(BufferedReader r) throws java.io.IOException {
		StringBuilder sb = new StringBuilder();
		int c;
		while ((c = r.read()) != -1) {
			if (c == '\n') return sb.toString();
			if (c == '\r') {
				r.mark(1);
				int n = r.read();
				if (n != '\n' && n != -1) r.reset();
				return sb.toString();
			}
			if (sb.length() >= MAX_LINE_CHARS) throw new java.io.IOException("line too long");
			sb.append((char) c);
		}
		return sb.length() > 0 ? sb.toString() : null;
	}

	private void dispatch(SseParser.Event ev) {
		JsonObject json = Json.parseObject(ev.data());
		if (json == null) return;
		try {
			switch (ev.event()) {
				case "flip" -> listener.onFlip(json);
				case "profile" -> listener.onProfile(json);
				default -> {
					// unknown events are ignored
				}
			}
		} catch (RuntimeException ignored) {
			// never let one bad event end the stream
		}
	}

	private void checkIdle() {
		InputStream in = current;
		if (in != null && System.currentTimeMillis() - lastActivityMs > IDLE_LIMIT_MS) closeCurrent();
	}

	private void closeCurrent() {
		InputStream in = current;
		if (in != null) closeQuietly(in);
	}

	private static void closeQuietly(InputStream in) {
		try {
			in.close();
		} catch (Exception ignored) {
			// nothing to do
		}
	}

	/** Waits until {@link #restart()} is called (or shutdown). */
	private void waitForRestart(int gen) {
		synchronized (lock) {
			while (running && gen == generation) {
				try {
					lock.wait(5_000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	/** Waits until the generation changes, a poke, or the timeout (0 = until the next wake, max 5 s). */
	private void waitFor(int gen, long ms) {
		long deadline = ms > 0 ? System.currentTimeMillis() + ms : Long.MAX_VALUE;
		synchronized (lock) {
			while (running && gen == generation) {
				long left = deadline - System.currentTimeMillis();
				if (left <= 0) return;
				try {
					lock.wait(Math.min(left, 5_000));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
				if (ms == 0) return; // re-check conditions after every wake or 5 s
			}
		}
	}
}
