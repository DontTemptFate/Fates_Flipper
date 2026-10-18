package io.github.donttemptfate.fatesflipper;

import io.github.donttemptfate.fatesflipper.core.FlipStream;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Objects;

/**
 * A small card in the top-left corner, shown only when something needs attention (offline,
 * reconnecting, token problem, update required) and for a few seconds after connecting. When the
 * connection is stopped or retrying, the reason is shown under it (audit F-R8). The card is rebuilt
 * only when what it says changes (audit F-P2).
 */
public final class StatusHud {
	private StatusHud() {}

	static final long SHOW_CONNECTED_MS = 6_000;

	private record Shown(String text, String detail, int color, boolean look) {}

	private static Shown lastShown;
	private static Card lastCard;

	public static void register(FlipService service) {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(FatesFlipperClient.MOD_ID, "status"),
				(g, delta) -> {
					Minecraft mc = Minecraft.getInstance();
					if (!service.config().hudStatus) return;
					String text = text(service);
					if (text == null) return;
					String detail = detail(service);
					FlipStream.State st = service.streamState();
					boolean bad = service.updateRequired() != null || st == FlipStream.State.STOPPED;
					int color = st == FlipStream.State.CONNECTED && !bad ? Ui.GREEN : bad ? Ui.RED : Ui.ORANGE;
					Shown now = new Shown(text, detail, color, service.config().fatesLook);
					if (now.look) {
						try {
							if (!now.equals(lastShown) || lastCard == null) {
								Card card = new Card(0.80f, 0, 260).add(new Card.Status(
										text.replace("Fates Flipper: ", "FATES · ").toUpperCase(Locale.ROOT), color, null));
								if (detail != null) card.add(new Card.Text(Ui.t(detail, Ui.Role.SMALL, Ui.GRAY), Ui.Role.SMALL, 0, 3));
								lastCard = card;
								lastShown = now;
							}
							lastCard.draw(g, 6, 6, 1f);
							return;
						} catch (RuntimeException e) {
							// fall back to the plain line below
						}
					}
					int w = mc.font.width(text) + 6;
					g.fill(2, 2, 2 + w, 4 + mc.font.lineHeight + 2, 0x90000000);
					g.text(mc.font, text, 5, 5, color, true);
					if (detail != null) {
						int dw = Math.min(mc.font.width(detail), 300) + 6;
						g.fill(2, 4 + mc.font.lineHeight + 2, 2 + dw, 6 + 2 * mc.font.lineHeight + 2, 0x90000000);
						g.text(mc.font, net.minecraft.network.chat.Component.literal(mc.font.plainSubstrByWidth(detail, 300)), 5, 7 + mc.font.lineHeight, 0xFFAAAAAA, true);
					}
				});
	}

	static String text(FlipService service) {
		if (!service.config().alerts || !service.active()) return null;
		if (service.updateRequired() != null) return "Fates Flipper: update required";
		long since = System.currentTimeMillis() - service.stateChangedAtMs();
		return switch (service.streamState()) {
			case CONNECTED -> since < SHOW_CONNECTED_MS
					? "Fates Flipper: connected" + (service.profileName() != null ? " · " + service.profileName() : "")
					: null;
			case CONNECTING -> since > 3_000 ? "Fates Flipper: connecting…" : null;
			case RETRYING -> "Fates Flipper: offline, reconnecting";
			case STOPPED -> "Fates Flipper: stopped";
			case WAITING -> "Fates Flipper: not set up (/ff config)";
			case OFF -> null;
		};
	}

	/** Why: the update message, or the connection's own reason while stopped / retrying. */
	static String detail(FlipService service) {
		if (service.updateRequired() != null) return service.updateRequired();
		FlipStream.State st = service.streamState();
		if (st != FlipStream.State.STOPPED && st != FlipStream.State.RETRYING) return null;
		String d = service.streamDetail();
		return d == null || d.isBlank() ? null : Objects.requireNonNull(d);
	}
}
