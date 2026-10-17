package io.github.donttemptfate.fatesflipper.ui;

import io.github.donttemptfate.fatesflipper.ui.Ui.Role;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * A framed card made of rows (header, hero number, section rule, grid, key/value rows, sales bars,
 * status line, wrapped text, progress bar). Measure with {@link #width()} / {@link #height(int)},
 * draw with {@link #draw}. Everything in GUI pixels; the whole card can be drawn scaled.
 */
public final class Card {
	private final List<El> rows = new ArrayList<>();
	private final List<Integer> gaps = new ArrayList<>();
	private final float alpha;
	private final int minW;
	private final int maxW;
	private int gap = 3;

	public Card(float alpha, int minW, int maxW) {
		this.alpha = alpha;
		this.minW = minW;
		this.maxW = maxW;
	}

	/** Space before the next row added. */
	public Card gap(int px) {
		this.gap = px;
		return this;
	}

	public Card add(El el) {
		gaps.add(rows.isEmpty() ? 0 : gap);
		rows.add(el);
		return this;
	}

	/** Extra space before the last row (on top of the normal gap). */
	public Card space(int px) {
		if (!gaps.isEmpty()) gaps.set(gaps.size() - 1, gaps.get(gaps.size() - 1) + px);
		return this;
	}

	public boolean isEmpty() {
		return rows.isEmpty();
	}

	/** Content width the card wants (between its min and max). */
	public int contentWidth() {
		int w = 0;
		for (El e : rows) w = Math.max(w, e.prefWidth(maxW));
		return Math.max(minW, Math.min(maxW, w));
	}

	public int width() {
		return contentWidth() + 2 * Ui.INSET_X;
	}

	public int height() {
		int cw = contentWidth();
		int h = 0;
		for (int i = 0; i < rows.size(); i++) h += gaps.get(i) + rows.get(i).height(cw);
		return h + 2 * Ui.INSET_Y;
	}

	/** Draws at (x, y) in GUI pixels, scaled by {@code scale} (1 = normal). */
	public void draw(GuiGraphicsExtractor g, int x, int y, float scale) {
		int cw = contentWidth();
		int w = cw + 2 * Ui.INSET_X;
		int h = height();
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		if (scale != 1f) g.pose().scale(scale, scale);
		Ui.frame(g, 0, 0, w, h, alpha);
		int cy = Ui.INSET_Y;
		for (int i = 0; i < rows.size(); i++) {
			cy += gaps.get(i);
			El e = rows.get(i);
			e.draw(g, Ui.INSET_X, cy, cw);
			cy += e.height(cw);
		}
		g.pose().popMatrix();
	}

	// ==== elements ==================================================================================

	public interface El {
		/** Natural width (up to {@code max}). */
		int prefWidth(int max);

		int height(int w);

		void draw(GuiGraphicsExtractor g, int x, int y, int w);
	}

	/** One run of text in one size and colour. */
	public record Seg(String text, Role role, int color) {
		Component c() {
			return Ui.t(text, role, color);
		}

		int width() {
			return Ui.width(c(), role);
		}
	}

	public static Seg seg(String text, Role role, int color) {
		return new Seg(text, role, color);
	}

	static int cap(List<Seg> segs) {
		int c = 0;
		for (Seg s : segs) c = Math.max(c, s.role().cap());
		return c;
	}

	static int desc(List<Seg> segs) {
		int d = 0;
		for (Seg s : segs) d = Math.max(d, s.role().desc());
		return d;
	}

	static int width(List<Seg> segs) {
		int w = 0;
		for (Seg s : segs) w += s.width();
		return w;
	}

	/** Draws segments on one baseline; returns the x after them. */
	static int drawSegs(GuiGraphicsExtractor g, List<Seg> segs, int x, int baseline) {
		for (Seg s : segs) {
			Ui.draw(g, s.c(), s.role(), x, baseline);
			x += s.width();
		}
		return x;
	}

	/** A line: segments on the left, optional segments pushed to the right. */
	public static final class Line implements El {
		final List<Seg> left;
		final List<Seg> right;

		public Line(List<Seg> left, List<Seg> right) {
			this.left = left;
			this.right = right == null ? List.of() : right;
		}

		public static Line of(Seg... left) {
			return new Line(List.of(left), List.of());
		}

		@Override
		public int prefWidth(int max) {
			return Math.min(max, width(left) + (right.isEmpty() ? 0 : 10 + width(right)));
		}

		@Override
		public int height(int w) {
			List<Seg> all = new ArrayList<>(left);
			all.addAll(right);
			return cap(all) + desc(all);
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			List<Seg> all = new ArrayList<>(left);
			all.addAll(right);
			int base = y + cap(all);
			drawSegs(g, left, x, base);
			if (!right.isEmpty()) drawSegs(g, right, x + w - width(right), base);
		}
	}

	/** Big number, a boxed tag beside it, a label on the far right. */
	public static final class Hero implements El {
		final List<Seg> number;
		final Seg tag;
		final int tagBorder;
		final List<Seg> right;
		boolean tagFar;

		public Hero(List<Seg> number, Seg tag, int tagBorder, List<Seg> right) {
			this.number = number;
			this.tag = tag;
			this.tagBorder = tagBorder;
			this.right = right == null ? List.of() : right;
		}

		/** Puts the boxed tag at the far right instead of beside the number. */
		public Hero tagFar() {
			this.tagFar = true;
			return this;
		}

		int tagW() {
			return tag == null ? 0 : tag.width() + 6 + 2;
		}

		int tagH() {
			return tag == null ? 0 : tag.role().cap() + 2 + 2 + 2;
		}

		@Override
		public int prefWidth(int max) {
			return Math.min(max, width(number) + (tag == null ? 0 : 5 + tagW()) + (right.isEmpty() ? 0 : 12 + width(right)));
		}

		@Override
		public int height(int w) {
			return Math.max(cap(number) + 2, tagH()) + 1;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			int base = y + Math.max(cap(number), tagH() - 2);
			int nx = drawSegs(g, number, x, base);
			if (tag != null) {
				int tx = tagFar ? x + w - tagW() : nx + 5;
				int bh = tagH();
				int by = base - bh + 1;
				Ui.ring(g, tx, by, tagW(), bh, 1, tagBorder);
				Ui.draw(g, tag.c(), tag.role(), tx + 4, by + 2 + tag.role().cap());
			}
			if (!right.isEmpty()) drawSegs(g, right, x + w - width(right), base);
		}
	}

	/** Section rule: LABEL ───── meta. */
	public static final class Rule implements El {
		final String label;
		final int labelColor;
		final String meta;

		public Rule(String label, int labelColor, String meta) {
			this.label = label;
			this.labelColor = labelColor;
			this.meta = meta;
		}

		@Override
		public int prefWidth(int max) {
			int w = Ui.width(Ui.t(label, Role.LABEL, labelColor), Role.LABEL) + 24;
			if (meta != null) w += 6 + Ui.width(Ui.t(meta, Role.LABEL, Ui.DARK), Role.LABEL);
			return Math.min(max, w);
		}

		@Override
		public int height(int w) {
			return Role.LABEL.cap() + 2;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			int base = y + Role.LABEL.cap();
			Component l = Ui.t(label, Role.LABEL, labelColor);
			int lw = Ui.width(l, Role.LABEL);
			Ui.draw(g, l, Role.LABEL, x, base);
			int lineEnd = x + w;
			if (meta != null) {
				Component m = Ui.t(meta, Role.LABEL, Ui.DARK);
				int mw = Ui.width(m, Role.LABEL);
				Ui.draw(g, m, Role.LABEL, x + w - mw, base);
				lineEnd = x + w - mw - 4;
			}
			int ly = base - Role.LABEL.cap() / 2;
			if (lineEnd > x + lw + 4) g.fill(x + lw + 4, ly, lineEnd, ly + 1, Ui.RULE);
		}
	}

	/** Label-over-value cells in equal columns. */
	public static final class Grid implements El {
		final int cols;
		final List<String> labels = new ArrayList<>();
		final List<List<Seg>> values = new ArrayList<>();

		public Grid(int cols) {
			this.cols = cols;
		}

		public Grid cell(String label, Seg... value) {
			labels.add(label);
			values.add(List.of(value));
			return this;
		}

		public boolean isEmpty() {
			return labels.isEmpty();
		}

		int cellW(int i) {
			return Math.max(Ui.width(Ui.t(labels.get(i), Role.LABEL, Ui.GRAY), Role.LABEL), width(values.get(i)));
		}

		int rowH() {
			int c = 0, d = 0;
			for (List<Seg> v : values) {
				c = Math.max(c, cap(v));
				d = Math.max(d, desc(v));
			}
			return Role.LABEL.cap() + 3 + c + d;
		}

		@Override
		public int prefWidth(int max) {
			int m = 0;
			for (int i = 0; i < labels.size(); i++) m = Math.max(m, cellW(i));
			return Math.min(max, cols * m + (cols - 1) * 8);
		}

		@Override
		public int height(int w) {
			int rows = (labels.size() + cols - 1) / cols;
			return rows == 0 ? 0 : rows * rowH() + (rows - 1) * 3;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			int colW = (w - (cols - 1) * 8) / cols;
			int rh = rowH();
			for (int i = 0; i < labels.size(); i++) {
				int cx = x + (i % cols) * (colW + 8);
				int cy = y + (i / cols) * (rh + 3);
				Ui.draw(g, Ui.t(labels.get(i), Role.LABEL, Ui.GRAY), Role.LABEL, cx, cy + Role.LABEL.cap());
				drawSegs(g, values.get(i), cx, cy + Role.LABEL.cap() + 3 + cap(values.get(i)));
			}
		}
	}

	/** Key on the left, value right-aligned; one row per entry. */
	public static final class KeyValues implements El {
		final List<Seg> keys = new ArrayList<>();
		final List<List<Seg>> values = new ArrayList<>();
		final int cols;

		public KeyValues(int cols) {
			this.cols = Math.max(1, cols);
		}

		public KeyValues row(Seg key, Seg... value) {
			keys.add(key);
			values.add(List.of(value));
			return this;
		}

		public boolean isEmpty() {
			return keys.isEmpty();
		}

		int rowH() {
			int h = 0;
			for (int i = 0; i < keys.size(); i++) {
				List<Seg> all = new ArrayList<>(values.get(i));
				all.add(keys.get(i));
				h = Math.max(h, cap(all) + desc(all));
			}
			return h;
		}

		@Override
		public int prefWidth(int max) {
			int m = 0;
			for (int i = 0; i < keys.size(); i++) m = Math.max(m, keys.get(i).width() + 10 + width(values.get(i)));
			return Math.min(max, cols * m + (cols - 1) * 10);
		}

		@Override
		public int height(int w) {
			int rows = (keys.size() + cols - 1) / cols;
			return rows == 0 ? 0 : rows * rowH() + (rows - 1);
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			int colW = (w - (cols - 1) * 10) / cols;
			int rh = rowH();
			for (int i = 0; i < keys.size(); i++) {
				int cx = x + (i % cols) * (colW + 10);
				int cy = y + (i / cols) * (rh + 1);
				List<Seg> all = new ArrayList<>(values.get(i));
				all.add(keys.get(i));
				int base = cy + cap(all);
				Ui.draw(g, keys.get(i).c(), keys.get(i).role(), cx, base);
				drawSegs(g, values.get(i), cx + colW - width(values.get(i)), base);
			}
		}
	}

	/** Recent sales: up to 4 columns, a bar (price ÷ highest) over "price age". */
	public static final class SalesStrip implements El {
		final double[] prices;
		final String[] ages;
		final boolean[] ignored;

		public SalesStrip(double[] prices, String[] ages, boolean[] ignored) {
			this.prices = prices;
			this.ages = ages;
			this.ignored = ignored;
		}

		@Override
		public int prefWidth(int max) {
			int m = 0;
			for (int i = 0; i < prices.length; i++) m = Math.max(m, label(i));
			return Math.min(max, prices.length * m + (prices.length - 1) * 5);
		}

		int label(int i) {
			return Ui.width(Ui.t(io.github.donttemptfate.fatesflipper.core.Coins.format(prices[i]) + " ", Role.SMALL, Ui.WHITE), Role.SMALL)
					+ Ui.width(Ui.t(ages[i], Role.SMALL, Ui.DARK), Role.SMALL);
		}

		@Override
		public int height(int w) {
			return 2 + 2 + Role.SMALL.height();
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			int n = prices.length;
			if (n == 0) return;
			int colW = (w - (n - 1) * 5) / n;
			double max = 0;
			for (double p : prices) max = Math.max(max, p);
			for (int i = 0; i < n; i++) {
				int cx = x + i * (colW + 5);
				int bw = max > 0 ? (int) Math.max(2, Math.round(colW * prices[i] / max)) : 2;
				g.fill(cx, y, cx + bw, y + 2, ignored[i] ? Ui.DARK : Ui.BAR);
				int base = y + 4 + Role.SMALL.cap();
				Component p = Ui.t(io.github.donttemptfate.fatesflipper.core.Coins.format(prices[i]) + " ", Role.SMALL, ignored[i] ? Ui.DARK : Ui.WHITE);
				Ui.draw(g, p, Role.SMALL, cx, base);
				Ui.draw(g, Ui.t(ages[i], Role.SMALL, Ui.DARK), Role.SMALL, cx + Ui.width(p, Role.SMALL), base);
			}
		}
	}

	/** Status: a glowing square, a label, an optional hint on the right. */
	public static final class Status implements El {
		final String label;
		final int color;
		final String hint;

		public Status(String label, int color, String hint) {
			this.label = label;
			this.color = color;
			this.hint = hint;
		}

		@Override
		public int prefWidth(int max) {
			int w = 4 + 5 + Ui.width(Ui.t(label, Role.LABEL, color), Role.LABEL);
			if (hint != null) w += 10 + Ui.width(Ui.t(hint, Role.LABEL, Ui.DARK), Role.LABEL);
			return Math.min(max, w);
		}

		@Override
		public int height(int w) {
			return Math.max(Role.LABEL.cap() + 2, 6);
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			int base = y + Math.max(Role.LABEL.cap(), 4);
			int dy = base - Role.LABEL.cap() / 2 - 2;
			int rgb = color & 0xFFFFFF;
			g.fill(x - 2, dy - 2, x + 6, dy + 6, 0x18000000 | rgb);
			g.fill(x - 1, dy - 1, x + 5, dy + 5, 0x30000000 | rgb);
			g.fill(x, dy, x + 4, dy + 4, color);
			Ui.draw(g, Ui.t(label, Role.LABEL, color), Role.LABEL, x + 9, base);
			if (hint != null) {
				Component h = Ui.t(hint, Role.LABEL, Ui.DARK);
				Ui.draw(g, h, Role.LABEL, x + w - Ui.width(h, Role.LABEL), base);
			}
		}
	}

	/** A component wrapped to the card width (keeps its own colours), optionally indented. */
	public static final class Text implements El {
		final MutableComponent text;
		final Role role;
		final int indent;
		final int maxLines;

		public Text(Component text, Role role, int indent, int maxLines) {
			this.text = Ui.refont(text, role);
			this.role = role;
			this.indent = indent;
			this.maxLines = maxLines;
		}

		List<FormattedCharSequence> lines(int w) {
			List<FormattedCharSequence> l = Ui.font().split(text, Math.max(20, w - indent));
			if (maxLines > 0 && l.size() > maxLines) {
				List<FormattedCharSequence> cut = new ArrayList<>(l.subList(0, maxLines));
				// mark the cut with an ellipsis on the last kept line
				cut.set(maxLines - 1, FormattedCharSequence.composite(cut.get(maxLines - 1), Ui.t("…", role, Ui.GRAY).getVisualOrderText()));
				return cut;
			}
			return l;
		}

		@Override
		public int prefWidth(int max) {
			return Math.min(max, indent + Ui.width(text));
		}

		@Override
		public int height(int w) {
			int n = lines(w).size();
			return n == 0 ? 0 : n * (role.height() + 1) - 1;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			int base = y + role.cap();
			for (FormattedCharSequence s : lines(w)) {
				g.text(Ui.font(), s, x + indent, Math.round(base - Ui.baselineAt(role)), Ui.WHITE, true);
				base += role.height() + 1;
			}
		}
	}

	/** A thin progress bar. */
	public static final class Bar implements El {
		final double fraction;
		final int color;

		public Bar(double fraction, int color) {
			this.fraction = Math.max(0, Math.min(1, fraction));
			this.color = color;
		}

		@Override
		public int prefWidth(int max) {
			return Math.min(max, 60);
		}

		@Override
		public int height(int w) {
			return 4;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			g.fill(x, y, x + w, y + 4, Ui.TRACK);
			Ui.ring(g, x, y, w, 4, 1, Ui.RULE);
			int fw = (int) Math.round((w - 2) * fraction);
			if (fw > 0) g.fill(x + 1, y + 1, x + 1 + fw, y + 3, color);
		}
	}

	/** Key / value pairs whose values keep their own colours (item stats), in 1 or 2 columns. */
	public static final class Stats implements El {
		final List<Component> keys = new ArrayList<>();
		final List<MutableComponent> values = new ArrayList<>();
		final Role role;

		public Stats(Role role) {
			this.role = role;
		}

		public Stats add(Component key, Component value) {
			keys.add(Ui.refont(key, role));
			values.add(Ui.refont(value, role));
			return this;
		}

		int entryW(int i) {
			return Ui.width(keys.get(i)) + 8 + Ui.width(values.get(i));
		}

		int cols(int w) {
			if (keys.size() < 2) return 1;
			for (int i = 0; i < keys.size(); i++) if (entryW(i) > (w - 12) / 2) return 1;
			return 2;
		}

		@Override
		public int prefWidth(int max) {
			int m = 0;
			for (int i = 0; i < keys.size(); i++) m = Math.max(m, entryW(i));
			return Math.min(max, keys.size() >= 2 && 2 * m + 12 <= max ? 2 * m + 12 : m);
		}

		@Override
		public int height(int w) {
			int c = cols(w);
			int rows = (keys.size() + c - 1) / c;
			return rows == 0 ? 0 : rows * (role.height() + 1) - 1;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			int c = cols(w);
			int colW = c == 1 ? w : (w - 12) / 2;
			for (int i = 0; i < keys.size(); i++) {
				int cx = x + (i % c) * (colW + 12);
				int base = y + (i / c) * (role.height() + 1) + role.cap();
				int top = Math.round(base - Ui.baselineAt(role));
				g.text(Ui.font(), keys.get(i), cx, top, Ui.WHITE, true);
				g.text(Ui.font(), values.get(i), cx + colW - Ui.width(values.get(i)), top, Ui.WHITE, true);
			}
		}
	}

	/** Empty space. */
	public static El spacer(int h) {
		return new El() {
			public int prefWidth(int max) {
				return 0;
			}

			public int height(int w) {
				return h;
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			}
		};
	}
}
