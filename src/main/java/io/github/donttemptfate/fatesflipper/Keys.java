package io.github.donttemptfate.fatesflipper;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import io.github.donttemptfate.fatesflipper.compat.Compat;

/**
 * Key bindings (Options → Controls → Key Binds → Fates Flipper). All unbound by default so they
 * never clash with other mods; the player picks the keys.
 */
public final class Keys {
	private Keys() {}

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(FatesFlipperClient.MOD_ID, "main"));

	public static KeyMapping openBest;
	public static KeyMapping openSafest;
	public static KeyMapping openSettings;

	public static void register() {
		openBest = KeyMappingHelper.registerKeyMapping(Compat.unboundKey("key.fatesflipper.open_best", CATEGORY));
		openSafest = KeyMappingHelper.registerKeyMapping(Compat.unboundKey("key.fatesflipper.open_safest", CATEGORY));
		openSettings = KeyMappingHelper.registerKeyMapping(Compat.unboundKey("key.fatesflipper.settings", CATEGORY));
	}

	/**
	 * Called every client tick. One press = one action: extra queued clicks from the same press
	 * (or from holding the key) are thrown away.
	 */
	public static void tick(Minecraft mc, FlipService service) {
		if (pressedOnce(openBest) && service.keyActionAllowed()) service.openNext("best");
		if (pressedOnce(openSafest) && service.keyActionAllowed()) service.openNext("safest");
		if (pressedOnce(openSettings)) mc.gui.setScreen(new ConfigScreen(mc.gui.screen(), service));
	}

	private static boolean pressedOnce(KeyMapping key) {
		if (key == null || !key.consumeClick()) return false;
		while (key.consumeClick()) {
			// drain: holding the key or pressing fast never queues more actions
		}
		return true;
	}
}
