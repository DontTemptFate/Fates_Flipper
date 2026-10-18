package io.github.donttemptfate.fatesflipper;

import io.github.donttemptfate.fatescommon.skyblock.SkyBlockLocation;
import net.minecraft.client.Minecraft;

/**
 * Passes Fates Common's sidebar reading (in SkyBlock / in a dungeon or Kuudra, once a second) to the
 * flip stream (audit F-R6). Only on Hypixel; if the sidebar can't be read, alerts aren't blocked.
 */
public final class Location {
	private Location() {}

	private static int ticks;

	public static void tick(Minecraft mc, FlipService service) {
		if (++ticks % 20 != 0) return;
		if (!service.onHypixel()) return;
		if (!SkyBlockLocation.readable()) {
			service.setLocation(true, false);
			return;
		}
		SkyBlockLocation.Where w = SkyBlockLocation.current();
		service.setLocation(w.skyBlock(), w.instance());
	}
}
