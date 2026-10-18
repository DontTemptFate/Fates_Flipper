package io.github.donttemptfate.fatesflipper;

import io.github.donttemptfate.fatescommon.Settings;
import io.github.donttemptfate.fatescommon.ui.Theme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.donttemptfate.fatesflipper.core.AuctionIds;
import io.github.donttemptfate.fatesflipper.core.FlipEvent;
import io.github.donttemptfate.fatesflipper.core.FlipStream;
import io.github.donttemptfate.fatesflipper.core.Json;
import io.github.donttemptfate.fatesflipper.core.ModConfig;
import io.github.donttemptfate.fatesflipper.core.PriceCache;
import io.github.donttemptfate.fatesflipper.core.Timing;
import io.github.donttemptfate.fatesflipper.core.ViewerApi;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Everything the mod knows and does with the viewer: settings, the flip stream, opened auctions
 * and hover prices. Game-facing pieces (chat, keys, overlay) call into this.
 */
public final class FlipService implements FlipStream.Listener {
	/** One press / one click = at most one command, and never faster than this. */
	public static final long COMMAND_COOLDOWN_MS = 500;
	/** The overlay and cursor assist only act on auctions opened from a flip this recently. */
	public static final long OPENED_WINDOW_MS = 30_000;

	/** An auction the player opened through the mod. */
	public record Opened(String auctionId, long atMs, FlipEvent flip) {}

	private final Minecraft mc;
	private volatile ModConfig config;
	private final ViewerApi api;
	private final FlipStream stream;
	private final PriceCache itemPrices;
	private final Timing.Cooldown commandCooldown = new Timing.Cooldown(COMMAND_COOLDOWN_MS);
	/** Open-flip key presses (open and close alike): at most one action per half second. */
	private final Timing.Cooldown keyCooldown = new Timing.Cooldown(COMMAND_COOLDOWN_MS);

	private final Deque<FlipEvent> recent = new ArrayDeque<>();
	private final LinkedHashSet<String> openedIds = new LinkedHashSet<>();
	private volatile Opened lastOpened;
	private volatile JsonObject lastOpenedPrice;
	private volatile boolean lookupInFlight;
	private volatile boolean onHypixel;

	private volatile String profileName;
	private volatile JsonObject chatSettings;
	private volatile FlipStream.State lastState = FlipStream.State.OFF;
	private volatile long stateChangedAtMs;
	private volatile boolean announcedConnected;

	// ---- version handshake (audit F-S5; viewer /mod/ping mod_min_version / mod_latest_version) ----
	private final String modVersion;
	/** Set when the server needs a newer mod: flips stop, hover prices keep working. */
	private volatile String updateRequired;
	private volatile boolean toldAboutLatest;
	private volatile long versionCheckedAtMs;

	public String modVersion() {
		return modVersion;
	}

	/** Why flips are stopped for an outdated mod, or null. */
	public String updateRequired() {
		return updateRequired;
	}

	/** Once per connection (at most every 10 min): asks the viewer which mod versions it wants. */
	void checkVersion() {
		long now = System.currentTimeMillis();
		if (now - versionCheckedAtMs < 10 * 60_000) return;
		versionCheckedAtMs = now;
		api.ping().thenAccept(r -> {
			if (!r.ok()) return;
			applyVersions(Json.str(r.json(), "mod_min_version"), Json.str(r.json(), "mod_latest_version"),
					io.github.donttemptfate.fatescommon.text.Versions.safeUrl(Json.str(r.json(), "mod_download_url")));
		});
	}

	public void applyVersions(String min, String latest, String url) {
		if (io.github.donttemptfate.fatescommon.text.Versions.valid(min)
				&& io.github.donttemptfate.fatescommon.text.Versions.compare(modVersion, min) < 0) {
			blockOutdated(min, url);
			return;
		}
		if (!toldAboutLatest && io.github.donttemptfate.fatescommon.text.Versions.valid(latest)
				&& io.github.donttemptfate.fatescommon.text.Versions.compare(modVersion, latest) < 0) {
			toldAboutLatest = true;
			mc.execute(() -> Chat.info(url != null ? io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.update_available_url", latest, modVersion, url)
					: io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.update_available", latest, modVersion)));
		}
	}

	private void blockOutdated(String min, String url) {
		String msg = io.github.donttemptfate.fatesflipper.core.Tr.tr(min != null ? "fatesflipper.msg.update_required" : "fatesflipper.msg.update_required_any", min != null ? min : "", modVersion)
				+ (url != null ? " " + io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.get_it", url) : "") + " " + io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.flips_off");
		boolean first = updateRequired == null;
		updateRequired = msg;
		stream.poke();
		if (first) mc.execute(() -> Chat.error(msg));
	}

	@Override
	public void onOutdated(String min, String url) {
		blockOutdated(min, url);
	}

	public FlipService(Minecraft mc, ModConfig config, String version) {
		this.mc = mc;
		this.config = config;
		// the font moved to the settings shared with Fates OFA: carry a 0.3.x choice over once
		if (config.legacyChakra != null && !Settings.saved()) Settings.setChakra(config.legacyChakra);
		mirrorShared(config);
		this.modVersion = io.github.donttemptfate.fatescommon.text.Versions.plain(version);
		this.api = new ViewerApi(() -> this.config, modVersion + " (Minecraft " + io.github.donttemptfate.fatescommon.compat.Compat.MINECRAFT + ")");
		this.stream = new FlipStream(api, this, this::streamAllowed);
		this.itemPrices = new PriceCache(api::priceByItem, System::currentTimeMillis, 3);
	}

	public void start() {
		stream.start();
	}

	public void shutdown() {
		stream.shutdown();
	}

	// ---- settings ------------------------------------------------------------------------------

	public ModConfig config() {
		mirrorShared(config); // Fates OFA may have changed the shared font or theme
		return config;
	}

	/** Copies the shared font and theme (Fates Common's settings) into the mod's settings object. */
	private static void mirrorShared(ModConfig c) {
		c.chakraFont = Settings.chakra();
		c.theme = Settings.theme().id();
	}

	/** Saves new settings and reconnects with them. */
	public void applyConfig(ModConfig next) {
		ModConfig prev = this.config;
		this.config = next;
		if (next.chakraFont != Settings.chakra()) Settings.setChakra(next.chakraFont);
		if (!Settings.theme().id().equals(next.theme)) Settings.setTheme(Theme.byId(next.theme));
		mirrorShared(next);
		try {
			next.save();
		} catch (Exception e) {
			FatesFlipperClient.LOG.warn("Couldn't save settings: {}", e.toString());
			Chat.error(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.save_failed", e.getClass().getSimpleName()));
		}
		boolean connectionChanged = !prev.serverUrl.equals(next.serverUrl) || !prev.token.equals(next.token);
		if (connectionChanged) {
			itemPrices.clear();
			announcedConnected = false;
			profileName = null;
			chatSettings = null;
			stream.restart();
		} else if (prev.alerts != next.alerts || prev.onlyOnHypixel != next.onlyOnHypixel
				|| prev.onlyInSkyBlock != next.onlyInSkyBlock || prev.alertsInDungeons != next.alertsInDungeons) {
			stream.poke(); // start or stop the stream; display toggles don't touch the connection
		}
	}

	public ViewerApi api() {
		return api;
	}

	public PriceCache itemPrices() {
		return itemPrices;
	}

	public boolean onHypixel() {
		return onHypixel;
	}

	/** True when the mod should talk to the viewer here (Hypixel, or anywhere if allowed). */
	public boolean active() {
		ModConfig c = config;
		return c.isReady() && (onHypixel || !c.onlyOnHypixel);
	}

	private boolean streamAllowed() {
		return config.alerts && active() && updateRequired == null && (!config.onlyInSkyBlock || inSkyBlockForAlerts());
	}

	/** SkyBlock (and, unless allowed, not in dungeons / Kuudra), read from the sidebar each tick (audit F-R6). */
	private volatile boolean inSkyBlock = true;

	public void setLocation(boolean skyBlock, boolean instance) {
		boolean was = inSkyBlockForAlerts();
		inSkyBlock = skyBlock;
		inInstance = instance;
		if (was != inSkyBlockForAlerts()) stream.poke();
	}

	private volatile boolean inInstance;

	/** Only judged on Hypixel (elsewhere, e.g. a test server, there's no SkyBlock sidebar to read). */
	boolean inSkyBlockForAlerts() {
		if (!onHypixel) return true;
		return inSkyBlock && (config.alertsInDungeons || !inInstance);
	}

	public void setOnHypixel(boolean value) {
		boolean changed = onHypixel != value;
		onHypixel = value;
		if (!value) {
			lastOpened = null;
			lastOpenedPrice = null;
		}
		if (changed) {
			announcedConnected = false;
			stream.restart();
		} else {
			stream.poke();
		}
	}

	public void reconnect() {
		announcedConnected = false;
		stream.restart();
	}

	// ---- state for the HUD and /ff status --------------------------------------------------

	public FlipStream.State streamState() {
		return stream.state();
	}

	public String streamDetail() {
		return stream.detail();
	}

	public long stateChangedAtMs() {
		return stateChangedAtMs;
	}

	public String profileName() {
		return profileName;
	}

	public Opened lastOpened() {
		Opened o = lastOpened;
		if (o == null || System.currentTimeMillis() - o.atMs() > OPENED_WINDOW_MS) return null;
		return o;
	}

	public JsonObject lastOpenedPrice() {
		return lastOpenedPrice;
	}

	public synchronized List<FlipEvent> recentFlips() {
		return new ArrayList<>(recent);
	}

	public synchronized FlipEvent recentFlip(String auctionId) {
		for (FlipEvent f : recent) if (f.auctionId().equals(auctionId)) return f;
		return null;
	}

	// ---- stream callbacks (stream thread) --------------------------------------------------

	@Override
	public void onState(FlipStream.State state, String detail) {
		FlipStream.State prev = lastState;
		lastState = state;
		if (prev != state) stateChangedAtMs = System.currentTimeMillis();
		mc.execute(() -> {
			if (state == FlipStream.State.CONNECTED && !announcedConnected) {
				announcedConnected = true;
				Chat.info(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.connected"));
				refreshChatSettings();
				checkVersion();
			} else if (state == FlipStream.State.STOPPED && prev != FlipStream.State.STOPPED) {
				Chat.error(detail);
			}
		});
	}

	static final int SEEN_FLIPS = 200;
	private final java.util.LinkedHashSet<String> seenFlips = new java.util.LinkedHashSet<>();

	@Override
	public void onFlip(JsonObject json) {
		FlipEvent f = FlipEvent.parse(json);
		if (f == null) return;
		if (!f.commandMatches()) {
			FatesFlipperClient.LOG.warn("Ignored a flip whose command wasn't /viewauction <id>");
			return;
		}
		synchronized (this) {
			// a replayed or re-sent flip never dings or prints twice (audit F-R2)
			if (!seenFlips.add(f.auctionId())) return;
			Iterator<String> it = seenFlips.iterator();
			while (seenFlips.size() > SEEN_FLIPS && it.hasNext()) {
				it.next();
				it.remove();
			}
			recent.addFirst(f);
			while (recent.size() > 30) recent.removeLast();
		}
		mc.execute(() -> Alerts.show(this, f));
	}

	@Override
	public void onProfile(JsonObject json) {
		JsonObject p = Json.obj(json, "profile");
		String name = Json.str(p, "name");
		boolean changed = profileName != null && !profileName.equals(name);
		profileName = name;
		if (changed) {
			mc.execute(() -> Chat.info(name == null ? io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.profile_off") : io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.profile_active", name)));
			refreshChatSettings();
		}
	}

	/**
	 * Reads the active profile's chat settings (sound etc.): from {@code /mod/profiles} when the viewer
	 * sends them there (handoff-mod-to-viewer-mod-service-2026-10-18), else from {@code /api/profiles}.
	 */
	public void refreshChatSettings() {
		api.profiles().thenAccept(r -> {
			JsonObject fromMod = r.ok() ? activeChat(r.json()) : null;
			if (fromMod != null) {
				chatSettings = fromMod;
				return;
			}
			api.profileSettings().thenAccept(r2 -> {
				if (!r2.ok()) return;
				JsonObject json = r2.json();
				JsonElement active = json.get("active");
				JsonArray list = Json.arr(json, "profiles");
				if (active == null || active.isJsonNull() || list == null) {
					chatSettings = null;
					return;
				}
				for (JsonElement e : list) {
					if (!e.isJsonObject()) continue;
					JsonObject p = e.getAsJsonObject();
					JsonElement id = p.get("id");
					if (id != null && id.equals(active)) {
						chatSettings = Json.obj(Json.obj(p, "settings"), "chat");
						return;
					}
				}
			});
		});
	}

	/** {@code active_chat}, or {@code chat} on the active profile's entry, from {@code /mod/profiles}; null if absent. */
	static JsonObject activeChat(JsonObject json) {
		JsonObject top = Json.obj(json, "active_chat");
		if (top != null) return top;
		JsonElement active = json == null ? null : json.get("active");
		JsonArray list = Json.arr(json, "profiles");
		if (active == null || active.isJsonNull() || list == null) return null;
		for (JsonElement e : list) {
			if (!e.isJsonObject()) continue;
			JsonObject p = e.getAsJsonObject();
			if (active.equals(p.get("id"))) return Json.obj(p, "chat");
		}
		return null;
	}

	/** Sound on alerts: the player's setting, unless the active profile turned it off. */
	public boolean alertSound() {
		if (!config.sound) return false;
		JsonObject cs = chatSettings;
		return cs == null || !Json.hasBool(cs, "sound") || Json.bool(cs, "sound", true);
	}

	// ---- opening auctions (game thread) ----------------------------------------------------

	/**
	 * Sends exactly one {@code /viewauction <id>} if the id is valid and the cooldown allows it.
	 * Called from a key press or a click on a flip line; never repeats on its own.
	 */
	public boolean openAuction(String rawId, FlipEvent flip) {
		String id = AuctionIds.normalize(rawId);
		if (id == null) {
			Chat.error(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.bad_id"));
			return false;
		}
		if (mc.player == null) return false;
		if (!onHypixel && config.onlyOnHypixel) {
			Chat.warn(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.not_hypixel_open"));
			return false;
		}
		if (!commandCooldown.tryAcquire(System.currentTimeMillis())) {
			Chat.warn(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.slow"));
			return false;
		}
		mc.player.connection.sendCommand(AuctionIds.viewAuctionCommand(id));
		FlipEvent known = flip != null ? flip : recentFlip(id);
		lastOpened = new Opened(id, System.currentTimeMillis(), known);
		lastOpenedPrice = null;
		synchronized (this) {
			openedIds.add(id);
			Iterator<String> it = openedIds.iterator();
			while (openedIds.size() > 60 && it.hasNext()) {
				it.next();
				it.remove();
			}
		}
		// recent sales of this exact item for the auction window's side panel
		api.priceByAuction(id).thenAccept(r -> {
			Opened o = lastOpened;
			if (r.ok() && o != null && o.auctionId().equals(id)) lastOpenedPrice = r.json();
		});
		return true;
	}

	/**
	 * {@code /ff open <id>} (clicking a flip line in chat): checks the auction is still up first, like
	 * the open keys, so an old chat line never opens a sold auction (audit F-R4). One lookup, then at
	 * most one command.
	 */
	public void openFromChat(String rawId) {
		String id = AuctionIds.normalize(rawId);
		if (id == null) {
			Chat.error(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.bad_id"));
			return;
		}
		if (lookupInFlight) return;
		lookupInFlight = true;
		api.auction(id).thenAccept(state -> {
			String s = state.ok() ? Json.str(state.json(), "state") : "unknown";
			if ("sold".equals(s) || "gone".equals(s)) {
				synchronized (this) {
					openedIds.add(id);
				}
				FlipEvent f = recentFlip(id);
				mc.execute(() -> Chat.warn(io.github.donttemptfate.fatesflipper.core.Tr.tr("sold".equals(s) ? "fatesflipper.msg.already_sold" : "fatesflipper.msg.already_ended", f == null ? io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.that_auction") : f.name())));
				return;
			}
			mc.execute(() -> openAuction(id, null));
		}).whenComplete((v, ex) -> lookupInFlight = false);
	}

	/** A press of an open-flip key may act now (shared by open and close, so a quick double tap can't close and reopen). */
	public boolean keyActionAllowed() {
		return keyCooldown.tryAcquire(System.currentTimeMillis());
	}

	/**
	 * The open-flip key pressed again inside the flip's own auction / confirm window: closes it,
	 * exactly like Esc (the flip stays skipped). Never sends anything else.
	 */
	public void closeFlipWindow(net.minecraft.client.gui.screens.Screen screen) {
		if (!keyActionAllowed()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != screen) return;
		screen.onClose();
	}

	/**
	 * Why the key found nothing, from {@code /mod/next} (viewer v0.28.1: {@code matching}, {@code skipped},
	 * {@code held}, {@code next_in_s}; older viewers only send {@code waiting}).
	 */
	static String noFlipMessage(String mode, com.google.gson.JsonObject json) {
		long matching = Json.lng(json, "matching", -1);
		if (matching >= 0) {
			long skipped = Json.lng(json, "skipped", 0);
			long held = Json.lng(json, "held", 0);
			long nextIn = Json.lng(json, "next_in_s", -1);
			if (held > 0) {
				return nextIn >= 0 ? io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.next.held_in", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.mode." + mode), held, nextIn) : io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.next.held_soon", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.mode." + mode), held);
			}
			if (matching > 0 && skipped >= matching) {
				return io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.next.all_opened", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.mode." + mode), matching);
			}
			return io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.next.none", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.mode." + mode));
		}
		long waiting = Json.lng(json, "waiting", 0);
		return waiting > 0
				? io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.next.waiting", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.mode." + mode), waiting)
				: io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.next.none", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.mode." + mode));
	}

	/** The open best / safest flip key: one lookup, then at most one command. */
	public void openNext(String mode) {
		if (!config.isReady()) {
			Chat.error(api.target().problem());
			return;
		}
		if (!active()) {
			Chat.warn(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.not_hypixel_flips"));
			return;
		}
		if (lookupInFlight) return; // a press while the last one is still looking does nothing
		lookupInFlight = true;
		List<String> skip;
		synchronized (this) {
			skip = new ArrayList<>(openedIds);
		}
		api.next(mode, skip).thenCompose(r -> {
			if (!r.ok()) {
				mc.execute(() -> Chat.error(r.message()));
				return java.util.concurrent.CompletableFuture.completedFuture(null);
			}
			FlipEvent f = FlipEvent.parse(Json.obj(r.json(), "flip"));
			if (f == null || !f.commandMatches()) {
				String msg = noFlipMessage(mode, r.json());
				mc.execute(() -> Chat.info(msg));
				return java.util.concurrent.CompletableFuture.completedFuture(null);
			}
			return api.auction(f.auctionId()).thenAccept(state -> {
				String s = state.ok() ? Json.str(state.json(), "state") : "unknown";
				if ("sold".equals(s) || "gone".equals(s)) {
					synchronized (this) {
						openedIds.add(f.auctionId());
					}
					mc.execute(() -> Chat.warn(io.github.donttemptfate.fatesflipper.core.Tr.tr("sold".equals(s) ? "fatesflipper.next.sold" : "fatesflipper.next.gone", f.name())));
					return;
				}
				mc.execute(() -> {
					if (openAuction(f.auctionId(), f)) Chat.info(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.msg.opening", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.mode." + mode), f.name()));
				});
			});
		}).whenComplete((v, ex) -> lookupInFlight = false);
	}
}
