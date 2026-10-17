package io.github.donttemptfate.fatesflipper;

import com.google.gson.JsonObject;
import io.github.donttemptfate.fatesflipper.core.AuctionIds;
import io.github.donttemptfate.fatesflipper.core.BazaarIds;
import io.github.donttemptfate.fatesflipper.core.LoreParts;
import io.github.donttemptfate.fatesflipper.core.Coins;
import io.github.donttemptfate.fatesflipper.core.Json;
import io.github.donttemptfate.fatesflipper.core.PriceCache;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Price lines on item tooltips (viewer contract §4.3), cached (see {@link PriceCache}):
 * <ol>
 *   <li>items with a SkyBlock uuid: {@code GET /mod/price?item_uuid=} (works for anything that
 *       traded since tracking started);</li>
 *   <li>items that never traded, or have no uuid: the whole item in the API's {@code item_bytes}
 *       layout, {@code POST /mod/price} (see {@link ItemBytes}). Viewers that don't accept that yet
 *       answer 404 / 405; then the mod stops asking for 10 minutes and shows the old text.</li>
 * </ol>
 */
public final class PriceTooltips {
	private PriceTooltips() {}

	public static void register(FlipService service) {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			try {
				add(service, stack, lines);
			} catch (RuntimeException e) {
				// a tooltip must never crash the game
			}
		});
		bazaar = new BazaarIds(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("fatesflipper-bazaar.txt"));
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			try {
				if (++ticks % 20 != 0) return;
				if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> acs) learnBazaar(acs);
				if (ticks % 200 == 0) bazaar.saveIfDirty();
			} catch (RuntimeException e) {
				// never crash the game
			}
		});
	}

	private static int ticks;
	/** Bazaar product ids learned from Hypixel's Bazaar windows (see {@link BazaarIds}). */
	private static volatile BazaarIds bazaar = new BazaarIds(null);

	public static BazaarIds bazaar() {
		return bazaar;
	}

	/** Once a second while a window is open: remembers the ids of Bazaar products shown in it. */
	static void learnBazaar(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
		for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
			if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
			ItemStack stack = slot.getItem();
			CompoundTag data = ItemBytes.skyblockData(stack);
			if (data == null || !data.getStringOr("uuid", "").isEmpty()) continue;
			if (LoreParts.isBazaarProduct(ItemBytes.plain(ItemBytes.lore(stack)))) bazaar.learn(data.getStringOr("id", ""));
		}
	}

	/**
	 * A Bazaar product: its Bazaar menu item (lore says "commodity" / shows buy and sell prices), or
	 * an item without a uuid whose id was seen in the Bazaar.
	 */
	static boolean isBazaar(ItemStack stack, CompoundTag data, String uuid) {
		if (uuid != null) return false;
		String id = data.getStringOr("id", "");
		if (LoreParts.isBazaarProduct(ItemBytes.plain(ItemBytes.lore(stack)))) {
			bazaar.learn(id);
			return true;
		}
		return bazaar.contains(id);
	}

	/** Set when the viewer doesn't accept whole items yet (404 / 405); retried after 10 minutes. */
	private static volatile long bytesUnsupportedUntilMs;
	static final long UNSUPPORTED_RETRY_MS = 10 * 60_000;

	/** What the price lookup has for an item right now (the same for tooltip lines and the Fates card). */
	public enum Kind { HIDDEN, LOADING, FOUND, NO_EXACT, NOT_TRADED, BAZAAR }

	public record View(Kind kind, JsonObject data) {
		static final View HIDDEN = new View(Kind.HIDDEN, null);
		static final View LOADING = new View(Kind.LOADING, null);
	}

	/** Looks the item up (cached; starts a lookup when needed) and says what to show. */
	public static View view(FlipService service, ItemStack stack) {
		if (!service.config().tooltips || !service.active()) return View.HIDDEN;
		CompoundTag data = ItemBytes.skyblockData(stack);
		if (data == null) return View.HIDDEN; // not a SkyBlock item
		String uuid = AuctionIds.normalize(data.getStringOr("uuid", ""));
		// Bazaar products: Hypixel shows their real prices; auction lookups would only say "no sales"
		if (isBazaar(stack, data, uuid)) return new View(Kind.BAZAAR, null);
		PriceCache prices = service.itemPrices();

		PriceCache.Entry byUuid = null;
		if (uuid != null) {
			byUuid = prices.get("u:" + uuid, () -> service.api().priceByItem(uuid));
			if (byUuid == null) return View.LOADING; // rate limit: try again next frame
			if (byUuid.state() == PriceCache.State.FOUND || (byUuid.state() == PriceCache.State.LOADING && byUuid.data() != null && Json.bool(byUuid.data(), "found", false))) {
				return new View(Kind.FOUND, byUuid.data());
			}
			if (byUuid.state() == PriceCache.State.LOADING && byUuid.data() == null) return View.LOADING;
			if (byUuid.state() == PriceCache.State.FAILED) return View.HIDDEN; // /ff status shows connection problems
		}

		// Never traded (or no uuid): ask with the whole item, if the viewer supports it.
		if (System.currentTimeMillis() >= bytesUnsupportedUntilMs) {
			String key = uuid != null ? "b:" + uuid : ItemBytes.cacheKey(stack, data);
			// built only when a lookup actually starts (on this thread, before the method returns)
			PriceCache.Entry byItem = prices.get(key, () -> service.api().priceByItemBytes(ItemBytes.build(stack)));
			if (byItem == null) return View.LOADING; // rate limit: try again next frame
			switch (byItem.state()) {
					case FOUND -> {
						return new View(Kind.FOUND, byItem.data());
					}
					case LOADING -> {
						// refreshing: keep showing the last answer, never flash back to "loading…"
						if (byItem.data() == null) return View.LOADING;
						return new View(Json.bool(byItem.data(), "found", false) ? Kind.FOUND : Kind.NO_EXACT, byItem.data());
					}
					case NOT_FOUND -> {
						return new View(Kind.NO_EXACT, byItem.data());
					}
					case FAILED -> {
						if (byItem.httpCode() == 404 || byItem.httpCode() == 405) {
							bytesUnsupportedUntilMs = System.currentTimeMillis() + UNSUPPORTED_RETRY_MS;
						}
					}
			}
		}
		if (byUuid != null && (byUuid.state() == PriceCache.State.NOT_FOUND || byUuid.data() != null)) return new View(Kind.NOT_TRADED, byUuid.data());
		return View.HIDDEN;
	}

	static void add(FlipService service, ItemStack stack, List<Component> lines) {
		View v = view(service, stack);
		switch (v.kind()) {
			case HIDDEN -> {
			}
			case LOADING -> lines.add(gray("Fates: loading price…"));
			case BAZAAR -> lines.add(gray("Fates: Bazaar item, see the Bazaar for prices"));
			case FOUND -> addFound(v.data(), lines);
			case NO_EXACT -> {
				if (!addRough(v.data(), lines)) lines.add(gray("Fates: no sales of this exact item yet"));
			}
			case NOT_TRADED -> {
				if (!addRough(v.data(), lines)) lines.add(gray("Fates: not traded on the AH since tracking started"));
			}
		}
	}

	/** Viewer v0.20.0+: a rough, display-only value for items without enough exact sales. */
	static boolean addRough(JsonObject d, List<Component> lines) {
		JsonObject rough = Json.obj(d, "rough");
		double price = Json.num(rough, "price", Double.NaN);
		if (Double.isNaN(price)) return false;
		lines.add(Component.literal("Fates ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("rough value ~" + Coins.format(price)).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(" (estimate, not a flip)").withStyle(ChatFormatting.DARK_GRAY)));
		return true;
	}

	private static void addFound(JsonObject d, List<Component> lines) {
		String conf = Json.str(d, "confidence");
		if (conf != null) conf = io.github.donttemptfate.fatesflipper.core.LegacyText.strip(conf);
		boolean learning = Json.bool(d, "learning", false);
		double market = Json.num(d, "market", Double.NaN);
		double cautious = Json.num(d, "cautious", Double.NaN);
		double lowest = Json.num(d, "lowest_bin", Double.NaN);
		double vol = Json.num(d, "volume_per_day", Double.NaN);
		int used = (int) Json.num(d, "sales_used", 0);

		if ((learning || Double.isNaN(market)) && addRough(d, lines)) return;
		MutableComponent head = Component.literal("Fates ").withStyle(ChatFormatting.GOLD);
		if (learning || Double.isNaN(market)) {
			head.append(Component.literal("MIXED".equals(conf) ? "prices split, no estimate" : "learning (too few sales)").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			head.append(Component.literal("value ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(Coins.format(market)).withStyle(ChatFormatting.YELLOW));
			if (!Double.isNaN(cautious)) {
				head.append(Component.literal(" · safe ").withStyle(ChatFormatting.GRAY))
						.append(Component.literal(Coins.format(cautious)).withStyle(ChatFormatting.YELLOW));
			}
			if (conf != null) head.append(Component.literal(" " + conf.toUpperCase(Locale.ROOT)).withStyle(confColor(conf)));
		}
		lines.add(head);

		MutableComponent second = Component.empty();
		boolean any = false;
		if (!Double.isNaN(lowest)) {
			second.append(Component.literal("lowest BIN ").withStyle(ChatFormatting.GRAY)).append(Component.literal(Coins.format(lowest)).withStyle(ChatFormatting.YELLOW));
			any = true;
		}
		if (!Double.isNaN(vol)) {
			second.append(Component.literal((any ? " · " : "") + String.format(Locale.ROOT, "%.1f sold/day", vol)).withStyle(ChatFormatting.GRAY));
			any = true;
		}
		if (used > 0) {
			second.append(Component.literal((any ? " · " : "") + used + " sales").withStyle(ChatFormatting.DARK_GRAY));
			any = true;
		}
		if (any) lines.add(Component.literal("  ").append(second));

		JsonObject dupe = Json.obj(d, "dupe");
		String level = Json.str(dupe, "level");
		if (level != null) level = io.github.donttemptfate.fatesflipper.core.LegacyText.strip(level);
		if (level != null) lines.add(Component.literal("  ⚠ dupe check: " + level).withStyle(ChatFormatting.RED));

		long last = Json.lng(d, "last_traded_at", 0);
		if (last > 0 && System.currentTimeMillis() - last > 24 * 3_600_000L) {
			lines.add(gray("  priced as it was when last traded, " + Coins.age(System.currentTimeMillis() - last) + " ago"));
		}
	}

	private static ChatFormatting confColor(String conf) {
		return switch (conf.toUpperCase(Locale.ROOT)) {
			case "HIGH" -> ChatFormatting.GREEN;
			case "MEDIUM" -> ChatFormatting.YELLOW;
			case "LOW" -> ChatFormatting.GOLD;
			default -> ChatFormatting.DARK_GRAY;
		};
	}

	private static Component gray(String s) {
		return Component.literal(s).withStyle(ChatFormatting.DARK_GRAY);
	}
}
