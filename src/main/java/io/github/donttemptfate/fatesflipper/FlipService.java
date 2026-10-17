package io.github.donttemptfate.fatesflipper;

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

	public FlipService(Minecraft mc, ModConfig config, String version) {
		this.mc = mc;
		this.config = config;
		this.api = new ViewerApi(() -> this.config, version);
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
		return config;
	}

	/** Saves new settings and reconnects with them. */
	public void applyConfig(ModConfig next) {
		ModConfig prev = this.config;
		this.config = next;
		try {
			next.save();
		} catch (Exception e) {
			FatesFlipperClient.LOG.warn("Couldn't save settings: {}", e.toString());
			Chat.error("Couldn't save settings: " + e.getClass().getSimpleName());
		}
		boolean connectionChanged = !prev.serverUrl.equals(next.serverUrl) || !prev.token.equals(next.token);
		if (connectionChanged) {
			itemPrices.clear();
			announcedConnected = false;
			profileName = null;
			chatSettings = null;
			stream.restart();
		} else if (prev.alerts != next.alerts || prev.onlyOnHypixel != next.onlyOnHypixel) {
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
		return config.alerts && active();
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
				Chat.info("Connected to Fates Viewer. Flips will show here.");
				refreshChatSettings();
			} else if (state == FlipStream.State.STOPPED && prev != FlipStream.State.STOPPED) {
				Chat.error(detail);
			}
		});
	}

	@Override
	public void onFlip(JsonObject json) {
		FlipEvent f = FlipEvent.parse(json);
		if (f == null) return;
		if (!f.commandMatches()) {
			FatesFlipperClient.LOG.warn("Ignored a flip whose command wasn't /viewauction <id>");
			return;
		}
		synchronized (this) {
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
			mc.execute(() -> Chat.info(name == null ? "Flip profile turned off: no flips will be sent." : "Active flip profile: " + name));
			refreshChatSettings();
		}
	}

	/** Reads the active profile's chat settings (sound etc.) from {@code /api/profiles}. */
	public void refreshChatSettings() {
		api.profileSettings().thenAccept(r -> {
			if (!r.ok()) return;
			JsonObject json = r.json();
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
			Chat.error("That isn't an auction id.");
			return false;
		}
		if (mc.player == null) return false;
		if (!onHypixel && config.onlyOnHypixel) {
			Chat.warn("Not on Hypixel: auctions only open there.");
			return false;
		}
		if (!commandCooldown.tryAcquire(System.currentTimeMillis())) {
			Chat.warn("Slow down: one auction every half second.");
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
				return "No " + mode + " flip ready yet: " + held + " coming" + (nextIn >= 0 ? " in " + nextIn + " s." : " soon.");
			}
			if (matching > 0 && skipped >= matching) {
				return "No new " + mode + " flip: " + matching + " matched, all already opened.";
			}
			return "No " + mode + " flip right now.";
		}
		long waiting = Json.lng(json, "waiting", 0);
		return waiting > 0
				? "No new " + mode + " flip right now (" + waiting + " matched, already opened or not visible yet)."
				: "No " + mode + " flip right now.";
	}

	/** The open best / safest flip key: one lookup, then at most one command. */
	public void openNext(String mode) {
		if (!config.isReady()) {
			Chat.error(api.target().problem());
			return;
		}
		if (!active()) {
			Chat.warn("Not on Hypixel: flips only open there.");
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
					mc.execute(() -> Chat.warn(f.name() + " already " + s + ". Press again for the next one."));
					return;
				}
				mc.execute(() -> {
					if (openAuction(f.auctionId(), f)) Chat.info("Opening " + mode + ": " + f.name());
				});
			});
		}).whenComplete((v, ex) -> lookupInFlight = false);
	}
}
