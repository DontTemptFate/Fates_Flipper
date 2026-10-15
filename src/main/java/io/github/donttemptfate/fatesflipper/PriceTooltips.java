package io.github.donttemptfate.fatesflipper;

import com.google.gson.JsonObject;
import io.github.donttemptfate.fatesflipper.core.AuctionIds;
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
	}

	/** Set when the viewer doesn't accept whole items yet (404 / 405); retried after 10 minutes. */
	private static volatile long bytesUnsupportedUntilMs;
	static final long UNSUPPORTED_RETRY_MS = 10 * 60_000;

	static void add(FlipService service, ItemStack stack, List<Component> lines) {
		if (!service.config().tooltips || !service.active()) return;
		CompoundTag data = ItemBytes.skyblockData(stack);
		if (data == null) return; // not a SkyBlock item
		String uuid = AuctionIds.normalize(data.getStringOr("uuid", ""));
		PriceCache prices = service.itemPrices();

		PriceCache.Entry byUuid = null;
		if (uuid != null) {
			byUuid = prices.get("u:" + uuid, () -> service.api().priceByItem(uuid));
			if (byUuid == null) return;
			if (byUuid.state() == PriceCache.State.FOUND || (byUuid.state() == PriceCache.State.LOADING && byUuid.data() != null)) {
				addFound(byUuid.data(), lines);
				return;
			}
			if (byUuid.state() == PriceCache.State.LOADING) {
				lines.add(gray("Fates: loading price…"));
				return;
			}
			if (byUuid.state() == PriceCache.State.FAILED) return; // /ff status shows connection problems
		}

		// Never traded (or no uuid): ask with the whole item, if the viewer supports it.
		boolean bytesOk = System.currentTimeMillis() >= bytesUnsupportedUntilMs;
		if (bytesOk) {
			String key = uuid != null ? "b:" + uuid : ItemBytes.cacheKey(stack, data);
			// built only when a lookup actually starts (on this thread, before the method returns)
			PriceCache.Entry byItem = prices.get(key, () -> service.api().priceByItemBytes(ItemBytes.build(stack)));
			if (byItem != null) {
				switch (byItem.state()) {
					case FOUND -> {
						addFound(byItem.data(), lines);
						return;
					}
					case LOADING -> {
						if (byItem.data() != null) addFound(byItem.data(), lines);
						else lines.add(gray("Fates: loading price…"));
						return;
					}
					case NOT_FOUND -> {
						if (addRough(byItem.data(), lines)) return;
						lines.add(gray("Fates: no sales of this exact item yet"));
						return;
					}
					case FAILED -> {
						if (byItem.httpCode() == 404 || byItem.httpCode() == 405) {
							bytesUnsupportedUntilMs = System.currentTimeMillis() + UNSUPPORTED_RETRY_MS;
						}
					}
				}
			}
		}
		if (byUuid != null && byUuid.state() == PriceCache.State.NOT_FOUND) {
			if (addRough(byUuid.data(), lines)) return;
			lines.add(gray("Fates: not traded on the AH since tracking started"));
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
