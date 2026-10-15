package io.github.donttemptfate.fatesflipper;

import io.github.donttemptfate.fatesflipper.core.LegacyText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.List;

/** Messages from the mod to the player's own chat (never sent to the server). */
public final class Chat {
	private Chat() {}

	static MutableComponent prefix() {
		return Component.literal("[FF] ").withStyle(ChatFormatting.GOLD);
	}

	public static void send(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(message);
	}

	public static void info(String text) {
		send(prefix().append(Component.literal(LegacyText.strip(text)).withStyle(ChatFormatting.GRAY)));
	}

	public static void ok(String text) {
		send(prefix().append(Component.literal(LegacyText.strip(text)).withStyle(ChatFormatting.GREEN)));
	}

	public static void warn(String text) {
		send(prefix().append(Component.literal(LegacyText.strip(text)).withStyle(ChatFormatting.YELLOW)));
	}

	public static void error(String text) {
		if (text == null || text.isBlank()) return;
		send(prefix().append(Component.literal(LegacyText.strip(text)).withStyle(ChatFormatting.RED)));
	}

	/**
	 * Turns text with § colour codes into a component. Only colours and formatting carry over;
	 * click and hover actions are never created from the text.
	 */
	public static MutableComponent fromLegacy(String text) {
		MutableComponent out = Component.empty();
		List<LegacyText.Run> runs = LegacyText.parse(text);
		for (LegacyText.Run r : runs) {
			Style style = Style.EMPTY;
			if (r.color() != 0) {
				ChatFormatting f = ChatFormatting.getByCode(r.color());
				if (f != null) style = style.withColor(TextColor.fromLegacyFormat(f));
			}
			if (r.bold()) style = style.withBold(true);
			if (r.italic()) style = style.withItalic(true);
			if (r.underlined()) style = style.withUnderlined(true);
			if (r.strikethrough()) style = style.withStrikethrough(true);
			if (r.obfuscated()) style = style.withObfuscated(true);
			out.append(Component.literal(r.text()).withStyle(style));
		}
		return out;
	}
}
