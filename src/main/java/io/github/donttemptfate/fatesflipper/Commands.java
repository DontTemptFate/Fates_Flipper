package io.github.donttemptfate.fatesflipper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.donttemptfate.fatesflipper.core.Coins;
import io.github.donttemptfate.fatesflipper.core.FlipEvent;
import io.github.donttemptfate.fatesflipper.core.Json;
import io.github.donttemptfate.fatesflipper.core.ModConfig;
import io.github.donttemptfate.fatesflipper.core.ServerAddress;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** The /ff command (client side only; nothing here is sent to Hypixel except /ff open). */
public final class Commands {
	private Commands() {}

	public static void register(CommandDispatcher<FabricClientCommandSource> d, FlipService s) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("ff")
				.executes(c -> help())
				.then(ClientCommands.literal("help").executes(c -> help()))
				.then(ClientCommands.literal("status").executes(c -> status(s)))
				.then(ClientCommands.literal("config").executes(c -> {
					FatesFlipperClient.openScreenNextTick(parent -> new ConfigScreen(parent, s));
					return 1;
				}))
				.then(ClientCommands.literal("reconnect").executes(c -> {
					s.reconnect();
					Chat.info("Reconnecting…");
					return 1;
				}))
				.then(ClientCommands.literal("on").executes(c -> setBool(s, (cfg, v) -> cfg.alerts = v, true, "Flip alerts on.")))
				.then(ClientCommands.literal("off").executes(c -> setBool(s, (cfg, v) -> cfg.alerts = v, false, "Flip alerts off (keys and tooltips still work).")))
				.then(toggle("sound", s, (cfg, v) -> cfg.sound = v, "Alert sound"))
				.then(toggle("tooltips", s, (cfg, v) -> cfg.tooltips = v, "Price tooltips"))
				.then(toggle("overlay", s, (cfg, v) -> cfg.auctionOverlay = v, "Auction window overlay"))
				.then(toggle("hud", s, (cfg, v) -> cfg.hudStatus = v, "Status display"))
				.then(toggle("closekey", s, (cfg, v) -> cfg.pressAgainToClose = v, "Press the open key again to close a flip"))
				.then(toggle("look", s, (cfg, v) -> cfg.fatesLook = v, "Fates look"))
				.then(toggle("fatestooltips", s, (cfg, v) -> cfg.fatesTooltips = v, "Fates item tooltips"))
				.then(ClientCommands.literal("font")
						.then(ClientCommands.literal("chakra").executes(c -> setBool(s, (cfg, v) -> cfg.chakraFont = v, true, "Font: Chakra Petch.")))
						.then(ClientCommands.literal("vanilla").executes(c -> setBool(s, (cfg, v) -> cfg.chakraFont = v, false, "Font: Minecraft."))))
				.then(ClientCommands.literal("cursorassist")
						.then(ClientCommands.literal("on").executes(c -> {
							setBool(s, (cfg, v) -> cfg.cursorAssist = v, true, "Cursor assist on.");
							Chat.warn(ConfigScreen.CURSOR_ASSIST_RISK);
							return 1;
						}))
						.then(ClientCommands.literal("off").executes(c -> setBool(s, (cfg, v) -> cfg.cursorAssist = v, false, "Cursor assist off."))))
				.then(ClientCommands.literal("server")
						.then(ClientCommands.argument("address", StringArgumentType.greedyString()).executes(c -> server(s, c))))
				.then(ClientCommands.literal("token")
						.then(ClientCommands.argument("token", StringArgumentType.greedyString()).executes(c -> token(s, c))))
				.then(ClientCommands.literal("profiles").executes(c -> profiles(s)))
				.then(ClientCommands.literal("profile")
						.then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(c -> profile(s, StringArgumentType.getString(c, "name")))))
				.then(ClientCommands.literal("recent").executes(c -> recent(s)))
				.then(ClientCommands.literal("open")
						.then(ClientCommands.argument("id", StringArgumentType.word()).executes(c -> {
							s.openAuction(StringArgumentType.getString(c, "id"), null);
							return 1;
						})))
				.then(ClientCommands.literal("best").executes(c -> {
					s.openNext("best");
					return 1;
				}))
				.then(ClientCommands.literal("safest").executes(c -> {
					s.openNext("safest");
					return 1;
				}));
		d.register(root);
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, FlipService s,
			BiConsumer<ModConfig, Boolean> setter, String label) {
		return ClientCommands.literal(name)
				.then(ClientCommands.literal("on").executes(c -> setBool(s, setter, true, label + " on.")))
				.then(ClientCommands.literal("off").executes(c -> setBool(s, setter, false, label + " off.")));
	}

	private static int setBool(FlipService s, BiConsumer<ModConfig, Boolean> setter, boolean value, String message) {
		ModConfig next = s.config().copy();
		setter.accept(next, value);
		s.applyConfig(next);
		Chat.ok(message);
		return 1;
	}

	private static int help() {
		Chat.send(Chat.prefix().append(Component.literal("Fates Flipper commands").withStyle(ChatFormatting.WHITE)));
		String[][] rows = {
				{"/ff config", "settings screen (server address, token, toggles)"},
				{"/ff status", "connection, profile and settings"},
				{"/ff best | safest", "open the best / safest flip (or bind keys in Controls)"},
				{"/ff recent", "the last flips, clickable"},
				{"/ff profiles", "list your flip profiles; /ff profile <name> to switch, /ff profile off"},
				{"/ff on | off", "flip alerts on / off"},
				{"/ff sound|tooltips|overlay|hud on|off", "toggles"},
				{"/ff closekey on|off", "press the open key again to close the flip (on)"},
				{"/ff look|fatestooltips on|off", "the Fates look; Fates item tooltips"},
				{"/ff font chakra|vanilla", "Chakra Petch or Minecraft's font"},
				{"/ff cursorassist on|off", "opt-in, off by default (read the warning)"},
				{"/ff reconnect", "connect again now"},
		};
		for (String[] r : rows) {
			Chat.send(Component.literal("  " + r[0]).withStyle(ChatFormatting.YELLOW)
					.append(Component.literal("  " + r[1]).withStyle(ChatFormatting.GRAY)));
		}
		return 1;
	}

	private static int status(FlipService s) {
		ModConfig c = s.config();
		ServerAddress.Check chk = ServerAddress.check(c.serverUrl);
		Chat.send(Chat.prefix().append(Component.literal("Status").withStyle(ChatFormatting.WHITE)));
		row("Server", chk.ok() ? chk.baseUrl() : "not set (" + chk.problem() + ")");
		row("Mod token", ModConfig.maskToken(c.token));
		row("On Hypixel", s.onHypixel() ? "yes" : "no" + (c.onlyOnHypixel ? " (the mod only connects on Hypixel)" : ""));
		String st = switch (s.streamState()) {
			case CONNECTED -> "connected";
			case CONNECTING -> "connecting…";
			case RETRYING -> "reconnecting: " + s.streamDetail();
			case STOPPED -> "stopped: " + s.streamDetail();
			case WAITING -> "waiting: " + s.streamDetail();
			case OFF -> c.alerts ? "off (not on Hypixel)" : "off (alerts are off)";
		};
		row("Flip alerts", st);
		row("Flip profile", s.profileName() == null ? "(none / unknown)" : s.profileName());
		row("Toggles", "sound " + onOff(c.sound) + ", tooltips " + onOff(c.tooltips) + ", overlay " + onOff(c.auctionOverlay)
				+ ", hud " + onOff(c.hudStatus) + ", close key " + onOff(c.pressAgainToClose) + ", cursor assist " + onOff(c.cursorAssist)
				+ ", look " + onOff(c.fatesLook) + ", Fates tooltips " + onOff(c.fatesTooltips) + ", font " + (c.chakraFont ? "Chakra" : "Minecraft"));
		s.api().ping().thenAccept(r -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
			if (r.ok()) {
				JsonObject j = r.json();
				row("Viewer", "v" + io.github.donttemptfate.fatesflipper.core.LegacyText.strip(String.valueOf(Json.str(j, "version"))) + ", collector " + (Json.bool(j, "collector_ok", false) ? "ok" : "not updating")
						+ (Json.hasBool(j, "live_feed") ? ", live feed " + (Json.bool(j, "live_feed", false) ? "on" : "off") : ""));
			} else {
				row("Viewer", r.message());
			}
		}));
		return 1;
	}

	private static String onOff(boolean b) {
		return b ? "on" : "off";
	}

	private static void row(String label, String value) {
		Chat.send(Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value == null ? "" : value).withStyle(ChatFormatting.WHITE)));
	}

	private static int server(FlipService s, CommandContext<FabricClientCommandSource> c) {
		String addr = StringArgumentType.getString(c, "address");
		ServerAddress.Check chk = ServerAddress.check(addr);
		if (!chk.ok()) {
			Chat.error(chk.problem());
			return 0;
		}
		ModConfig next = s.config().copy();
		next.serverUrl = chk.baseUrl();
		s.applyConfig(next);
		Chat.ok("Server set to " + chk.baseUrl());
		return 1;
	}

	private static int token(FlipService s, CommandContext<FabricClientCommandSource> c) {
		String t = StringArgumentType.getString(c, "token").trim();
		if (!ServerAddress.looksLikeToken(t)) Chat.warn("That doesn't look like a mod token (they start with fv_). Saved anyway.");
		ModConfig next = s.config().copy();
		next.token = t;
		s.applyConfig(next);
		Chat.ok("Mod token saved: " + ModConfig.maskToken(t) + ". Tip: /ff config keeps it out of your chat history.");
		return 1;
	}

	private static int profiles(FlipService s) {
		s.api().profiles().thenAccept(r -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
			if (!r.ok()) {
				Chat.error(r.message());
				return;
			}
			JsonElement active = r.json().get("active");
			JsonArray list = Json.arr(r.json(), "profiles");
			if (list == null || list.isEmpty()) {
				Chat.info("No flip profiles yet: make one in the web page.");
				return;
			}
			Chat.send(Chat.prefix().append(Component.literal("Flip profiles (click to switch)").withStyle(ChatFormatting.WHITE)));
			for (JsonElement e : list) {
				if (!e.isJsonObject()) continue;
				JsonObject p = e.getAsJsonObject();
				String name = Json.str(p, "name");
				if (name == null) continue;
				boolean isActive = active != null && active.equals(p.get("id"));
				long pid = (long) Json.num(p, "id", -1);
				MutableComponent line = Component.literal((isActive ? "  ▶ " : "    ") + io.github.donttemptfate.fatesflipper.core.LegacyText.strip(name))
						.withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
				if (pid >= 0) {
					String label = io.github.donttemptfate.fatesflipper.core.LegacyText.strip(name);
					line.withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/ff profile " + pid))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("Switch to " + label))));
				}
				Chat.send(line);
			}
		}));
		return 1;
	}

	private static int profile(FlipService s, String name) {
		String n = name.trim();
		s.api().setProfile(n.equalsIgnoreCase("off") ? "off" : n).thenAccept(r -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
			if (r.ok()) {
				JsonObject p = Json.obj(r.json(), "profile");
				String pn = p != null ? Json.str(p, "name") : Json.str(r.json(), "name");
				Chat.ok(n.equalsIgnoreCase("off") ? "Flip profile off: no flips will be sent." : "Switched to " + (pn != null ? pn : n) + ".");
				s.refreshChatSettings();
			} else {
				Chat.error(r.message());
			}
		}));
		return 1;
	}

	private static int recent(FlipService s) {
		List<FlipEvent> list = s.recentFlips();
		if (list.isEmpty()) {
			Chat.info("No flips yet this session.");
			return 1;
		}
		Chat.send(Chat.prefix().append(Component.literal("Recent flips (click to open)").withStyle(ChatFormatting.WHITE)));
		Function<FlipEvent, String> profit = f -> Double.isNaN(f.safeProfit()) ? "" : " +" + Coins.format(f.safeProfit());
		for (int i = 0; i < Math.min(8, list.size()); i++) {
			FlipEvent f = list.get(i);
			MutableComponent line = Component.literal("  ").append(Chat.fromLegacy(f.name()))
					.append(Component.literal(" " + Coins.format(f.price())).withStyle(ChatFormatting.YELLOW))
					.append(Component.literal(profit.apply(f)).withStyle(ChatFormatting.GREEN));
			line.withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/ff open " + f.auctionId()))
					.withHoverEvent(new HoverEvent.ShowText(Alerts.details(f))));
			Chat.send(line);
		}
		return 1;
	}
}
