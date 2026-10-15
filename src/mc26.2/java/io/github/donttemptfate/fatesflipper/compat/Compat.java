package io.github.donttemptfate.fatesflipper.compat;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** The few calls that differ between Minecraft versions. This copy: 26.2 (GLFW). */
public final class Compat {
	private Compat() {}

	public static final String MINECRAFT = "26.2";

	/** A key binding with no key assigned (the player picks one in Controls). */
	public static KeyMapping unboundKey(String name, KeyMapping.Category category) {
		return new KeyMapping(name, GLFW.GLFW_KEY_UNKNOWN, category);
	}

	/** Moves the system cursor to window coordinates (screen pixels, not GUI-scaled). */
	public static void setCursor(Minecraft mc, double x, double y) {
		GLFW.glfwSetCursorPos(mc.getWindow().handle(), x, y);
	}
}
