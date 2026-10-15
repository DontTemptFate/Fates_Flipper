package io.github.donttemptfate.fatesflipper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds an item in the Hypixel API's own {@code item_bytes} layout (collector contract §3.4,
 * handoff-collector-to-mod-item-format-2026-10-15), so the collector fingerprints a hovered item
 * with the same parser it uses for every auction:
 *
 * <pre>{ i: [ { id, Count, tag: { ExtraAttributes: custom_data (unchanged),
 *                                display: { Name: "§…", Lore: ["§…", …] } } } ] }</pre>
 *
 * gzipped, then base64. Only the item's own data goes in: never lines other mods add to tooltips.
 */
public final class ItemBytes {
	private ItemBytes() {}

	static final int MAX_CUSTOM_DATA_BYTES = 64 * 1024;
	static final int MAX_LORE_LINES = 64;

	/** The item's SkyBlock data, or {@code null} when it has none (no custom data or no "id"). */
	public static CompoundTag skyblockData(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		CompoundTag tag = data.copyTag();
		return tag.getStringOr("id", "").isEmpty() ? null : tag;
	}

	/** A cheap key for caching an item without a uuid: same data, name, lore and count → same key. */
	public static String cacheKey(ItemStack stack, CompoundTag skyblockData) {
		ItemLore lore = stack.get(DataComponents.LORE);
		int h = Objects.hash(skyblockData, displayName(stack).getString(),
				lore == null ? 0 : lore.lines().stream().map(Component::getString).toList(), stack.getCount());
		return "h:" + Integer.toHexString(h) + ":" + skyblockData.getStringOr("id", "");
	}

	/** Base64 of the gzipped API-layout NBT, or {@code null} if the item can't be sent. */
	public static String build(ItemStack stack) {
		CompoundTag extra = skyblockData(stack);
		if (extra == null) return null;
		try {
			ByteArrayOutputStream raw = new ByteArrayOutputStream();
			NbtIo.write(extra, new java.io.DataOutputStream(raw));
			if (raw.size() > MAX_CUSTOM_DATA_BYTES) return null;
		} catch (IOException e) {
			return null;
		}

		CompoundTag display = new CompoundTag();
		display.putString("Name", legacy(displayName(stack)));
		ListTag loreTag = new ListTag();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			List<Component> lines = lore.lines();
			for (int i = 0; i < lines.size() && i < MAX_LORE_LINES; i++) loreTag.add(StringTag.valueOf(legacy(lines.get(i))));
		}
		display.put("Lore", loreTag);

		CompoundTag tag = new CompoundTag();
		tag.put("ExtraAttributes", extra);
		tag.put("display", display);

		CompoundTag item = new CompoundTag();
		item.putString("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		item.putByte("Count", (byte) Math.max(1, Math.min(127, stack.getCount())));
		item.put("tag", tag);

		ListTag list = new ListTag();
		list.add(item);
		CompoundTag root = new CompoundTag();
		root.put("i", list);

		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(root, out);
			return Base64.getEncoder().encodeToString(out.toByteArray());
		} catch (IOException e) {
			return null;
		}
	}

	static Component displayName(ItemStack stack) {
		Component custom = stack.get(DataComponents.CUSTOM_NAME);
		return custom != null ? custom : stack.getHoverName();
	}

	/**
	 * Text component → legacy "§" text, the way Hypixel's items look in the API: a colour code
	 * whenever the colour changes, then the formatting codes that apply.
	 */
	public static String legacy(Component component) {
		StringBuilder sb = new StringBuilder();
		String[] last = {null};
		component.visit((style, text) -> {
			if (text.isEmpty()) return Optional.empty();
			String codes = codes(style);
			if (!codes.equals(last[0])) {
				sb.append(codes);
				last[0] = codes;
			}
			sb.append(text);
			return Optional.empty();
		}, Style.EMPTY);
		return sb.toString();
	}

	private static String codes(Style style) {
		StringBuilder c = new StringBuilder();
		ChatFormatting color = legacyColor(style.getColor());
		c.append('§').append(color == null ? 'f' : color.getChar());
		if (style.isObfuscated()) c.append("§k");
		if (style.isBold()) c.append("§l");
		if (style.isStrikethrough()) c.append("§m");
		if (style.isUnderlined()) c.append("§n");
		if (style.isItalic()) c.append("§o");
		return c.toString();
	}

	/** The legacy colour with exactly this RGB (Hypixel only uses those), else the nearest one. */
	static ChatFormatting legacyColor(TextColor color) {
		if (color == null) return null;
		int rgb = color.getValue() & 0xFFFFFF;
		ChatFormatting best = null;
		long bestDist = Long.MAX_VALUE;
		for (ChatFormatting f : ChatFormatting.values()) {
			if (!f.isColor() || f.getColor() == null) continue;
			int c = f.getColor() & 0xFFFFFF;
			if (c == rgb) return f;
			long dr = ((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
			long dg = ((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
			long db = (c & 0xFF) - (rgb & 0xFF);
			long d = dr * dr + dg * dg + db * db;
			if (d < bestDist) {
				bestDist = d;
				best = f;
			}
		}
		return best;
	}
}
