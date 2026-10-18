package io.github.donttemptfate.fatesflipper;

import io.github.donttemptfate.fatesflipper.core.LegacyText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Locale;

/**
 * Where the player is on Hypixel, read from the sidebar once a second (audit F-R6): SkyBlock or not,
 * and inside a dungeon / Kuudra run. Only reads what's on screen; no Hypixel Mod API (Fates OFA
 * registers that, and two mods registering it crash the game).
 */
public final class Location {
	private Location() {}

	private static int ticks;

	public static void tick(Minecraft mc, FlipService service) {
		if (++ticks % 20 != 0) return;
		if (!service.onHypixel() || mc.level == null) return;
		try {
			Read r = read(mc.level.getScoreboard());
			service.setLocation(r.skyBlock, r.instance);
		} catch (RuntimeException | LinkageError e) {
			service.setLocation(true, false); // can't tell: don't block alerts
		}
	}

	record Read(boolean skyBlock, boolean instance) {}

	static Read read(Scoreboard sb) {
		Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (obj == null) return new Read(false, false);
		String title = LegacyText.strip(obj.getDisplayName().getString()).toUpperCase(Locale.ROOT);
		boolean skyBlock = title.contains("SKYBLOCK") || title.contains("SKIBLOCK");
		boolean instance = false;
		if (skyBlock) {
			for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
				if (e.isHidden()) continue;
				PlayerTeam team = sb.getPlayersTeam(e.owner());
				Component line = PlayerTeam.formatNameForTeam(team, e.ownerName());
				if (isInstanceLine(line.getString())) {
					instance = true;
					break;
				}
			}
		}
		return new Read(skyBlock, instance);
	}

	/** Sidebar lines Hypixel shows in a dungeon ("The Catacombs (F7)") or Kuudra ("Kuudra's Hollow"). */
	static boolean isInstanceLine(String raw) {
		String p = LegacyText.strip(raw).toLowerCase(Locale.ROOT);
		return p.contains("the catacombs") || p.contains("kuudra's hollow") || p.contains("master mode catacombs");
	}
}
