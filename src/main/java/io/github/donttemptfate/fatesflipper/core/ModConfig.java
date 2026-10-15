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
 * Player settings, saved as {@code config/fatesflipper.json}. The mod token lives here and nowhere
 * else; it is never logged.
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Viewer address, e.g. https://flips.example.com. Empty = not set up yet. */
	public String serverUrl = "";
	/** Mod token from the web page (account menu → Mod tokens), starts with fv_. */
	public String token = "";

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
	/** Only connect while on Hypixel. */
	public boolean onlyOnHypixel = true;

	public transient Path file;

	public static ModConfig load(Path file) {
		ModConfig cfg = null;
		if (Files.isRegularFile(file)) {
			try {
				cfg = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ModConfig.class);
			} catch (Exception e) {
				cfg = null;
			}
		}
		if (cfg == null) cfg = new ModConfig();
		if (cfg.serverUrl == null) cfg.serverUrl = "";
		if (cfg.token == null) cfg.token = "";
		cfg.file = file;
		return cfg;
	}

	public void save() throws IOException {
		if (file == null) return;
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** True when an address and a token are set and the address is acceptable. */
	public boolean isReady() {
		return !token.isBlank() && ServerAddress.check(serverUrl).ok();
	}

	public ModConfig copy() {
		ModConfig c = GSON.fromJson(GSON.toJson(this), ModConfig.class);
		c.file = file;
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
