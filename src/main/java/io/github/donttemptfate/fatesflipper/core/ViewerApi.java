package io.github.donttemptfate.fatesflipper.core;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Requests to the viewer's mod service ({@code /mod/*}, viewer contract §4.3).
 *
 * <ul>
 *   <li>Every request carries {@code Authorization: Bearer <token>} and goes only to the configured
 *       address. Redirects are never followed, so the token can't be forwarded elsewhere.</li>
 *   <li>A 429 pauses all requests for the {@code Retry-After} time (30 s if missing).</li>
 *   <li>Everything is asynchronous; callers get a {@link Result}, never an exception.</li>
 * </ul>
 */
public final class ViewerApi {
	public enum Status { OK, NOT_CONFIGURED, UNAUTHORIZED, FORBIDDEN, RATE_LIMITED, NOT_FOUND, ERROR }

	public record Result(Status status, int httpCode, JsonObject json, String message, long retryAfterMs) {
		public boolean ok() {
			return status == Status.OK;
		}
	}

	private final Supplier<ModConfig> config;
	private final String userAgent;
	private final HttpClient http;
	private volatile long pausedUntilMs;

	public ViewerApi(Supplier<ModConfig> config, String modVersion) {
		this.config = config;
		this.userAgent = "FatesFlipper/" + modVersion;
		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	public HttpClient http() {
		return http;
	}

	public String userAgent() {
		return userAgent;
	}

	// ---- endpoints -------------------------------------------------------------------------

	public CompletableFuture<Result> ping() {
		return get("/mod/ping");
	}

	public CompletableFuture<Result> profiles() {
		return get("/mod/profiles");
	}

	/** Full profile list incl. each profile's chat settings ({@code /api/profiles}). */
	public CompletableFuture<Result> profileSettings() {
		return get("/api/profiles");
	}

	/** {@code profile}: a name, an id, or "off". */
	public CompletableFuture<Result> setProfile(String profile) {
		JsonObject body = new JsonObject();
		if (profile == null) body.add("profile", com.google.gson.JsonNull.INSTANCE);
		else if (profile.matches("^\\d{1,9}$")) body.addProperty("profile", Long.parseLong(profile));
		else body.addProperty("profile", profile);
		return post("/mod/profile", body);
	}

	/** {@code mode}: "best" or "safest". {@code skip}: auction ids already opened. */
	public CompletableFuture<Result> next(String mode, Iterable<String> skip) {
		StringBuilder q = new StringBuilder("/mod/next?mode=").append("safest".equals(mode) ? "safest" : "best");
		StringBuilder s = new StringBuilder();
		for (String id : skip) {
			if (!AuctionIds.isValid(id)) continue;
			if (s.length() > 0) s.append(',');
			s.append(id);
		}
		if (s.length() > 0) q.append("&skip=").append(s);
		return get(q.toString());
	}

	public CompletableFuture<Result> auction(String auctionId) {
		if (!AuctionIds.isValid(auctionId)) return done(Status.ERROR, 0, null, "bad auction id", 0);
		return get("/mod/auction/" + auctionId);
	}

	/** Hover price by the item's own uuid (dashes allowed). */
	public CompletableFuture<Result> priceByItem(String itemUuid) {
		String id = AuctionIds.normalize(itemUuid);
		if (id == null) return done(Status.ERROR, 0, null, "bad item uuid", 0);
		return get("/mod/price?item_uuid=" + id);
	}

	/** Price (incl. recent sales) for an auction. */
	public CompletableFuture<Result> priceByAuction(String auctionId) {
		if (!AuctionIds.isValid(auctionId)) return done(Status.ERROR, 0, null, "bad auction id", 0);
		return get("/mod/price?auction=" + auctionId);
	}

	// ---- plumbing --------------------------------------------------------------------------

	/** Base URL and token if usable, else a reason. */
	public record Target(String baseUrl, String token, String problem) {}

	public Target target() {
		ModConfig c = config.get();
		if (c == null) return new Target(null, null, "Not set up yet.");
		ServerAddress.Check check = ServerAddress.check(c.serverUrl);
		if (!check.ok()) return new Target(null, null, check.problem());
		if (c.token == null || c.token.isBlank()) return new Target(null, null, "No mod token set. Make one in the web page (account menu → Mod tokens), then /ff config.");
		return new Target(check.baseUrl(), c.token.trim(), null);
	}

	public long pausedForMs(long nowMs) {
		return Math.max(0, pausedUntilMs - nowMs);
	}

	public void pauseFor(long ms) {
		long until = System.currentTimeMillis() + ms;
		if (until > pausedUntilMs) pausedUntilMs = until;
	}

	HttpRequest.Builder request(Target t, String path) {
		return HttpRequest.newBuilder(URI.create(t.baseUrl() + path))
				.timeout(Duration.ofSeconds(15))
				.header("Authorization", "Bearer " + t.token())
				.header("User-Agent", userAgent)
				.header("Accept", "application/json");
	}

	public CompletableFuture<Result> get(String path) {
		return send(path, null);
	}

	public CompletableFuture<Result> post(String path, JsonObject body) {
		return send(path, body);
	}

	private CompletableFuture<Result> send(String path, JsonObject body) {
		Target t = target();
		if (t.problem() != null) return done(Status.NOT_CONFIGURED, 0, null, t.problem(), 0);
		long paused = pausedForMs(System.currentTimeMillis());
		if (paused > 0) return done(Status.RATE_LIMITED, 429, null, "Waiting " + (paused / 1000 + 1) + " s (server asked to slow down).", paused);
		HttpRequest.Builder b;
		try {
			b = request(t, path);
		} catch (IllegalArgumentException e) {
			return done(Status.ERROR, 0, null, "Bad server address.", 0);
		}
		if (body == null) b.GET();
		else b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
		return http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenApply(this::toResult)
				.exceptionally(ex -> new Result(Status.ERROR, 0, null, "Can't reach the server (" + rootMessage(ex) + ").", 0));
	}

	private Result toResult(HttpResponse<String> r) {
		int code = r.statusCode();
		JsonObject json = Json.parseObject(r.body());
		String err = Json.str(json, "error");
		return switch (code) {
			case 200 -> json == null
					? new Result(Status.ERROR, code, null, "The server sent something unexpected.", 0)
					: new Result(Status.OK, code, json, null, 0);
			case 401 -> new Result(Status.UNAUTHORIZED, code, json, "Mod token missing or revoked: make a new one in the web page (account menu → Mod tokens), then /ff config.", 0);
			case 403 -> new Result(Status.FORBIDDEN, code, json, forbiddenMessage(json, err), 0);
			case 404 -> new Result(Status.NOT_FOUND, code, json, err != null ? err : "Not found.", 0);
			case 429 -> {
				long ms = retryAfterMs(r);
				pauseFor(ms);
				yield new Result(Status.RATE_LIMITED, code, json, "The server asked to slow down; waiting " + (ms / 1000) + " s.", ms);
			}
			default -> new Result(Status.ERROR, code, json, "Server error " + code + (err != null ? ": " + LegacyText.sanitize(err) : "") + ".", 0);
		};
	}

	static String forbiddenMessage(JsonObject json, String err) {
		if (Json.bool(json, "must_change_pw", false)) return "Your viewer account must change its password first (open the web page).";
		if (Json.bool(json, "private_only", false) || (err != null && err.toLowerCase().contains("admin")))
			return "Admin tokens only work on the private (Tailscale) address. Use your private address, or a normal account's token here.";
		return "The server refused this token" + (err != null ? " (" + LegacyText.sanitize(err) + ")" : "") + ". Admin tokens only work on the private address.";
	}

	static long retryAfterMs(HttpResponse<?> r) {
		Optional<String> h = r.headers().firstValue("Retry-After");
		return parseRetryAfter(h.orElse(null));
	}

	static long parseRetryAfter(String header) {
		if (header != null) {
			try {
				long s = Long.parseLong(header.trim());
				if (s >= 0) return Math.min(s, 600) * 1000L;
			} catch (NumberFormatException ignored) {
				// HTTP-date form isn't used by the viewer; fall through to the default
			}
		}
		return 30_000L;
	}

	static String rootMessage(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null && c.getCause() != c) c = c.getCause();
		String m = c.getClass().getSimpleName();
		return m.isEmpty() ? "network error" : m;
	}

	private static CompletableFuture<Result> done(Status s, int code, JsonObject json, String msg, long retry) {
		return CompletableFuture.completedFuture(new Result(s, code, json, msg, retry));
	}

	public static String urlEncode(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
