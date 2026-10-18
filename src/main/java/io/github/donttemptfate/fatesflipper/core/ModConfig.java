package io.github.donttemptfate.fatesflipper.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Player settings, saved as {@code config/fatesflipper.json}. The mod token is kept apart in
 * {@code config/fatesflipper-token.txt} (audit F-S1), so sharing the settings file or a modpack's
 * config folder never shares the token; it is never logged.
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Viewer address, e.g. https://flips.example.com. Empty = not set up yet. */
	public String serverUrl = "";
	/** Mod token from the web page (account menu → Mod tokens), starts with fv_. Saved in its own file. */
	public transient String token = "";

	/** Flip alerts in chat. */
	public boolean alerts = true;
	/** Sound with each alert (the active flip profile can also turn it off). */
	public boolean sound = true;
	/** Price lines on item tooltips. */
	public boolean tooltips = true;
	/** Highlights and side panel on auction windows opened from a flip. */
	public boolean auctionOverlay = true;
	/** Small connection status on screen (shown when something needs attention). */
	public boolean hudStatus = true;
	/**
	 * Cursor assist: places the cursor once on Buy, then once on Confirm, for auctions opened from
	 * a flip. Off by default; the player turns it on knowingly.
	 */
	public boolean cursorAssist = false;
	/** Pressing an open-flip key again inside that flip's auction window closes it (like Esc). */
	public boolean pressAgainToClose = true;
	/** The Fates look (purple cards, Chakra Petch) for the mod's panels, flip hovers and tooltips. */
	public boolean fatesLook = true;
	/** SkyBlock item tooltips in the Fates look (off = vanilla tooltips with price lines). */
	public boolean fatesTooltips = true;
	/**
	 * Chakra Petch font in the Fates look (off = Minecraft's font, same layout) and the colour theme.
	 * Since 0.4.0 both are shared with Fates OFA and saved by Fates Common in config/fatescommon.json;
	 * these two fields only mirror it (FlipService keeps them in step) and aren't saved here.
	 */
	public transient boolean chakraFont = true;
	public transient String theme = "fates";
	/** The font setting from a 0.3.x settings file, carried over once into the shared settings. */
	public transient Boolean legacyChakra;
	/** Only connect while on Hypixel. */
	public boolean onlyOnHypixel = true;
	/** Flip alerts only while in SkyBlock (not in lobbies or other games). */
	public boolean onlyInSkyBlock = true;
	/** Also alert inside dungeons and Kuudra. */
	public boolean alertsInDungeons = false;

	public transient Path file;

	public static ModConfig load(Path file) {
		ModConfig cfg = null;
		String legacyToken = null;
		Boolean legacyChakra = null;
		if (Files.isRegularFile(file)) {
			try {
				String text = Files.readString(file, StandardCharsets.UTF_8);
				cfg = GSON.fromJson(text, ModConfig.class);
				com.google.gson.JsonObject raw = Json.parseObject(text);
				legacyToken = Json.str(raw, "token"); // 0.3.3 and older kept it in this file
				if (raw != null && raw.has("chakraFont") && raw.get("chakraFont").isJsonPrimitive()) {
					legacyChakra = raw.get("chakraFont").getAsBoolean(); // 0.3.x kept the font here
				}
			} catch (Exception e) {
				cfg = null;
			}
		}
		if (cfg == null) cfg = new ModConfig();
		if (cfg.serverUrl == null) cfg.serverUrl = "";
		cfg.file = file;
		cfg.legacyChakra = legacyChakra;
		if (legacyChakra != null) cfg.chakraFont = legacyChakra;
		cfg.token = readToken(tokenFile(file));
		if (cfg.token.isEmpty() && legacyToken != null && !legacyToken.isBlank()) {
			cfg.token = legacyToken.trim();
			try {
				cfg.save(); // moves it to its own file and out of the settings file
			} catch (IOException ignored) {
				// keep using it; the next save moves it
			}
		}
		return cfg;
	}

	static Path tokenFile(Path settings) {
		return settings.resolveSibling("fatesflipper-token.txt");
	}

	static String readToken(Path f) {
		try {
			if (Files.isRegularFile(f) && Files.size(f) < 4096) return Files.readString(f, StandardCharsets.UTF_8).trim();
		} catch (IOException ignored) {
			// treated as no token
		}
		return "";
	}

	public void save() throws IOException {
		if (file == null) return;
		Files.createDirectories(file.getParent());
		writeAtomic(file, GSON.toJson(this));
		Path tf = tokenFile(file);
		if (token == null || token.isBlank()) Files.deleteIfExists(tf);
		else writeAtomic(tf, token.trim() + System.lineSeparator());
	}

	private static void writeAtomic(Path target, String text) throws IOException {
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.writeString(tmp, text, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** True when an address and a token are set and the address is acceptable. */
	public boolean isReady() {
		return !token.isBlank() && ServerAddress.check(serverUrl).ok();
	}

	public ModConfig copy() {
		ModConfig c = GSON.fromJson(GSON.toJson(this), ModConfig.class);
		c.file = file;
		c.token = token;
		c.chakraFont = chakraFont;
		c.theme = theme;
		c.legacyChakra = legacyChakra;
		return c;
	}

	/** The token with all but the first few characters hidden, for showing to the player. */
	public static String maskToken(String token) {
		if (token == null || token.isBlank()) return "(none)";
		String t = token.trim();
		int keep = Math.min(6, t.length());
		return t.substring(0, keep) + "…" + " (" + t.length() + " chars)";
	}
}
