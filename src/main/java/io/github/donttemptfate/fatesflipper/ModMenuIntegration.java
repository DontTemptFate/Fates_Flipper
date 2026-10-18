package io.github.donttemptfate.fatesflipper;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's gear button opens the Fates Flipper settings (audit F-Q5). Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> {
			FlipService s = FatesFlipperClient.service();
			return s == null ? null : new ConfigScreen(parent, s);
		};
	}
}
