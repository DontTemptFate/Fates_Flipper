package io.github.donttemptfate.fatesflipper.ui;

import io.github.donttemptfate.fatesflipper.FatesFlipperClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * The Fates look (design handoff 2026-10-17): vanilla tooltip purple frame, Minecraft colours,
 * Chakra Petch text in a few fixed sizes. Players can switch to the Minecraft font in settings; the
 * layout stays the same.
 */
public final class Ui {
	private Ui() {}

	// ---- colours (Minecraft palette) ------------------------------------------------------------
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFFAAAAAA;
	public static final int DARK = 0xFF555555;
	public static final int GOLD = 0xFFFFAA00;
	public static final int GREEN = 0xFF55FF55;
	public static final int RED = 0xFFFF5555;
	public static final int PINK = 0xFFFF55FF;
	public static final int AQUA = 0xFF55FFFF;
	public static final int YELLOW = 0xFFFFFF55;
	public static final int ORANGE = 0xFFFFA040;
	public static final int BLUE = 0xFF5555FF;
	public static final int BAR = 0xFFB048FF;
	public static final int RULE = 0xFF2A0B5E;
	public static final int TRACK = 0xFF1A0A30;
	public static final int BOX_GREEN = 0xFF2A8A2A;
	public static final int BOX_GOLD = 0xFF8A5C00;
	public static final int BOX_RED = 0xFF8A2A2A;
	static final int BORDER_OUTER = 0xFF5000FF;
	static final int BORDER_INNER = 0xFF28007F;
	static final int BG_RGB = 0x100010;

	/** Text sizes (Chakra Petch) — one font file per size, see assets/fatesflipper/font. */
	public enum Role {
		LABEL("ui_label", 6.5f),
		SMALL("ui_small", 7.5f),
		BODY("ui_body", 8.0f),
		BODY_BOLD("ui_body_bold", 8.0f),
		VALUE("ui_value", 8.5f),
		TAG("ui_tag", 7.0f),
		NAME("ui_name", 9.0f),
		TITLE("ui_title", 10.0f),
		HERO_S("ui_hero_s", 13.0f),
		HERO_M("ui_hero_m", 14.0f),
		HERO_L("ui_hero_l", 16.0f);

		final FontDescription font;
		final float size;

		Role(String id, float size) {
			this.font = new FontDescription.Resource(Identifier.fromNamespaceAndPath(FatesFlipperClient.MOD_ID, id));
			this.size = size;
		}

		/** Height of capitals above the baseline. */
		public int cap() {
			return chakra() ? Math.round(size * CAP) : (int) Math.ceil(7 * vanillaScale());
		}

		/** Room below the baseline (descenders, shadow). */
		public int desc() {
			return chakra() ? Math.max(2, Math.round(size * DESC)) : (int) Math.ceil(2 * vanillaScale());
		}

		public int height() {
			return cap() + desc();
		}

		/** With the Minecraft font, big numbers are drawn scaled up instead. */
		float vanillaScale() {
			return size >= 12 ? size / 8.5f : 1f;
		}
	}

	/** Cap height and descender of Chakra Petch as a share of the font size. */
	static final float CAP = 0.72f;
	static final float DESC = 0.28f;
	/**
	 * Where the baseline sits below the y a line of text is drawn at, as share of the font size
	 * for TrueType fonts (Minecraft's bitmap font: 7 px).
	 */
	static float baselineAt(Role r) {
		return chakra() ? BASELINE_K * r.size + BASELINE_C : 7f * r.vanillaScale();
	}

	static float BASELINE_K = 0f;
	static float BASELINE_C = 7f; // measured in game: TrueType glyphs sit on y + 7 like the bitmap font

	// ---- font choice -----------------------------------------------------------------------------
	private static volatile boolean chakra = true;

	public static void useChakra(boolean on) {
		chakra = on;
	}

	public static boolean chakra() {
		return chakra;
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	/** Text in one size and colour. */
	public static MutableComponent t(String text, Role role, int color) {
		Style s = Style.EMPTY.withColor(color & 0xFFFFFF);
		if (chakra) s = s.withFont(role.font);
		return Component.literal(text == null ? "" : text).withStyle(s);
	}

	/** A component (e.g. an item's own lore line) re-set in one size, keeping its colours and formatting. */
	public static MutableComponent refont(Component c, Role role) {
		MutableComponent out = Component.empty();
		c.visit((style, text) -> {
			if (!text.isEmpty()) {
				Style s = style;
				if (chakra) s = s.withFont(role.font);
				out.append(Component.literal(text).withStyle(s));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	public static int width(FormattedText c) {
		return font().width(c);
	}

	/** Draws text so its baseline lands at {@code baseline}. */
	static void draw(GuiGraphicsExtractor g, Component c, Role role, int x, int baseline) {
		if (!chakra && role.vanillaScale() != 1f) {
			float s = role.vanillaScale();
			g.pose().pushMatrix();
			g.pose().translate(x, baseline - 7 * s);
			g.pose().scale(s, s);
			g.text(font(), c, 0, 0, WHITE, true);
			g.pose().popMatrix();
			return;
		}
		g.text(font(), c, x, Math.round(baseline - baselineAt(role)), WHITE, true);
	}

	/** Width of text drawn in a role (scaled for the Minecraft font's big numbers). */
	static int width(Component c, Role role) {
		int w = font().width(c);
		return !chakra && role.vanillaScale() != 1f ? Math.round(w * role.vanillaScale()) : w;
	}

	// ---- shapes -----------------------------------------------------------------------------------
	/** Frame: 2 px halo, 2 px #5000FF, 2 px #28007F, then the background. {@code alpha} 0..1 for the background. */
	public static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
		int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
		int bg = (a << 24) | BG_RGB;
		int halo = (Math.round(a * 0.9f) << 24) | BG_RGB;
		ring(g, x - 2, y - 2, w + 4, h + 4, 2, halo);
		ring(g, x, y, w, h, 2, BORDER_OUTER);
		ring(g, x + 2, y + 2, w - 4, h - 4, 2, BORDER_INNER);
		g.fill(x + 4, y + 4, x + w - 4, y + h - 4, bg);
	}

	/** A hollow rectangle {@code t} pixels thick (no overlap, so translucent colours stay even). */
	public static void ring(GuiGraphicsExtractor g, int x, int y, int w, int h, int t, int color) {
		g.fill(x, y, x + w, y + t, color);
		g.fill(x, y + h - t, x + w, y + h, color);
		g.fill(x, y + t, x + t, y + h - t, color);
		g.fill(x + w - t, y + t, x + w, y + h - t, color);
	}

	/** Frame border plus padding: content starts this far in from the frame's edge. */
	public static final int INSET_X = 4 + 6;
	public static final int INSET_Y = 4 + 5;
}
