package io.github.donttemptfate.fatesflipper.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLMouse;

/** The few calls that differ between Minecraft versions. This copy: 26.3 (SDL3). */
public final class Compat {
	private Compat() {}

	public static final String MINECRAFT = "26.3";

	/** A key binding with no key assigned (the player picks one in Controls). */
	public static KeyMapping unboundKey(String name, KeyMapping.Category category) {
		return new KeyMapping(name, InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category);
	}

	/** Moves the system cursor to window coordinates (screen pixels, not GUI-scaled). */
	public static void setCursor(Minecraft mc, double x, double y) {
		SDLMouse.SDL_WarpMouseInWindow(mc.getWindow().handle(), (float) x, (float) y);
	}
}
