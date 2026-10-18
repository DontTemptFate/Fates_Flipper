package io.github.donttemptfate.fatesflipper.ui;

import io.github.donttemptfate.fatesflipper.FatesFlipperClient;
import io.github.donttemptfate.fatesflipper.FlipService;
import io.github.donttemptfate.fatesflipper.core.FlipEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Chat flip alerts carry a hover text; this remembers which hover texts are ours, so hovering an
 * alert shows the flip card (design 2.3) instead of plain text. Any other hover stays vanilla.
 */
public final class FlipHovers {
	private FlipHovers() {}

	private static final Map<Component, FlipEvent> MINE = Collections.synchronizedMap(new WeakHashMap<>());

	public static void register(Component hoverText, FlipEvent flip) {
		MINE.put(hoverText, flip);
	}

	/** The flip a chat style's hover belongs to, or null. */
	public static FlipEvent flipFor(Style style) {
		if (style == null || !(style.getHoverEvent() instanceof HoverEvent.ShowText show)) return null;
		return MINE.get(show.value());
	}

	/** Draws the flip card for a hovered chat line; false = not ours (vanilla draws it). */
	public static boolean draw(GuiGraphicsExtractor g, Style style, int mouseX, int mouseY) {
		FlipService service = FatesFlipperClient.service();
		if (service == null || !service.config().fatesLook) return false;
		FlipEvent f = flipFor(style);
		if (f == null || io.github.donttemptfate.fatesflipper.core.SafeMode.off(io.github.donttemptfate.fatesflipper.core.SafeMode.Feature.HOVER)) return false;
		try {
			g.nextStratum();
			Cards.drawAtMouse(g, Cards.flipHover(f, service.chatPrefs().loreOnHover()), mouseX, mouseY, g.guiWidth(), g.guiHeight());
			return true;
		} catch (RuntimeException e) {
			io.github.donttemptfate.fatesflipper.core.SafeMode.error(io.github.donttemptfate.fatesflipper.core.SafeMode.Feature.HOVER, e);
			return false;
		}
	}
}
