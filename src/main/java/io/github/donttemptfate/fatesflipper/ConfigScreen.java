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

	/** Layout: everything fits in 232 scaled pixels (480p at GUI scale 2), centred when taller. */
	private static final int W = 310;
	private static final int H = 232;

	private final Screen parent;
	private final FlipService service;
	private final ModConfig draft;
	private EditBox server;
	private EditBox token;
	private String message = "";
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

		server = new EditBox(this.font, x, t + 28, W, 16, Component.literal("Server address"));
		server.setMaxLength(200);
		server.setValue(draft.serverUrl);
		server.setHint(Component.literal("https://your-viewer-address"));
		addRenderableWidget(server);

		token = new EditBox(this.font, x, t + 58, W, 16, Component.literal("Mod token"));
		token.setMaxLength(300);
		token.setValue(draft.token);
		token.setHint(Component.literal("fv_… (web page → account menu → Mod tokens)"));
		addRenderableWidget(token);

		int half = (W - 6) / 2;
		int y = t + 80;
		toggle(x, y, half, "Flip alerts", c -> c.alerts, (c) -> c.alerts = !c.alerts);
		toggle(x + half + 6, y, half, "Alert sound", c -> c.sound, (c) -> c.sound = !c.sound);
		y += 22;
		toggle(x, y, half, "Price tooltips", c -> c.tooltips, (c) -> c.tooltips = !c.tooltips);
		toggle(x + half + 6, y, half, "Auction overlay", c -> c.auctionOverlay, (c) -> c.auctionOverlay = !c.auctionOverlay);
		y += 22;
		toggle(x, y, half, "Status display", c -> c.hudStatus, (c) -> c.hudStatus = !c.hudStatus);
		toggle(x + half + 6, y, half, "Only on Hypixel", c -> c.onlyOnHypixel, (c) -> c.onlyOnHypixel = !c.onlyOnHypixel);
		y += 22;
		toggle(x, y, W, "Cursor assist (opt-in, see below)", c -> c.cursorAssist, (c) -> c.cursorAssist = !c.cursorAssist);

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
		}).bounds(x, y, w, 20).build();
		addRenderableWidget(b);
	}

	private static Component label(String label, boolean on) {
		return Component.literal(label + ": " + (on ? "§aON" : "§cOFF"));
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

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		int x = (this.width - W) / 2;
		int t = top();
		g.centeredText(this.font, this.title, this.width / 2, t + 4, 0xFFFFC23D);
		g.text(this.font, "Server address", x, t + 18, 0xFFCCCCCC, true);
		g.text(this.font, "Mod token (kept only in config/fatesflipper.json)", x, t + 48, 0xFFCCCCCC, true);
		int ty = t + 170;
		int lines = 0;
		if (message.isEmpty()) {
			for (String part : wrap(CURSOR_ASSIST_RISK, W)) {
				if (lines++ >= 3) break;
				g.text(this.font, part, x, ty, 0xFFFFAA33, false);
				ty += this.font.lineHeight + 1;
			}
		} else {
			for (String part : wrap(message, W)) {
				if (lines++ >= 3) break;
				g.text(this.font, part, x, ty, messageColor, true);
				ty += this.font.lineHeight + 1;
			}
		}
	}

	private java.util.List<String> wrap(String text, int width) {
		java.util.List<String> out = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String test = line.length() == 0 ? word : line + " " + word;
			if (this.font.width(test) > width && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(test);
			}
		}
		if (line.length() > 0) out.add(line.toString());
		return out;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
