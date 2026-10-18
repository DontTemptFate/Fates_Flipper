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
	public static final String CURSOR_ASSIST_RISK =
			"Cursor assist moves the cursor onto Buy, then Confirm, once per auction you open from a flip. "
					+ "It never clicks. Hypixel doesn't certify mods: use at your own risk.";

	/** Layout: everything fits in 220 scaled pixels (480p at GUI scale 2), centred when taller. */
	private static final int W = 330;
	private static final int H = 220;

	private final Screen parent;
	private final FlipService service;
	private final ModConfig draft;
	private EditBox server;
	private EditBox token;
	private String message = "";
	private boolean tokenShown;
	private int messageColor = 0xFFAAAAAA;

	public ConfigScreen(Screen parent, FlipService service) {
		super(Component.literal("Fates Flipper settings"));
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

		server = new EditBox(this.font, x, t + 34, W, 16, Component.literal("Server address"));
		server.setMaxLength(200);
		server.setValue(draft.serverUrl);
		server.setHint(Component.literal("https://your-viewer-address"));
		addRenderableWidget(server);

		// hidden until "Show" (audit F-S1: streams and screenshots never show the token by accident)
		token = new EditBox(this.font, x, t + 64, W - 56, 16, Component.literal("Mod token"));
		token.setMaxLength(300);
		token.setValue(draft.token);
		token.setHint(Component.literal("fv_… (web page → account menu → Mod tokens)"));
		token.visible = draft.token.isBlank() || tokenShown;
		addRenderableWidget(token);
		addRenderableWidget(Button.builder(Component.literal(token.visible ? "Hide" : "Show"), b -> {
			tokenShown = !token.visible;
			token.visible = tokenShown;
			b.setMessage(Component.literal(token.visible ? "Hide" : "Show"));
		}).bounds(x + W - 50, t + 62, 50, 20).build());

		int col = (W - 12) / 3;
		int y = t + 96;
		int[] cx = {x, x + col + 6, x + 2 * (col + 6)};
		toggle(cx[0], y, col, "Flip alerts", c -> c.alerts, c -> c.alerts = !c.alerts);
		toggle(cx[1], y, col, "Alert sound", c -> c.sound, c -> c.sound = !c.sound);
		toggle(cx[2], y, col, "Hypixel only", c -> c.onlyOnHypixel, c -> c.onlyOnHypixel = !c.onlyOnHypixel);
		y += 20;
		toggle(cx[0], y, col, "Price tooltips", c -> c.tooltips, c -> c.tooltips = !c.tooltips);
		toggle(cx[1], y, col, "Auction overlay", c -> c.auctionOverlay, c -> c.auctionOverlay = !c.auctionOverlay);
		toggle(cx[2], y, col, "Status display", c -> c.hudStatus, c -> c.hudStatus = !c.hudStatus);
		y += 20;
		toggle(cx[0], y, col, "Fates look", c -> c.fatesLook, c -> c.fatesLook = !c.fatesLook);
		toggle(cx[1], y, col, "Fates tooltips", c -> c.fatesTooltips, c -> c.fatesTooltips = !c.fatesTooltips);
		toggle(cx[2], y, col, "Chakra font", c -> c.chakraFont, c -> c.chakraFont = !c.chakraFont);
		y += 20;
		toggle(cx[0], y, col, "Key again closes", c -> c.pressAgainToClose, c -> c.pressAgainToClose = !c.pressAgainToClose);
		// turning it on needs /ff cursorassist on + CONFIRM (Fate, 2026-10-18); off works here
		toggle(cx[1], y, col, "Cursor assist", c -> c.cursorAssist, c -> {
			if (c.cursorAssist) c.cursorAssist = false;
			else setMessage("To turn cursor assist on, type /ff cursorassist on (it asks you to confirm).", 0xFFFFAA33);
		});
		toggle(cx[2], y, col, "SkyBlock only", c -> c.onlyInSkyBlock, c -> c.onlyInSkyBlock = !c.onlyInSkyBlock);

		int third = (W - 12) / 3;
		int by = t + H - 20;
		addRenderableWidget(Button.builder(Component.literal("Test connection"), b -> test()).bounds(x, by, third, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x + third + 6, by, third, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 2 * (third + 6), by, third, 20).build());
	}

	private void toggle(int x, int y, int w, String label, Function<ModConfig, Boolean> get, Consumer<ModConfig> flip) {
		Button b = Button.builder(label(label, get.apply(draft)), btn -> {
			flip.accept(draft);
			btn.setMessage(label(label, get.apply(draft)));
		}).bounds(x, y, w, 18).build();
		addRenderableWidget(b);
	}

	private static Component label(String label, boolean on) {
		return io.github.donttemptfate.fatescommon.ui.Ui.t(label + ": ", io.github.donttemptfate.fatescommon.ui.Ui.Role.BODY, 0xFFFFFF)
				.append(io.github.donttemptfate.fatescommon.ui.Ui.t(on ? "ON" : "OFF", io.github.donttemptfate.fatescommon.ui.Ui.Role.BODY,
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
			setMessage("Fill in the server address and the mod token first.", 0xFFFFAA33);
			return;
		}
		setMessage("Testing…", 0xFFAAAAAA);
		ViewerApi probe = new ViewerApi(() -> next, "test", service.api().http());
		probe.ping().thenAccept(r -> this.minecraft.execute(() -> {
			if (r.ok()) {
				String profile = Json.str(Json.obj(r.json(), "profile"), "name");
				setMessage("Works: viewer v" + Json.str(r.json(), "version") + (profile != null ? ", profile " + profile : "") + ". Press Save.", 0xFF55FF77);
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
				java.util.List.of(io.github.donttemptfate.fatescommon.ui.Card.seg("FATES FLIPPER", io.github.donttemptfate.fatescommon.ui.Ui.Role.LABEL, io.github.donttemptfate.fatescommon.ui.Ui.PINK)),
				java.util.List.of(io.github.donttemptfate.fatescommon.ui.Card.seg("SETTINGS", io.github.donttemptfate.fatescommon.ui.Ui.Role.LABEL, io.github.donttemptfate.fatescommon.ui.Ui.GRAY)))
				.draw(g, x, t + 2, W);
		rule(g, "SERVER ADDRESS", x, t + 22);
		rule(g, "MOD TOKEN · NEVER SHARE IT", x, t + 52);
		if (!token.visible) {
			new io.github.donttemptfate.fatescommon.ui.Card.Text(
					io.github.donttemptfate.fatescommon.ui.Ui.t(ModConfig.maskToken(token.getValue()), io.github.donttemptfate.fatescommon.ui.Ui.Role.BODY, 0xFFAAAAAA),
					io.github.donttemptfate.fatescommon.ui.Ui.Role.BODY, 0, 1).draw(g, x + 4, t + 68, W - 60);
		}
		rule(g, "DISPLAY & ALERTS", x, t + 84);
		int ty = t + 177;
		String text = message.isEmpty() ? CURSOR_ASSIST_RISK : message;
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
