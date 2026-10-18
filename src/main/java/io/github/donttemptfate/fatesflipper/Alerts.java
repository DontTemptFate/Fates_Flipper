package io.github.donttemptfate.fatesflipper;

import io.github.donttemptfate.fatesflipper.core.AuctionIds;
import io.github.donttemptfate.fatescommon.text.Coins;
import io.github.donttemptfate.fatesflipper.core.FlipEvent;
import io.github.donttemptfate.fatescommon.text.LegacyText;
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
		line(c, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.price"), Coins.format(f.price()), ChatFormatting.YELLOW);
		if (!Double.isNaN(f.resale())) line(c, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.resale"), Coins.format(f.resale()), ChatFormatting.YELLOW);
		if (!Double.isNaN(f.safeProfit())) {
			String pct = Double.isNaN(f.safePct()) ? "" : " (" + Coins.percent(f.safePct()) + ")";
			line(c, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.safe_profit"), (f.safeProfit() >= 0 ? "+" : "") + Coins.format(f.safeProfit()) + pct,
					f.safeProfit() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
		}
		if (!Double.isNaN(f.expectedProfit())) line(c, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.expected"), Coins.format(f.expectedProfit()), ChatFormatting.GREEN);
		if (!Double.isNaN(f.chance())) line(c, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.chance"), Coins.percent(f.chance()), ChatFormatting.AQUA);
		String conf = f.confidence() == null ? "?" : LegacyText.strip(f.confidence());
		String sales = f.salesUsed() > 0 ? ", " + io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.sales", f.salesUsed()) : "";
		String vol = Double.isNaN(f.volumePerDay()) ? "" : ", " + io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.per_day", String.format(Locale.ROOT, "%.1f", f.volumePerDay()));
		line(c, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.confidence"), conf + sales + vol, ChatFormatting.WHITE);
		if (f.dupe() != null) line(c, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.dupe"), LegacyText.strip(f.dupe()).toUpperCase(Locale.ROOT), ChatFormatting.RED);
		if (f.firstSeenAt() > 0) line(c, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.listed"), io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.ago", Coins.age(System.currentTimeMillis() - f.firstSeenAt())), ChatFormatting.GRAY);
		c.append(Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.alert.click")).withStyle(ChatFormatting.GOLD));
		io.github.donttemptfate.fatesflipper.ui.FlipHovers.register(c, f); // shown as the Fates flip card
		return c;
	}

	private static void line(MutableComponent c, String label, String value, ChatFormatting color) {
		c.append(Component.literal(label + ": ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(value).withStyle(color))
				.append("\n");
	}
}
