package io.github.donttemptfate.fatesflipper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.donttemptfate.fatescommon.text.Coins;
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
					Chat.info(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.reconnecting"));
					return 1;
				}))
				.then(ClientCommands.literal("on").executes(c -> setBool(s, (cfg, v) -> cfg.alerts = v, true, "Flip alerts on.")))
				.then(ClientCommands.literal("off").executes(c -> setBool(s, (cfg, v) -> cfg.alerts = v, false, "Flip alerts off (keys and tooltips still work).")))
				.then(toggle("sound", s, (cfg, v) -> cfg.sound = v, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.t.sound")))
				.then(toggle("tooltips", s, (cfg, v) -> cfg.tooltips = v, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.t.tooltips")))
				.then(toggle("overlay", s, (cfg, v) -> cfg.auctionOverlay = v, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.t.overlay")))
				.then(toggle("hud", s, (cfg, v) -> cfg.hudStatus = v, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.t.hud")))
				.then(toggle("closekey", s, (cfg, v) -> cfg.pressAgainToClose = v, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.t.closekey")))
				.then(toggle("look", s, (cfg, v) -> cfg.fatesLook = v, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.t.look")))
				.then(toggle("fatestooltips", s, (cfg, v) -> cfg.fatesTooltips = v, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.t.fatestooltips")))
				.then(toggle("skyblock", s, (cfg, v) -> cfg.onlyInSkyBlock = v, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.t.skyblock")))
				.then(toggle("dungeons", s, (cfg, v) -> cfg.alertsInDungeons = v, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.t.dungeons")))
				.then(ClientCommands.literal("font")
						.then(ClientCommands.literal("chakra").executes(c -> setBool(s, (cfg, v) -> cfg.chakraFont = v, true, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.font.chakra"))))
						.then(ClientCommands.literal("vanilla").executes(c -> setBool(s, (cfg, v) -> cfg.chakraFont = v, false, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.font.vanilla")))))
				.then(themeCommand(s))
				// opt-in account check; every step is written out in Verify.java (Fate, 2026-10-18)
				.then(ClientCommands.literal("verify").executes(c -> {
					Verify.explain();
					return 1;
				}).then(ClientCommands.literal("CONFIRM").executes(c -> {
					Verify.run(s);
					return 1;
				})))
				.then(ClientCommands.literal("cursorassist")
						// turning it on needs a second, typed confirmation (Fate, 2026-10-18)
						.then(ClientCommands.literal("on").executes(c -> {
							if (s.config().cursorAssist) {
								Chat.info(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.ca.already"));
								return 1;
							}
							Chat.warn(ConfigScreen.cursorAssistRisk());
							Chat.warn(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.ca.confirm"));
							return 1;
						}).then(ClientCommands.literal("CONFIRM").executes(c -> {
							setBool(s, (cfg, v) -> cfg.cursorAssist = v, true, io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.ca.on"));
							return 1;
						})))
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
							s.openFromChat(StringArgumentType.getString(c, "id"));
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

	/** /ff theme: lists the themes; /ff theme <id> picks one (shared with Fates OFA). */
	private static LiteralArgumentBuilder<FabricClientCommandSource> themeCommand(FlipService s) {
		LiteralArgumentBuilder<FabricClientCommandSource> b = ClientCommands.literal("theme").executes(c -> {
			Chat.info(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.theme.list", s.config().theme, String.join(", ", io.github.donttemptfate.fatescommon.ui.Theme.ids())));
			return 1;
		});
		for (io.github.donttemptfate.fatescommon.ui.Theme t : io.github.donttemptfate.fatescommon.ui.Theme.ALL) {
			b.then(ClientCommands.literal(t.id()).executes(c -> {
				ModConfig next = s.config().copy();
				next.theme = t.id();
				s.applyConfig(next);
				Chat.ok(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.theme.set", t.name()));
				return 1;
			}));
		}
		return b;
	}

	private static int help() {
		Chat.send(Chat.prefix().append(Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.title")).withStyle(ChatFormatting.WHITE)));
		String[][] rows = {
				{"/ff config", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.01")},
				{"/ff status", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.02")},
				{"/ff best | safest", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.03")},
				{"/ff recent", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.04")},
				{"/ff profiles", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.05")},
				{"/ff on | off", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.06")},
				{"/ff sound|tooltips|overlay|hud on|off", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.07")},
				{"/ff skyblock on|off", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.08")},
				{"/ff dungeons on|off", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.09")},
				{"/ff closekey on|off", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.10")},
				{"/ff look|fatestooltips on|off", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.11")},
				{"/ff font chakra|vanilla", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.12")},
				{"/ff theme [name]", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.theme")},
				{"/ff cursorassist on|off", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.13")},
				{"/ff reconnect", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.14")},
				{"/ff verify", io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.help.verify")},
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
		Chat.send(Chat.prefix().append(Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.title")).withStyle(ChatFormatting.WHITE)));
		row(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.server"), chk.ok() ? chk.baseUrl() : io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.notset", chk.problem()));
		row(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.token"), ModConfig.maskToken(c.token));
		row(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.hypixel"), s.onHypixel() ? io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.yes") : io.github.donttemptfate.fatesflipper.core.Tr.tr(c.onlyOnHypixel ? "fatesflipper.cmd.status.hypixel.no_only" : "fatesflipper.cmd.no"));
		String st = switch (s.streamState()) {
			case CONNECTED -> io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.state.connected");
			case CONNECTING -> io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.state.connecting");
			case RETRYING -> io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.state.retrying", s.streamDetail());
			case STOPPED -> io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.state.stopped", s.streamDetail());
			case WAITING -> io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.state.waiting", s.streamDetail());
			case OFF -> io.github.donttemptfate.fatesflipper.core.Tr.tr(c.alerts ? "fatesflipper.cmd.state.off_hypixel" : "fatesflipper.cmd.state.off_alerts");
		};
		row(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.alerts"), st);
		row(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.profile"), s.profileName() == null ? io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.profile.none") : s.profileName());
		row(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.version"), s.modVersion() + (s.updateRequired() != null ? " " + io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.version.outdated") : ""));
		row(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.toggles"), "sound " + onOff(c.sound) + ", tooltips " + onOff(c.tooltips) + ", overlay " + onOff(c.auctionOverlay)
				+ ", hud " + onOff(c.hudStatus) + ", close key " + onOff(c.pressAgainToClose) + ", cursor assist " + onOff(c.cursorAssist)
				+ ", look " + onOff(c.fatesLook) + ", Fates tooltips " + onOff(c.fatesTooltips) + ", font " + (c.chakraFont ? "Chakra" : "Minecraft") + ", theme " + c.theme);
		s.api().ping().thenAccept(r -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
			if (r.ok()) {
				JsonObject j = r.json();
				row(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.viewer"), "v" + io.github.donttemptfate.fatescommon.text.LegacyText.strip(String.valueOf(Json.str(j, "version"))) + ", collector " + (Json.bool(j, "collector_ok", false) ? "ok" : "not updating")
						+ (Json.hasBool(j, "live_feed") ? ", live feed " + (Json.bool(j, "live_feed", false) ? "on" : "off") : ""));
			} else {
				row(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.status.viewer"), r.message());
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
		Chat.ok(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.server.set", chk.baseUrl()));
		return 1;
	}

	private static int token(FlipService s, CommandContext<FabricClientCommandSource> c) {
		String t = StringArgumentType.getString(c, "token").trim();
		if (!ServerAddress.looksLikeToken(t)) Chat.warn(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.token.odd"));
		ModConfig next = s.config().copy();
		next.token = t;
		s.applyConfig(next);
		Chat.ok(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.token.saved", ModConfig.maskToken(t)));
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
				Chat.info(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.profiles.none"));
				return;
			}
			Chat.send(Chat.prefix().append(Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.profiles.title")).withStyle(ChatFormatting.WHITE)));
			for (JsonElement e : list) {
				if (!e.isJsonObject()) continue;
				JsonObject p = e.getAsJsonObject();
				String name = Json.str(p, "name");
				if (name == null) continue;
				boolean isActive = active != null && active.equals(p.get("id"));
				long pid = (long) Json.num(p, "id", -1);
				MutableComponent line = Component.literal((isActive ? "  ▶ " : "    ") + io.github.donttemptfate.fatescommon.text.LegacyText.strip(name))
						.withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
				if (pid >= 0) {
					String label = io.github.donttemptfate.fatescommon.text.LegacyText.strip(name);
					line.withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/ff profile " + pid))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.profiles.switch_to", label)))));
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
				Chat.ok(n.equalsIgnoreCase("off") ? io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.profile.off") : io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.profile.switched", pn != null ? pn : n));
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
			Chat.info(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.recent.none"));
			return 1;
		}
		Chat.send(Chat.prefix().append(Component.literal(io.github.donttemptfate.fatesflipper.core.Tr.tr("fatesflipper.cmd.recent.title")).withStyle(ChatFormatting.WHITE)));
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
