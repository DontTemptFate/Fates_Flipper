package io.github.donttemptfate.fatesflipper;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FatesFlipperClient implements ClientModInitializer {
	public static final String MOD_ID = "fatesflipper";
	public static final Logger LOG = LoggerFactory.getLogger("Fates Flipper");

	@Override
	public void onInitializeClient() {
		LOG.info("Fates Flipper loaded");
	}
}
