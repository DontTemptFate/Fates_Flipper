package io.github.donttemptfate.fatesflipper;

import io.github.donttemptfate.fatesflipper.core.AuctionIds;
import io.github.donttemptfate.fatesflipper.core.Coins;
import io.github.donttemptfate.fatesflipper.core.FlipEvent;
import io.github.donttemptfate.fatesflipper.core.LegacyText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/** Flip alerts in chat. */
public final class Alerts {
	private Alerts() {}

	/** Prints one flip. Clicking it runs {@code /ff open <id>}, which sends one /viewauction. */
	public static void show(FlipService service, FlipEvent f) {
		if (!service.config().alerts) return;
		if (!AuctionIds.isValid(f.auctionId())) return;
		String text = f.chat() != null && !f.chat().isBlank() ? f.chat() : f.fallbackChat();
		MutableComponent line = Chat.fromLegacy(text);
		MutableComponent full = Component.empty().append(line);
		full.withStyle(style -> style
				.withClickEvent(new ClickEvent.RunCommand("/ff open " + f.auctionId()))
				.withHoverEvent(new HoverEvent.ShowText(details(f))));
		Chat.send(full);
		if (service.alertSound()) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.6f));
		}
	}

	static Component details(FlipEvent f) {
		MutableComponent c = Component.empty();
		c.append(Chat.fromLegacy(f.name())).append("\n");
		if (f.summary() != null && !f.summary().isBlank()) {
			c.append(Component.literal(LegacyText.strip(f.summary())).withStyle(ChatFormatting.DARK_GRAY)).append("\n");
		}
		line(c, "Price", Coins.format(f.price()), ChatFormatting.YELLOW);
		if (!Double.isNaN(f.resale())) line(c, "Estimated resale", Coins.format(f.resale()), ChatFormatting.YELLOW);
		if (!Double.isNaN(f.safeProfit())) {
			String pct = Double.isNaN(f.safePct()) ? "" : " (" + Coins.percent(f.safePct()) + ")";
			line(c, "Cautious profit", (f.safeProfit() >= 0 ? "+" : "") + Coins.format(f.safeProfit()) + pct,
					f.safeProfit() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
		}
		if (!Double.isNaN(f.expectedProfit())) line(c, "Expected profit", Coins.format(f.expectedProfit()), ChatFormatting.GREEN);
		if (!Double.isNaN(f.chance())) line(c, "Chance to hit target", Coins.percent(f.chance()), ChatFormatting.AQUA);
		String conf = f.confidence() == null ? "?" : LegacyText.strip(f.confidence());
		String sales = f.salesUsed() > 0 ? ", " + f.salesUsed() + " sales" : "";
		String vol = Double.isNaN(f.volumePerDay()) ? "" : String.format(Locale.ROOT, ", %.1f/day", f.volumePerDay());
		line(c, "Confidence", conf + sales + vol, ChatFormatting.WHITE);
		if (f.dupe() != null) line(c, "Dupe check", LegacyText.strip(f.dupe()).toUpperCase(Locale.ROOT), ChatFormatting.RED);
		if (f.firstSeenAt() > 0) line(c, "Listed", Coins.age(System.currentTimeMillis() - f.firstSeenAt()) + " ago", ChatFormatting.GRAY);
		c.append(Component.literal("Click to open the auction").withStyle(ChatFormatting.GOLD));
		return c;
	}

	private static void line(MutableComponent c, String label, String value, ChatFormatting color) {
		c.append(Component.literal(label + ": ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(value).withStyle(color))
				.append("\n");
	}
}
