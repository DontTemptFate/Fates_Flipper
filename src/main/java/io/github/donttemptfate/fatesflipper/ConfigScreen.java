package io.github.donttemptfate.fatesflipper;

import io.github.donttemptfate.fatesflipper.core.Json;
import io.github.donttemptfate.fatesflipper.core.ModConfig;
import io.github.donttemptfate.fatesflipper.core.ServerAddress;
import io.github.donttemptfate.fatesflipper.core.ViewerApi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;

/** Settings: server address, mod token and toggles. Opened with /ff config or its key. */
public final class ConfigScreen extends Screen {
	public static String cursorAssistRisk() {
		return io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.cursor_assist_risk");
	}

	/** Layout: everything fits in 220 scaled pixels (480p at GUI scale 2), centred when taller. */
	private static final int W = 330;
	private static final int H = 242;

	private final Screen parent;
	private final FlipService service;
	private final ModConfig draft;
	private EditBox server;
	private EditBox token;
	private String message = "";
	private boolean tokenShown;
	private int messageColor = 0xFFAAAAAA;

	public ConfigScreen(Screen parent, FlipService service) {
		super(Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.title")));
		this.parent = parent;
		this.service = service;
		this.draft = service.config().copy();
	}

	private int top() {
		return Math.max(0, (this.height - H) / 2);
	}

	@Override
	protected void init() {
		int x = (this.width - W) / 2;
		int t = top();

		server = new EditBox(this.font, x, t + 34, W, 16, Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.server")));
		server.setMaxLength(200);
		server.setValue(draft.serverUrl);
		server.setHint(Component.literal("https://your-viewer-address"));
		addRenderableWidget(server);

		// hidden until "Show" (audit F-S1: streams and screenshots never show the token by accident)
		token = new EditBox(this.font, x, t + 64, W - 56, 16, Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.token")));
		token.setMaxLength(300);
		token.setValue(draft.token);
		token.setHint(Component.literal("fv_… (web page → account menu → Mod tokens)"));
		token.visible = draft.token.isBlank() || tokenShown;
		addRenderableWidget(token);
		addRenderableWidget(Button.builder(Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr(token.visible ? "fatesflipper.settings.hide" : "fatesflipper.settings.show")), b -> {
			tokenShown = !token.visible;
			token.visible = tokenShown;
			b.setMessage(Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr(token.visible ? "fatesflipper.settings.hide" : "fatesflipper.settings.show")));
		}).bounds(x + W - 50, t + 62, 50, 20).build());

		int col = (W - 12) / 3;
		int y = t + 96;
		int[] cx = {x, x + col + 6, x + 2 * (col + 6)};
		toggle(cx[0], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.alerts"), c -> c.alerts, c -> c.alerts = !c.alerts);
		toggle(cx[1], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.sound"), c -> c.sound, c -> c.sound = !c.sound);
		toggle(cx[2], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.hypixel"), c -> c.onlyOnHypixel, c -> c.onlyOnHypixel = !c.onlyOnHypixel);
		y += 20;
		toggle(cx[0], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.tooltips"), c -> c.tooltips, c -> c.tooltips = !c.tooltips);
		toggle(cx[1], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.overlay"), c -> c.auctionOverlay, c -> c.auctionOverlay = !c.auctionOverlay);
		toggle(cx[2], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.hud"), c -> c.hudStatus, c -> c.hudStatus = !c.hudStatus);
		y += 20;
		toggle(cx[0], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.look"), c -> c.fatesLook, c -> c.fatesLook = !c.fatesLook);
		toggle(cx[1], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.fatestooltips"), c -> c.fatesTooltips, c -> c.fatesTooltips = !c.fatesTooltips);
		toggle(cx[2], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.dungeons"), c -> c.alertsInDungeons, c -> c.alertsInDungeons = !c.alertsInDungeons);
		y += 20;
		toggle(cx[0], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.closekey"), c -> c.pressAgainToClose, c -> c.pressAgainToClose = !c.pressAgainToClose);
		// turning it on needs /ff cursorassist on + CONFIRM (Fate, 2026-10-18); off works here
		toggle(cx[1], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.cursorassist"), c -> c.cursorAssist, c -> {
			if (c.cursorAssist) c.cursorAssist = false;
			else setMessage(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.ca.how"), 0xFFFFAA33);
		});
		toggle(cx[2], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.skyblock"), c -> c.onlyInSkyBlock, c -> c.onlyInSkyBlock = !c.onlyInSkyBlock);

		// shared with Fates OFA (one font and one theme for all Fates mods, saved by Fates Common)
		y += 20;
		addRenderableWidget(Button.builder(themeLabel(), btn -> {
			java.util.List<io.github.donttemptfate.fatescommon.ui.Theme> all = io.github.donttemptfate.fatescommon.ui.Theme.ALL;
			int i = all.indexOf(io.github.donttemptfate.fatescommon.ui.Theme.byId(draft.theme));
			draft.theme = all.get((i + 1) % all.size()).id();
			btn.setMessage(themeLabel());
		}).bounds(cx[0], y, 2 * col + 6, 18).build());
		toggle(cx[2], y, col, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.chakra"), c -> c.chakraFont, c -> c.chakraFont = !c.chakraFont);

		int third = (W - 12) / 3;
		int by = t + H - 20;
		addRenderableWidget(Button.builder(Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.test")), b -> test()).bounds(x, by, third, 20).build());
		addRenderableWidget(Button.builder(Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.save")), b -> save()).bounds(x + third + 6, by, third, 20).build());
		addRenderableWidget(Button.builder(Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.cancel")), b -> onClose()).bounds(x + 2 * (third + 6), by, third, 20).build());
	}

	private void toggle(int x, int y, int w, String label, Function<ModConfig, Boolean> get, Consumer<ModConfig> flip) {
		Button b = Button.builder(label(label, get.apply(draft)), btn -> {
			flip.accept(draft);
			btn.setMessage(label(label, get.apply(draft)));
		}).bounds(x, y, w, 18).build();
		addRenderableWidget(b);
	}

	private Component themeLabel() {
		io.github.donttemptfate.fatescommon.ui.Theme th = io.github.donttemptfate.fatescommon.ui.Theme.byId(draft.theme);
		return io.github.donttemptfate.fatescommon.ui.Ui.t(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.t.theme") + ": ", io.github.donttemptfate.fatescommon.ui.Ui.Role.BODY, 0xFFFFFF)
				.append(io.github.donttemptfate.fatescommon.ui.Ui.t(th.name(), io.github.donttemptfate.fatescommon.ui.Ui.Role.BODY, th.accent() & 0xFFFFFF))
				.append(io.github.donttemptfate.fatescommon.ui.Ui.t("  " + io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.shared"), io.github.donttemptfate.fatescommon.ui.Ui.Role.SMALL, 0xAAAAAA));
	}

	private static Component label(String label, boolean on) {
		return io.github.donttemptfate.fatescommon.ui.Ui.t(label + ": ", io.github.donttemptfate.fatescommon.ui.Ui.Role.BODY, 0xFFFFFF)
				.append(io.github.donttemptfate.fatescommon.ui.Ui.t(io.github.donttemptfate.fatesflipper.core.Tr.tr(on ? "fatesflipper.toggle.on" : "fatesflipper.toggle.off"), io.github.donttemptfate.fatescommon.ui.Ui.Role.BODY,
						on ? 0x55FF55 : 0xFF5555));
	}

	private ModConfig collect() {
		ModConfig next = draft.copy();
		String addr = server.getValue().trim();
		if (addr.isEmpty()) {
			next.serverUrl = "";
		} else {
			ServerAddress.Check chk = ServerAddress.check(addr);
			if (!chk.ok()) {
				setMessage(chk.problem(), 0xFFFF5555);
				return null;
			}
			next.serverUrl = chk.baseUrl();
		}
		next.token = token.getValue().trim();
		return next;
	}

	private void save() {
		ModConfig next = collect();
		if (next == null) return;
		service.applyConfig(next);
		onClose();
	}

	private void test() {
		ModConfig next = collect();
		if (next == null) return;
		if (!next.isReady()) {
			setMessage(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.fill"), 0xFFFFAA33);
			return;
		}
		setMessage(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.testing"), 0xFFAAAAAA);
		ViewerApi probe = new ViewerApi(() -> next, "test", service.api().http());
		probe.ping().thenAccept(r -> this.minecraft.execute(() -> {
			if (r.ok()) {
				String profile = Json.str(Json.obj(r.json(), "profile"), "name");
				setMessage(profile != null ? io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.works_profile", Json.str(r.json(), "version"), profile) : io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.works", Json.str(r.json(), "version")), 0xFF55FF77);
			} else {
				setMessage(r.message(), 0xFFFF5555);
			}
		}));
	}

	private void setMessage(String text, int color) {
		this.message = text == null ? "" : text;
		this.messageColor = color;
	}

	/** The Fates frame behind the form (drawn after the blurred background, before the widgets). */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		int x = (this.width - W) / 2;
		int t = top();
		io.github.donttemptfate.fatescommon.ui.Ui.frame(g, x - 12, Math.max(0, t - 6), W + 24, Math.min(this.height, H + 12), 0.94f);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		int x = (this.width - W) / 2;
		int t = top();
		new io.github.donttemptfate.fatescommon.ui.Card.Line(
				java.util.List.of(io.github.donttemptfate.fatescommon.ui.Card.seg(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.head"), io.github.donttemptfate.fatescommon.ui.Ui.Role.LABEL, io.github.donttemptfate.fatescommon.ui.Ui.accent())),
				java.util.List.of(io.github.donttemptfate.fatescommon.ui.Card.seg(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.head.settings"), io.github.donttemptfate.fatescommon.ui.Ui.Role.LABEL, io.github.donttemptfate.fatescommon.ui.Ui.GRAY)))
				.draw(g, x, t + 2, W);
		rule(g, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.rule.server"), x, t + 22);
		rule(g, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.rule.token"), x, t + 52);
		if (!token.visible) {
			new io.github.donttemptfate.fatescommon.ui.Card.Text(
					io.github.donttemptfate.fatescommon.ui.Ui.t(ModConfig.maskToken(token.getValue()), io.github.donttemptfate.fatescommon.ui.Ui.Role.BODY, 0xFFAAAAAA),
					io.github.donttemptfate.fatescommon.ui.Ui.Role.BODY, 0, 1).draw(g, x + 4, t + 68, W - 60);
		}
		rule(g, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.settings.rule.display"), x, t + 84);
		int ty = t + 199;
		String text = message.isEmpty() ? cursorAssistRisk() : message;
		int color = message.isEmpty() ? 0xFFFFAA00 : messageColor;
		new io.github.donttemptfate.fatescommon.ui.Card.Text(
				io.github.donttemptfate.fatescommon.ui.Ui.t(text, io.github.donttemptfate.fatescommon.ui.Ui.Role.SMALL, color),
				io.github.donttemptfate.fatescommon.ui.Ui.Role.SMALL, 0, 2).draw(g, x, ty, W);
	}

	private static void rule(GuiGraphicsExtractor g, String label, int x, int y) {
		new io.github.donttemptfate.fatescommon.ui.Card.Rule(label, io.github.donttemptfate.fatescommon.ui.Ui.GRAY, null).draw(g, x, y, W);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
