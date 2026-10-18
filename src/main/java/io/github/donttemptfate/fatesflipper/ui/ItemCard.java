package io.github.donttemptfate.fatesflipper.ui;

import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatescommon.ui.Ui;

import io.github.donttemptfate.fatesflipper.PriceTooltips;
import io.github.donttemptfate.fatescommon.text.LegacyText;
import io.github.donttemptfate.fatescommon.skyblock.LoreParts;
import io.github.donttemptfate.fatescommon.ui.Card.Rule;
import io.github.donttemptfate.fatescommon.ui.Card.Stats;
import io.github.donttemptfate.fatescommon.ui.Card.Text;
import io.github.donttemptfate.fatescommon.ui.Ui.Role;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The full item tooltip in the Fates look (design 2.4): the item's own lore, grouped into sections
 * (stats, enchants, …) by SkyBlock's standard layout, then the Fates price block. Lines it doesn't
 * recognise are shown as they are, so nothing from Hypixel is lost.
 */
public final class ItemCard {
	private ItemCard() {}

	static final Pattern STAT = Pattern.compile("^[A-Za-z][A-Za-z' ]{1,28}: .*[0-9\\[].*$");
	static final Pattern ROMAN = Pattern.compile("^[A-Z][A-Za-z' -]+ ([IVXLC]+|[0-9]+)(,.*)?( [0-9,]+)?$");
	static final Pattern BAR = Pattern.compile("^[\\s]*[-━▬■─=]{8,}.*$");
	static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "ULTIMATE", "ADMIN"};

	public static Card build(ItemStack stack, PriceTooltips.View view) {
		return build(stack, view, List.of());
	}

	/** {@code extra}: lines other mods add to this item's tooltip, shown in a MODS section (audit F-C1). */
	public static Card build(ItemStack stack, PriceTooltips.View view, List<Component> extra) {
		Card c = new Card(0.94f, 120, 230).gap(1);
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		if (name == null) name = stack.getHoverName();
		c.add(new Text(noFakeBold(name), Role.NAME, 0, 0));

		var facts = io.github.donttemptfate.fatesflipper.ItemBytes.facts(stack);
		List<Component> all = facts == null ? List.of() : facts.lore();
		// Hypixel's auction listing lines (Auctions Browser) get their own LISTING section
		int cut = facts == null ? -1 : facts.listingCut();
		List<Component> lore = cut < 0 ? all : new ArrayList<>(all.subList(0, cut));
		List<Component> listing = cut < 0 ? List.of() : all.subList(cut, all.size());
		int rarity = rarityLine(lore);
		List<List<Component>> blocks = blocks(lore, rarity < 0 ? lore.size() : rarity);
		if (!blocks.isEmpty() && isSubtitle(blocks.get(0))) {
			// "Efficiency V · Farming Tool": a short first block under the name
			MutableComponent sub = Component.empty();
			List<Component> b0 = blocks.remove(0);
			for (int i = 0; i < b0.size(); i++) {
				if (i > 0) sub.append(Component.literal(" · ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
				sub.append(Component.literal(plain(b0.get(i))).withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			c.add(new Text(sub, Role.SMALL, 0, 0));
		}
		boolean first = true;
		for (List<Component> b : blocks) {
			if (!first) c.space(3);
			addBlock(c, b);
			first = false;
		}
		if (rarity >= 0) c.space(3).add(new Text(noFakeBold(lore.get(rarity)), Role.BODY_BOLD, 0, 0));
		addListing(c, listing);
		addMods(c, extra);
		Cards.fatesBlock(c, view);
		return c;
	}

	/** Lines other mods add to the tooltip (prices, pet info…), as they are, so the Fates look never hides them. */
	static void addMods(Card c, List<Component> extra) {
		if (extra == null || extra.isEmpty()) return;
		c.space(2).add(new Rule("MODS", Ui.GRAY, null));
		int n = 0;
		for (Component line : extra) {
			if (++n > 24) break; // a runaway tooltip from another mod stays bounded
			c.add(new Text(line, Role.SMALL, 0, 3));
		}
	}

	/**
	 * Lines in the full tooltip that aren't the item's own name or lore, our lines, or vanilla's
	 * advanced-tooltip lines (item id, NBT count): what other mods added.
	 */
	public static List<Component> extraLines(List<Component> full, ItemStack stack) {
		var facts = io.github.donttemptfate.fatesflipper.ItemBytes.facts(stack);
		java.util.Map<String, Integer> own = new java.util.HashMap<>();
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		own.merge((name == null ? stack.getHoverName() : name).getString(), 1, Integer::sum);
		if (facts != null) for (Component l : facts.lore()) own.merge(l.getString(), 1, Integer::sum);
		List<Component> out = new ArrayList<>();
		for (Component line : full) {
			String s = line.getString();
			Integer left = own.get(s);
			if (left != null && left > 0) {
				own.put(s, left - 1);
				continue;
			}
			String p = LegacyText.strip(s).trim();
			if (p.matches("^[a-z0-9_.-]+:[a-z0-9_/.-]+$") || p.matches("^NBT: \\d+ tags?$")) continue;
			out.add(line);
		}
		// drop blank lines at the ends, and repeated blanks
		while (!out.isEmpty() && out.get(0).getString().isBlank()) out.remove(0);
		while (!out.isEmpty() && out.get(out.size() - 1).getString().isBlank()) out.remove(out.size() - 1);
		for (int i = out.size() - 1; i > 0; i--) if (out.get(i).getString().isBlank() && out.get(i - 1).getString().isBlank()) out.remove(i);
		return out;
	}

	/** Seller, price, time left…: key/value rows in one column (the countdown ticks in place); other lines as they are. */
	static void addListing(Card c, List<Component> listing) {
		Stats rows = new Stats(Role.SMALL).single();
		List<Component> after = new ArrayList<>();
		for (Component line : listing) {
			String p = plain(line);
			if (p.isEmpty() || LoreParts.isSeparator(p)) continue;
			Component[] kv = splitAtColon(line);
			if (kv != null && after.isEmpty()) rows.add(kv[0], kv[1]);
			else after.add(line);
		}
		if (rows.isEmpty() && after.isEmpty()) return;
		c.space(2).add(new Rule("LISTING", Ui.GRAY, null));
		if (!rows.isEmpty()) c.add(rows);
		for (Component line : after) c.add(new Text(line, Role.SMALL, 0, 0));
	}

	/** A short first block (1–2 short lines, no "key: value") reads as a subtitle. */
	static boolean isSubtitle(List<Component> b) {
		if (b.size() > 2) return false;
		for (Component line : b) {
			String p = plain(line);
			if (p.length() > 28 || p.contains(":") || p.endsWith("!") || p.endsWith(".")) return false;
		}
		return true;
	}

	static String plain(Component c) {
		return LegacyText.strip(c.getString()).trim();
	}

	static Component noFakeBold(Component c) {
		MutableComponent out = Component.empty();
		c.visit((style, text) -> {
			if (!text.isEmpty()) out.append(Component.literal(text).withStyle(style.withBold(false)));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** The rarity line (e.g. "RARE FARMING TOOL"): the last non-empty line, all capitals with a rarity word. */
	static int rarityLine(List<Component> lore) {
		for (int i = lore.size() - 1; i >= 0; i--) {
			String p = plain(lore.get(i));
			if (p.isEmpty()) continue;
			if (!p.equals(p.toUpperCase(Locale.ROOT))) return -1;
			for (String r : RARITIES) if (p.contains(r)) return i;
			return -1;
		}
		return -1;
	}

	static List<List<Component>> blocks(List<Component> lore, int end) {
		List<List<Component>> out = new ArrayList<>();
		List<Component> cur = new ArrayList<>();
		for (int i = 0; i < end; i++) {
			if (plain(lore.get(i)).isEmpty()) {
				if (!cur.isEmpty()) out.add(cur);
				cur = new ArrayList<>();
			} else {
				cur.add(lore.get(i));
			}
		}
		if (!cur.isEmpty()) out.add(cur);
		return out;
	}

	static int firstColor(Component c) {
		int[] col = {-1};
		c.visit((style, text) -> {
			if (text.isBlank()) return Optional.empty();
			TextColor tc = style.getColor();
			col[0] = tc == null ? 0xFFFFFF : tc.getValue();
			return Optional.of(Boolean.TRUE);
		}, Style.EMPTY);
		return col[0];
	}

	static boolean enchantColor(int rgb) {
		return rgb == 0x5555FF || rgb == 0xFF55FF || rgb == 0xAA00AA;
	}

	static void addBlock(Card c, List<Component> b) {
		// stats: "Farming Fortune: +79.5 (+3)"
		int stats = 0;
		for (Component line : b) if (STAT.matcher(plain(line)).matches() && plain(line).length() <= 48) stats++;
		if (stats >= Math.max(1, Math.ceil(b.size() * 0.6))) {
			c.add(new Rule("STATS", Ui.GRAY, null));
			Stats s = new Stats(Role.SMALL);
			for (Component line : b) {
				Component[] kv = splitAtColon(line);
				if (kv != null) s.add(kv[0], kv[1]);
				else c.add(new Text(line, Role.SMALL, 0, 0));
			}
			c.add(s);
			return;
		}
		// enchants: blue / pink names, grey descriptions under each
		if (enchantColor(firstColor(b.get(0))) && ROMAN.matcher(plain(b.get(0))).matches()) {
			int count = 0;
			for (Component line : b) {
				if (enchantColor(firstColor(line))) count += plain(line).split(",").length;
			}
			c.add(new Rule("ENCHANTS · " + count, Ui.GRAY, null));
			List<Component> desc = new ArrayList<>();
			for (Component line : b) {
				if (enchantColor(firstColor(line))) {
					flushDesc(c, desc);
					c.add(new Text(line, Role.BODY, 0, 0));
				} else {
					desc.add(line);
				}
			}
			flushDesc(c, desc);
			return;
		}
		for (Component line : b) {
			String p = plain(line);
			if (BAR.matcher(p).matches()) {
				double f = barFraction(line);
				if (f >= 0) {
					c.add(new Card.Bar(f, Ui.GREEN));
					continue;
				}
			}
			c.add(new Text(line, Role.BODY, 0, 0));
		}
	}

	static void flushDesc(Card c, List<Component> desc) {
		if (desc.isEmpty()) return;
		MutableComponent joined = Component.empty();
		for (int i = 0; i < desc.size(); i++) {
			if (i > 0) joined.append(" ");
			joined.append(desc.get(i));
		}
		c.add(new Text(joined, Role.SMALL, 6, 2));
		desc.clear();
	}

	/** "Key: value" → [key, value] keeping the value's colours; null when there's no ": ". */
	static Component[] splitAtColon(Component line) {
		MutableComponent key = Component.empty();
		MutableComponent value = Component.empty();
		boolean[] inValue = {false};
		line.visit((style, text) -> {
			if (inValue[0]) {
				value.append(Component.literal(text).withStyle(style));
			} else {
				int i = text.indexOf(": ");
				if (i < 0) {
					key.append(Component.literal(text).withStyle(style));
				} else {
					key.append(Component.literal(text.substring(0, i)).withStyle(style));
					String rest = text.substring(i + 2);
					if (!rest.isEmpty()) value.append(Component.literal(rest).withStyle(style));
					inValue[0] = true;
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		return inValue[0] ? new Component[] {key, value} : null;
	}

	/** Share of a progress bar line drawn in its first colour (the filled part), or -1. */
	static double barFraction(Component line) {
		int[] first = {-2};
		int[] filled = {0};
		int[] total = {0};
		line.visit((style, text) -> {
			int n = 0;
			for (char ch : text.toCharArray()) if ("-━▬■─=".indexOf(ch) >= 0) n++;
			if (n == 0) return Optional.empty();
			TextColor tc = style.getColor();
			int col = tc == null ? -1 : tc.getValue();
			if (first[0] == -2) first[0] = col;
			if (col == first[0]) filled[0] += n;
			total[0] += n;
			return Optional.empty();
		}, Style.EMPTY);
		return total[0] == 0 ? -1 : (double) filled[0] / total[0];
	}
}
