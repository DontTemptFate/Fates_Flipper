package io.github.donttemptfate.fatesflipper;

import com.google.gson.JsonObject;
import io.github.donttemptfate.fatesflipper.core.AuctionIds;
import io.github.donttemptfate.fatesflipper.core.Coins;
import io.github.donttemptfate.fatesflipper.core.Json;
import io.github.donttemptfate.fatesflipper.core.PriceCache;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;

/**
 * Price lines on item tooltips, from the viewer's {@code /mod/price?item_uuid=} (viewer contract
 * §4.3). Only items with a SkyBlock item uuid; answers are cached (see {@link PriceCache}).
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

	static String itemUuid(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		CompoundTag tag = data.copyTag();
		return AuctionIds.normalize(tag.getStringOr("uuid", ""));
	}

	static void add(FlipService service, ItemStack stack, List<Component> lines) {
		if (!service.config().tooltips || !service.active()) return;
		String uuid = itemUuid(stack);
		if (uuid == null) return;
		PriceCache.Entry e = service.itemPrices().get(uuid);
		if (e == null) return;
		JsonObject d = e.data();
		switch (e.state()) {
			case LOADING -> {
				if (d == null) lines.add(gray("Fates: loading price…"));
				else addFound(d, lines);
			}
			case FOUND -> addFound(d, lines);
			case NOT_FOUND -> lines.add(gray("Fates: not traded on the AH since tracking started"));
			case FAILED -> {
				// stay quiet; /ff status shows connection problems
			}
		}
	}

	private static void addFound(JsonObject d, List<Component> lines) {
		String conf = Json.str(d, "confidence");
		boolean learning = Json.bool(d, "learning", false);
		double market = Json.num(d, "market", Double.NaN);
		double cautious = Json.num(d, "cautious", Double.NaN);
		double lowest = Json.num(d, "lowest_bin", Double.NaN);
		double vol = Json.num(d, "volume_per_day", Double.NaN);
		int used = (int) Json.num(d, "sales_used", 0);

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
