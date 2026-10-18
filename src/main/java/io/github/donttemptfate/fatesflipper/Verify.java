package io.github.donttemptfate.fatesflipper;

import com.google.gson.JsonObject;
import io.github.donttemptfate.fatesflipper.core.Json;
import io.github.donttemptfate.fatesflipper.core.ViewerApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static io.github.donttemptfate.fatesflipper.core.Tr.tr;

/**
 * {@code /ff verify}: proves to the Fates viewer which Minecraft account you play on, so "My flips" can
 * show your own trades. Opt-in, off until you type it, one run per command, nothing on a timer.
 *
 * <h2>Everything this class does, in order</h2>
 * <ol>
 *   <li>{@code /ff verify} only prints what will happen ({@link #explain}). Nothing is sent.</li>
 *   <li>{@code /ff verify CONFIRM} runs {@link #run} once (a second run waits for the first to end).</li>
 *   <li>{@link #run} asks <b>your viewer</b> (the address in settings, with your mod token, like every
 *       other request) for a one-use code: {@code GET /mod/whoami/challenge} → {@code {server_id}}.
 *       The code is checked to be short and plain ({@link #SERVER_ID}); anything else stops here.</li>
 *   <li>{@link #joinMojang} hands that code to <b>Minecraft's own</b> session service:
 *       {@code mc.services().sessionService().joinServer(profileId, accessToken, serverId)}. This is the exact
 *       call Minecraft makes every time you join any online-mode server. Minecraft sends it to Mojang's
 *       session server ({@code sessionserver.mojang.com}) and nowhere else. Your access token is read in
 *       that one line, passed straight into the call, and never kept, logged, shown or sent anywhere by
 *       this mod.</li>
 *   <li>{@link #run} then posts <b>only</b> your player name and the same code to your viewer:
 *       {@code POST /mod/whoami {"name": "...", "server_id": "..."}} ({@link #body}; a unit test checks
 *       that the body has exactly those two fields).</li>
 *   <li>The viewer asks Mojang "did <i>name</i> join with <i>server_id</i>?" ({@code hasJoined}). Mojang
 *       only says yes if step 4 happened from your game, so the viewer learns your account is yours,
 *       without ever seeing a password or token.</li>
 *   <li>The answer (linked / verified / already taken / limit) is shown in chat ({@link #report}).</li>
 * </ol>
 * There are no other network calls, files or settings in this class. Search the mod for
 * {@code getAccessToken}: this file is the only place it appears.
 */
public final class Verify {
	private Verify() {}

	/** What a one-use code from the viewer may look like (the viewer sends 40 hex characters). */
	static final Pattern SERVER_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");
	/** Minecraft player names. */
	static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

	private static final AtomicBoolean RUNNING = new AtomicBoolean();

	/** Step 1: {@code /ff verify} shows what CONFIRM will do. */
	public static void explain() {
		Chat.info(tr("fatesflipper.verify.what"));
		Chat.info(tr("fatesflipper.verify.sends"));
		Chat.warn(tr("fatesflipper.verify.how"));
	}

	/** Steps 2–7: {@code /ff verify CONFIRM}. */
	public static void run(FlipService service) {
		Minecraft mc = Minecraft.getInstance();
		User user = mc.getUser();
		String name = user.getName();
		if (!NAME.matcher(name == null ? "" : name).matches()) {
			Chat.error(tr("fatesflipper.verify.offline"));
			return;
		}
		if (!RUNNING.compareAndSet(false, true)) {
			Chat.warn(tr("fatesflipper.verify.busy"));
			return;
		}
		ViewerApi api = service.api();
		Chat.info(tr("fatesflipper.verify.start"));
		api.get("/mod/whoami/challenge")
				.thenCompose(r -> {
					String serverId = r.ok() ? Json.str(r.json(), "server_id") : null;
					if (serverId == null || !SERVER_ID.matcher(serverId).matches()) {
						throw new IllegalStateException(r.ok() ? tr("fatesflipper.verify.bad_code") : r.message());
					}
					return CompletableFuture.supplyAsync(() -> {
						joinMojang(mc, serverId);
						return serverId;
					});
				})
				.thenCompose(serverId -> api.post("/mod/whoami", body(name, serverId)))
				.whenComplete((r, err) -> {
					RUNNING.set(false);
					mc.execute(() -> report(r, err));
				});
	}

	/**
	 * Step 4. Minecraft's own "join server" call, exactly as when joining a server. The access token is
	 * read here and goes straight into Minecraft's session service (to Mojang); it never leaves this line.
	 */
	private static void joinMojang(Minecraft mc, String serverId) {
		User user = mc.getUser();
		UUID profileId = user.getProfileId();
		var sessions = mc.services().sessionService(); // 26.2 MinecraftSessionService, 26.3 SessionService
		try {
			sessions.joinServer(profileId, user.getAccessToken(), serverId);
		} catch (Exception e) {
			// the message only says why Mojang refused (e.g. invalid session); it never contains the token
			throw new IllegalStateException(tr("fatesflipper.verify.mojang", e.getClass().getSimpleName()));
		}
	}

	/** Step 5: the only thing posted to the viewer: your player name and the one-use code. */
	static JsonObject body(String name, String serverId) {
		JsonObject o = new JsonObject();
		o.addProperty("name", name);
		o.addProperty("server_id", serverId);
		return o;
	}

	/** Step 7. */
	private static void report(ViewerApi.Result r, Throwable err) {
		if (err != null) {
			Throwable c = err.getCause() != null ? err.getCause() : err;
			Chat.error(tr("fatesflipper.verify.failed", c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage()));
			return;
		}
		if (r == null || !r.ok()) {
			Chat.error(tr("fatesflipper.verify.failed", r == null ? "?" : r.message()));
			return;
		}
		String result = Json.str(r.json(), "result");
		String shown = Json.str(r.json(), "name");
		String who = shown != null && NAME.matcher(shown).matches() ? shown : "?";
		switch (result == null ? "" : result) {
			case "verified", "linked" -> Chat.ok(tr("fatesflipper.verify.ok", who));
			case "taken" -> Chat.error(tr("fatesflipper.verify.taken", who));
			case "limit" -> Chat.error(tr("fatesflipper.verify.limit"));
			default -> Chat.warn(tr("fatesflipper.verify.unknown"));
		}
	}
}
