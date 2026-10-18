package io.github.donttemptfate.fatesflipper;

import io.github.donttemptfate.fatesflipper.core.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.function.Function;

/**
 * Fates Flipper: the in-game part of the SkyBlock Market Intelligence Suite.
 *
 * <p>Display only. The mod shows flips from Fates Viewer, opens an auction (one /viewauction per
 * key press or click), draws over Hypixel's auction windows and adds price tooltips. It never
 * clicks, buys, confirms or repeats anything for the player.
 */
public final class FatesFlipperClient implements ClientModInitializer {
	public static final String MOD_ID = "fatesflipper";
	public static final Logger LOG = LoggerFactory.getLogger("Fates Flipper");

	private static FlipService service;
	private static Function<Screen, Screen> pendingScreen;

	public static FlipService service() {
		return service;
	}

	/** Opens a screen on the next tick (a command's chat window would close it immediately). */
	public static void openScreenNextTick(Function<Screen, Screen> factory) {
		pendingScreen = factory;
	}

	@Override
	public void onInitializeClient() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		ModConfig config = ModConfig.load(FabricLoader.getInstance().getConfigDir().resolve("fatesflipper.json"));
		Minecraft mc = Minecraft.getInstance();
		// the player's language (and resource packs) can replace any of the mod's text (audit B-6)
		io.github.donttemptfate.fatesflipper.core.Tr.setLookup(key -> {
			var lang = net.minecraft.locale.Language.getInstance();
			return lang.has(key) ? lang.getOrDefault(key) : null;
		});
		service = new FlipService(mc, config, version);
		// safe mode: log a failing feature's first error, tell the player once when it switches off
		io.github.donttemptfate.fatesflipper.core.SafeMode.setListener((f, err) -> {
			if (err != null) {
				LOG.warn("Fates Flipper: {} failed (safe mode counts it)", f.id, err);
			} else {
				LOG.warn("Fates Flipper: {} kept failing and is off until restart or /ff safemode reset", f.id);
				mc.execute(() -> Chat.warn(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.safe.tripped", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.safe.f." + f.id))));
			}
		});

		Keys.register();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> Commands.register(dispatcher, service));
		AuctionOverlay.register(service);
		PriceTooltips.register(service);
		StatusHud.register(service);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			AuctionOverlay.trackMouse(client);
			Keys.tick(client, service);
			Location.tick(client, service);
			Function<Screen, Screen> open = pendingScreen;
			if (open != null) {
				pendingScreen = null;
				client.gui.setScreen(open.apply(client.gui.screen()));
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> service.setOnHypixel(isHypixel(client)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> service.setOnHypixel(false));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> service.shutdown());

		service.start();
		LOG.info("Fates Flipper {} loaded (server {})", version, config.isReady() ? "set" : "not set up yet");
	}

	static boolean isHypixel(Minecraft client) {
		var server = client.getCurrentServer();
		if (server == null || server.ip == null) return false;
		String ip = server.ip.toLowerCase(Locale.ROOT);
		int colon = ip.lastIndexOf(':');
		if (colon > 0 && ip.indexOf(':') == colon) ip = ip.substring(0, colon);
		return ip.equals("hypixel.net") || ip.endsWith(".hypixel.net");
	}
}
