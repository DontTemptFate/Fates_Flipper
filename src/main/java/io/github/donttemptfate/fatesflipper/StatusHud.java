package io.github.donttemptfate.fatesflipper;

import io.github.donttemptfate.fatesflipper.core.FlipStream;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * A small line in the top-left corner, shown only when something needs attention (offline,
 * reconnecting, token problem) and for a few seconds after connecting.
 */
public final class StatusHud {
	private StatusHud() {}

	static final long SHOW_CONNECTED_MS = 6_000;

	public static void register(FlipService service) {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(FatesFlipperClient.MOD_ID, "status"),
				(g, delta) -> {
					Minecraft mc = Minecraft.getInstance();
					if (!service.config().hudStatus) return;
					String text = text(service);
					if (text == null) return;
					int color = service.streamState() == FlipStream.State.CONNECTED ? 0xFF55FF77 : 0xFFFFAA33;
					int w = mc.font.width(text) + 6;
					g.fill(2, 2, 2 + w, 4 + mc.font.lineHeight + 2, 0x90000000);
					g.text(mc.font, text, 5, 5, color, true);
				});
	}

	static String text(FlipService service) {
		if (!service.config().alerts || !service.active()) return null;
		long since = System.currentTimeMillis() - service.stateChangedAtMs();
		return switch (service.streamState()) {
			case CONNECTED -> since < SHOW_CONNECTED_MS
					? "Fates Flipper: connected" + (service.profileName() != null ? " · " + service.profileName() : "")
					: null;
			case CONNECTING -> since > 3_000 ? "Fates Flipper: connecting…" : null;
			case RETRYING -> "Fates Flipper: offline, reconnecting";
			case STOPPED -> "Fates Flipper: stopped (/ff status)";
			case WAITING -> "Fates Flipper: not set up (/ff config)";
			case OFF -> null;
		};
	}
}
