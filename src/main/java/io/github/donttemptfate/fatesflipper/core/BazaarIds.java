package io.github.donttemptfate.fatesflipper.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * SkyBlock item ids known to be Bazaar products, learned from Hypixel's own Bazaar windows (a
 * product's menu item says "commodity" / shows buy and sell prices). Bazaar items have no auction
 * history worth showing, so their tooltips say "Bazaar item" instead of "no sales". Kept in a small
 * text file next to the settings so it survives restarts.
 */
public final class BazaarIds {
	static final int MAX = 5000;
	static final Pattern ID = Pattern.compile("^[A-Z0-9_:;.\\-]{1,64}$");
	/** Generic ids that cover AH-only items too (an enchanted book's real content is in its data). */
	static final Set<String> NEVER = Set.of("ENCHANTED_BOOK", "PET", "RUNE", "UNIQUE_RUNE", "POTION", "ATTRIBUTE_SHARD");

	private final Path file;
	private final Set<String> ids = ConcurrentHashMap.newKeySet();
	private volatile boolean dirty;

	public BazaarIds(Path file) {
		this.file = file;
		if (file == null) return;
		try {
			if (Files.isRegularFile(file) && Files.size(file) < 512 * 1024) {
				for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					String id = line.trim();
					if (valid(id) && ids.size() < MAX) ids.add(id);
				}
			}
		} catch (IOException | RuntimeException e) {
			// a broken file just means relearning
		}
	}

	static boolean valid(String id) {
		return id != null && ID.matcher(id).matches() && !NEVER.contains(id);
	}

	public boolean contains(String id) {
		return id != null && ids.contains(id);
	}

	/** Remembers a Bazaar product id; true when it was new. */
	public boolean learn(String id) {
		if (!valid(id) || ids.size() >= MAX) return false;
		boolean added = ids.add(id);
		if (added) dirty = true;
		return added;
	}

	/** Writes the file if something new was learned (cheap no-op otherwise). */
	public synchronized void saveIfDirty() {
		if (!dirty || file == null) return;
		dirty = false;
		try {
			List<String> sorted = new ArrayList<>(ids);
			sorted.sort(null);
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.write(tmp, sorted, StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | RuntimeException e) {
			dirty = true;
		}
	}

	public int size() {
		return ids.size();
	}
}
