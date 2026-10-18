package io.github.donttemptfate.fatesflipper.test;

import io.github.donttemptfate.fatesflipper.AuctionOverlay;
import io.github.donttemptfate.fatesflipper.ConfigScreen;
import io.github.donttemptfate.fatesflipper.FatesFlipperClient;
import io.github.donttemptfate.fatesflipper.FlipService;
import io.github.donttemptfate.fatesflipper.Keys;
import io.github.donttemptfate.fatesflipper.PriceTooltips;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.donttemptfate.fatesflipper.core.FlipStream;
import io.github.donttemptfate.fatesflipper.core.ModConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Smoke test in a real game (CI): connects to a fake viewer, receives a flip, refuses a flip with
 * a wrong command, shows a price tooltip, opens the next flip, draws the auction overlay and the
 * settings screen. Screenshots go to build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class FatesFlipperGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		FakeViewer fake;
		try {
			fake = FakeViewer.start();
		} catch (Exception e) {
			throw new AssertionError("couldn't start the fake viewer", e);
		}
		FlipService service = FatesFlipperClient.service();
		check(service != null, "mod didn't initialise");
		// another mod adding a tooltip line: it must show in the Fates tooltip's MODS section (audit F-C1)
		net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, tctx, flag, lines) -> {
			var cd = stack.get(DataComponents.CUSTOM_DATA);
			if (cd != null && "FUNGI_CUTTER_2".equals(cd.copyTag().getStringOr("id", ""))) {
				lines.add(Component.literal("OtherMod value: ").withStyle(net.minecraft.ChatFormatting.GRAY)
						.append(Component.literal("12.3M").withStyle(net.minecraft.ChatFormatting.AQUA)));
			}
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			ctx.runOnClient(mc -> {
				ModConfig c = service.config().copy();
				c.serverUrl = fake.url();
				c.token = FakeViewer.TOKEN;
				c.onlyOnHypixel = false; // the test world isn't Hypixel
				c.cursorAssist = true;
				service.applyConfig(c);
			});

			// 1. connects and receives the flip; the wrong-command flip is refused
			ctx.waitFor(mc -> service.streamState() == FlipStream.State.CONNECTED, 600);
			ctx.waitFor(mc -> service.recentFlip(FakeViewer.FLIP_ID) != null, 600);
			ctx.waitTicks(30);
			check(service.recentFlip(FakeViewer.EVIL_ID) == null, "a flip with a non-/viewauction command was accepted");
			check(fake.unauthorized.get() == 0, "requests went out without the token");
			ctx.takeScreenshot("01_flip_alert");
			// chat settings came from /mod/profiles (viewer handoff 2026-10-18): no /api call needed
			check(fake.apiProfilesCalls.get() == 0, "read chat settings from /api/profiles though /mod/profiles had them");
			// the same flip again (a replay or re-send) never shows twice (audit F-R2)
			int shownBefore = ctx.computeOnClient(mc -> service.recentFlips().size());
			ctx.runOnClient(mc -> service.onFlip(com.google.gson.JsonParser.parseString(
					FakeViewer.flip(FakeViewer.FLIP_ID, "Heroic Hyperion", 1_150_000_000L, 42_000_000L, "/viewauction " + FakeViewer.FLIP_ID)).getAsJsonObject()));
			check(ctx.computeOnClient(mc -> service.recentFlips().size()) == shownBefore, "a repeated flip was shown twice");
			// after a drop the mod reconnects with the last event id, so the viewer can replay what it missed (audit F-R1)
			ctx.runOnClient(mc -> service.reconnect());
			ctx.waitFor(mc -> fake.lastEventIdHeader.get() != null, 600);
			check("42".equals(fake.lastEventIdHeader.get()), "reconnected with Last-Event-ID " + fake.lastEventIdHeader.get());
			ctx.waitFor(mc -> service.streamState() == FlipStream.State.CONNECTED, 600);
			ctx.waitTicks(30);
			check(ctx.computeOnClient(mc -> service.recentFlips().size()) == shownBefore, "a replayed flip was shown twice");

			// 1b. the /ff commands run without errors
			for (String cmd : List.of("ff help", "ff status", "ff profiles", "ff recent", "ff open not-an-id")) {
				ctx.runOnClient(mc -> run(mc, cmd));
				ctx.waitTicks(10);
			}
			ctx.waitTicks(20);
			ctx.takeScreenshot("01b_commands");

			// 1c. profile chat settings (countdown, seller button, lore on hover) and the DUPED?! tag
			check(ctx.computeOnClient(mc -> service.chatPrefs().sellerButton() && service.chatPrefs().countdown() && service.chatPrefs().loreOnHover()),
					"chat settings from /mod/profiles not applied");
			check("Test_Seller".equals(ctx.computeOnClient(mc -> service.recentFlip(FakeViewer.FLIP_ID).seller())), "seller name not read");
			ctx.runOnClient(mc -> {
				String duped = "ab" + FakeViewer.FLIP_ID.substring(2);
				String json = FakeViewer.flip(duped, "Duped Necron's Chestplate", 90_000_000L, 9_000_000L, "/viewauction " + duped)
						.replace("\"dupe\":null", "\"dupe\":\"duped\"").replace("\"chat\":\"", "\"chat_unused\":\"");
				service.onFlip(com.google.gson.JsonParser.parseString(json).getAsJsonObject());
			});
			ctx.waitTicks(10);
			check(ctx.computeOnClient(mc -> chatText(mc).contains("[SELLER]")), "no [SELLER] button on the alert");
			check(ctx.computeOnClient(mc -> chatText(mc).contains("DUPED?!")), "no DUPED?! on a duped alert");
			ctx.takeScreenshot("01c_chat_options");

			// 1c2. alert limits / mutes (viewer v0.31.0): the notice was printed; [mute 1h] and /ff mute
			check(ctx.computeOnClient(mc -> chatText(mc, 40).contains("Alert limit reached")), "the limit notice wasn't printed");
			check(ctx.computeOnClient(mc -> chatText(mc).contains("[mute 1h]")), "no [mute 1h] button on the alert");
			ctx.runOnClient(mc -> run(mc, "ff mute hour " + FakeViewer.FLIP_ID));
			ctx.waitFor(mc -> fake.lastMute.get() != null, 200);
			String muteBody = fake.lastMute.get();
			check(muteBody.startsWith("/mod/mute") && muteBody.contains("\"item_id\":\"HYPERION\"") && muteBody.contains("\"for\":\"hour\""), "mute request was " + muteBody);
			ctx.runOnClient(mc -> run(mc, "ff unmute all"));
			ctx.waitFor(mc -> fake.lastMute.get().startsWith("/mod/unmute"), 200);
			check(fake.lastMute.get().contains("\"all\":true"), "unmute request was " + fake.lastMute.get());

			// 1d. /ff verify: nothing runs until CONFIRM; with no real Minecraft login, Mojang refuses and
			// nothing is posted to the viewer
			ctx.runOnClient(mc -> run(mc, "ff verify"));
			ctx.waitTicks(20);
			check(fake.whoamiChallenges.get() == 0, "/ff verify without CONFIRM contacted the viewer");
			ctx.runOnClient(mc -> run(mc, "ff verify CONFIRM"));
			ctx.waitFor(mc -> fake.whoamiChallenges.get() == 1, 400);
			ctx.waitTicks(100);
			check(fake.whoamiPosts.get() == 0, "posted to /mod/whoami although Mojang's join check failed");
			ctx.takeScreenshot("01d_verify"); 

			// 2. price tooltip for an item with a SkyBlock uuid
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			CompoundTag tag = new CompoundTag();
			tag.putString("id", "HYPERION");
			tag.putString("uuid", FakeViewer.ITEM_UUID);
			sword.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			sword.set(DataComponents.CUSTOM_NAME, Component.literal("Heroic Hyperion"));
			ctx.runOnClient(mc -> tooltip(mc, sword)); // starts the lookup
			ctx.waitFor(mc -> String.join("\n", tooltip(mc, sword)).contains("value"), 400);
			List<String> lines = ctx.computeOnClient(mc -> tooltip(mc, sword));
			FatesFlipperClient.LOG.info("[gametest] tooltip: {}", lines);
			check(String.join("\n", lines).contains("1.25B"), "tooltip didn't show the price: " + lines);

			// 2b. never-traded item: sent whole in the API's item_bytes layout (collector §3.4)
			ItemStack never = skyblockItem("HYPERION", FakeViewer.NEVER_UUID, "Pristine Hyperion");
			ctx.runOnClient(mc -> tooltip(mc, never));
			ctx.waitFor(mc -> String.join("\n", tooltip(mc, never)).contains("77M"), 400);
			net.minecraft.nbt.CompoundTag sent = fake.lastItem.get();
			check(sent != null, "the whole item never reached the viewer");
			net.minecraft.nbt.CompoundTag item0 = sent.getListOrEmpty("i").getCompoundOrEmpty(0);
			net.minecraft.nbt.CompoundTag extra = item0.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes");
			net.minecraft.nbt.CompoundTag display = item0.getCompoundOrEmpty("tag").getCompoundOrEmpty("display");
			FatesFlipperClient.LOG.info("[gametest] item_bytes sent: {}", sent);
			check(FakeViewer.NEVER_UUID.equals(extra.getStringOr("uuid", "")), "ExtraAttributes.uuid changed: " + extra);
			check("HYPERION".equals(extra.getStringOr("id", "")), "ExtraAttributes.id missing: " + extra);
			check(display.getStringOr("Name", "").contains("§") && display.getStringOr("Name", "").contains("Pristine Hyperion"), "Name not in § form: " + display);
			var lore = display.getListOrEmpty("Lore");
			check(lore.size() == 3 && lore.getStringOr(2, "").contains("LEGENDARY SWORD"), "Lore not the item's own lines: " + lore);
			check(!lore.toString().contains("Fates"), "our own tooltip lines leaked into Lore: " + lore);

			// 2c. too few sales: rough, display-only value
			ItemStack rough = skyblockItem("SOME_RARE_THING", FakeViewer.ROUGH_UUID, "Rare Thing");
			ctx.runOnClient(mc -> tooltip(mc, rough));
			ctx.waitFor(mc -> String.join("\n", tooltip(mc, rough)).contains("rough value ~5M"), 400);

			// 3. open-best: one lookup, one /viewauction
			ctx.runOnClient(mc -> service.openNext("best"));
			ctx.waitFor(mc -> service.lastOpened() != null && FakeViewer.NEXT_ID.equals(service.lastOpened().auctionId()), 400);
			ctx.waitFor(mc -> service.lastOpenedPrice() != null, 400);

			// 4. auction overlay on a window titled like Hypixel's
			// 4a. a different auction (wrong price) opened right after the flip gets nothing
			SimpleContainer wrongBox = auctionBox(5_454_545_454L, "Click to purchase!");
			ctx.runOnClient(mc -> mc.gui.setScreen(auctionWindow(mc, wrongBox, "BIN Auction View", false)));
			ctx.waitTicks(20);
			check(Boolean.FALSE.equals(ctx.computeOnClient(mc -> AuctionOverlay.debugMatch(mc.gui.screen()))),
					"overlay attached to an auction that isn't the flip");
			ctx.takeScreenshot("02a_other_auction_untouched");
			// 4b. the flip's own window: matched by the price on the button, Buy outlined green
			SimpleContainer box = auctionBox(30_000_000L, "Click to purchase!");
			ctx.runOnClient(mc -> mc.gui.setScreen(auctionWindow(mc, box, "BIN Auction View", false)));
			ctx.waitTicks(20);
			check(Boolean.TRUE.equals(ctx.computeOnClient(mc -> AuctionOverlay.debugMatch(mc.gui.screen()))),
					"overlay didn't attach to the flip's own auction");
			ctx.takeScreenshot("02_auction_view_overlay");
			// 4c. the button turns into "Collect Auction" (sold): red, live
			ctx.runOnClient(mc -> box.setItem(31, named(Items.POTATO, "Collect Auction", "", "Someone else purchased the item!")));
			ctx.waitTicks(10);
			ctx.takeScreenshot("02c_auction_sold");
			ctx.runOnClient(mc -> box.setItem(31, named(Items.GOLD_NUGGET, "Buy Item Right Now", "", "Price: 30,000,000 coins", "", "Cannot afford bid!")));
			ctx.waitTicks(10);
			ctx.takeScreenshot("02d_auction_cant_afford");
			ctx.runOnClient(mc -> box.setItem(31, named(Items.GOLD_NUGGET, "Buy Item Right Now", "", "Price: 30,000,000 coins", "", "Click to purchase!")));
			ctx.runOnClient(mc -> mc.gui.setScreen(auctionWindow(mc, new SimpleContainer(27), "Confirm Purchase", true)));
			ctx.waitTicks(20);
			check(Boolean.TRUE.equals(ctx.computeOnClient(mc -> AuctionOverlay.debugMatch(mc.gui.screen()))),
					"confirm window of the flip wasn't recognised");
			ctx.takeScreenshot("03_confirm_overlay");
			// 4d. real mouse buttons (26.3 numbers them differently): left / right clicks are always the
			// player's own and never close the window; the open key bound to a mouse button does
			ctx.waitTicks(12); // past the half-second key cooldown
			ctx.getInput().setCursorPos(2, 2);
			ctx.runOnClient(mc -> Keys.openBest.setKey(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_MIDDLE)));
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(3);
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(3);
			check(ctx.computeOnClient(mc -> mc.gui.screen() != null), "a left / right click closed the flip's window");
			ctx.waitTicks(12);
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
			ctx.waitTicks(5);
			check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "the open key (on a mouse button) didn't close the flip's window");
			ctx.runOnClient(mc -> Keys.openBest.setKey(InputConstants.UNKNOWN));
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			// 4e. the Fates look: card gallery (font calibration, flip hover, item tooltips, side panel)
			ctx.getInput().resizeWindow(1280, 720);
			ctx.waitTicks(10);
			io.github.donttemptfate.fatesflipper.core.FlipEvent galleryFlip = ctx.computeOnClient(mc -> service.recentFlip(FakeViewer.FLIP_ID));
			ItemStack rich = richItem();
			ItemStack simple = simpleItem();
			for (int page = 0; page < 3; page++) {
				int p = page;
				ctx.setScreen(() -> new CardGallery(p, galleryFlip, rich, simple));
				ctx.waitTicks(10);
				ctx.takeScreenshot("06_gallery_" + p);
			}
			// the same cards with Minecraft's font (setting "Chakra font" off)
			ctx.runOnClient(mc -> io.github.donttemptfate.fatescommon.ui.Ui.useChakra(false));
			ctx.setScreen(() -> new CardGallery(1, galleryFlip, rich, simple));
			ctx.waitTicks(10);
			ctx.takeScreenshot("06_gallery_vanilla_font");
			ctx.runOnClient(mc -> io.github.donttemptfate.fatescommon.ui.Ui.useChakra(true));
			// 4f. a real hovered item in a chest: our tooltip replaces vanilla's
			SimpleContainer chest = new SimpleContainer(27);
			chest.setItem(10, rich);
			chest.setItem(12, simple);
			chest.setItem(14, longItem());
			ctx.runOnClient(mc -> mc.gui.setScreen(new ContainerScreen(ChestMenu.threeRows(0, mc.player.getInventory(), chest),
					mc.player.getInventory(), Component.literal("Chest"))));
			ctx.waitTicks(10);
			double[] at = ctx.computeOnClient(mc -> {
				var acc = (io.github.donttemptfate.fatesflipper.mixin.AbstractContainerScreenAccessor) mc.gui.screen();
				var slot = ((ContainerScreen) mc.gui.screen()).getMenu().slots.get(10);
				double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
				return new double[] {(acc.fatesflipper$getLeftPos() + slot.x + 8) * scale, (acc.fatesflipper$getTopPos() + slot.y + 8) * scale};
			});
			ctx.getInput().setCursorPos(at[0], at[1]);
			ctx.waitTicks(10);
			ctx.takeScreenshot("07_item_tooltip_live");
			var modLines = ctx.computeOnClient(mc -> io.github.donttemptfate.fatesflipper.ui.ItemTooltips.otherModsLines(rich)
					.stream().map(Component::getString).toList());
			check(modLines.stream().anyMatch(l -> l.contains("OtherMod value")), "another mod's tooltip line was dropped: " + modLines);
			check(modLines.stream().noneMatch(l -> l.startsWith("Fates")), "our own price lines leaked into MODS: " + modLines);
			// a tooltip taller than the screen: shown full size, scrolled with the mouse wheel
			double[] at2 = ctx.computeOnClient(mc -> {
				var acc = (io.github.donttemptfate.fatesflipper.mixin.AbstractContainerScreenAccessor) mc.gui.screen();
				var slot = ((ContainerScreen) mc.gui.screen()).getMenu().slots.get(14);
				double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
				return new double[] {(acc.fatesflipper$getLeftPos() + slot.x + 8) * scale, (acc.fatesflipper$getTopPos() + slot.y + 8) * scale};
			});
			ctx.getInput().setCursorPos(at2[0], at2[1]);
			ctx.waitTicks(10);
			ctx.takeScreenshot("07b_long_tooltip_top");
			ctx.getInput().scroll(-20);
			ctx.waitTicks(10);
			ctx.takeScreenshot("07c_long_tooltip_scrolled");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			// 4f2. Auctions Browser: an item without a uuid whose "Ends in" countdown changes every
			// second is looked up once (Hypixel's listing lines are never sent) and its tooltip holds still
			SimpleContainer browser = new SimpleContainer(54);
			browser.setItem(10, listedScroll(0));
			ctx.runOnClient(mc -> mc.gui.setScreen(new ContainerScreen(ChestMenu.sixRows(0, mc.player.getInventory(), browser),
					mc.player.getInventory(), Component.literal("Auctions Browser"))));
			ctx.waitTicks(10);
			int callsBefore = fake.priceCalls.get();
			ctx.getInput().setCursorPos(slotPos(ctx, 10)[0], slotPos(ctx, 10)[1]);
			for (int sec = 1; sec <= 12; sec++) {
				int t = sec;
				ctx.runOnClient(mc -> browser.setItem(10, listedScroll(t)));
				ctx.waitTicks(5);
				if (sec == 2) ctx.takeScreenshot("09a_listing_tooltip_early");
			}
			ctx.takeScreenshot("09b_listing_tooltip_later");
			int lookups = fake.priceCalls.get() - callsBefore;
			check(lookups <= 1, "an item with a ticking countdown was looked up " + lookups + " times (expected once)");
			var sentLore = fake.lastItem.get().getListOrEmpty("i").getCompoundOrEmpty(0).getCompoundOrEmpty("tag")
					.getCompoundOrEmpty("display").getListOrEmpty("Lore");
			check(!sentLore.toString().contains("Seller") && !sentLore.toString().contains("Ends in"),
					"Hypixel's listing lines were sent with the item: " + sentLore);
			check(sentLore.getStringOr(sentLore.size() - 1, "").contains("EPIC TRAVEL SCROLL"), "rarity isn't the last line sent: " + sentLore);

			// 4f3. Bazaar: a product's menu item says "Bazaar item" (no auction lookup), and the same id
			// without a uuid elsewhere (e.g. in the inventory) is recognised after the Bazaar was seen
			SimpleContainer bz = new SimpleContainer(54);
			bz.setItem(10, bazaarMenuItem());
			ItemStack plainSeeds = sbItem(Items.WHEAT_SEEDS, "SEEDS", null, "Seeds", 35, "COMMON");
			bz.setItem(12, plainSeeds);
			ctx.runOnClient(mc -> mc.gui.setScreen(new ContainerScreen(ChestMenu.sixRows(0, mc.player.getInventory(), bz),
					mc.player.getInventory(), Component.literal("Farming ➜ Wheat & Seeds"))));
			ctx.waitTicks(25); // the once-a-second Bazaar scan
			int bzBefore = fake.priceCalls.get();
			ctx.getInput().setCursorPos(slotPos(ctx, 10)[0], slotPos(ctx, 10)[1]);
			ctx.waitTicks(10);
			ctx.takeScreenshot("10a_bazaar_menu_item");
			ctx.getInput().setCursorPos(slotPos(ctx, 12)[0], slotPos(ctx, 12)[1]);
			ctx.waitTicks(10);
			ctx.takeScreenshot("10b_bazaar_item_elsewhere");
			check(ctx.computeOnClient(mc -> PriceTooltips.view(service, plainSeeds).kind() == PriceTooltips.Kind.BAZAAR),
					"a Bazaar product seen in the Bazaar wasn't recognised elsewhere");
			check(fake.priceCalls.get() == bzBefore, "Bazaar items were looked up on the auction side");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			// 4g. chat: open it and hover the newest alert line (best effort: position depends on the chat layout)
			ctx.runOnClient(mc -> io.github.donttemptfate.fatesflipper.Alerts.show(service, galleryFlip));
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
			ctx.waitTicks(5);
			double[] chatAt = ctx.computeOnClient(mc -> {
				double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
				return new double[] {30 * scale, (mc.getWindow().getGuiScaledHeight() - 44) * scale};
			});
			ctx.getInput().setCursorPos(chatAt[0], chatAt[1]);
			ctx.waitTicks(10);
			ctx.takeScreenshot("08_chat_hover");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.getInput().resizeWindow(854, 480);
			ctx.waitTicks(10);

			// 5. settings screen
			ctx.setScreen(() -> new ConfigScreen(null, service));
			ctx.waitTicks(10);
			ctx.takeScreenshot("04_settings");
			ctx.setScreen(() -> null);
			// the shared theme (Fates Common settings), picked with /ff theme
			ctx.runOnClient(mc -> {
				ModConfig c = service.config().copy();
				c.theme = "ember";
				service.applyConfig(c);
			});
			ctx.runOnClient(mc -> {
				if (io.github.donttemptfate.fatescommon.ui.Ui.theme() != io.github.donttemptfate.fatescommon.ui.Theme.EMBER) throw new AssertionError("theme not applied");
				if (!"ember".equals(service.config().theme)) throw new AssertionError("theme not mirrored");
			});
			ctx.setScreen(() -> new ConfigScreen(null, service));
			ctx.waitTicks(10);
			ctx.takeScreenshot("04b_settings_theme_ember");
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> {
				ModConfig c = service.config().copy();
				c.theme = "fates";
				service.applyConfig(c);
			});

			// 6. a revoked token stops the stream without retry storms
			ctx.runOnClient(mc -> {
				ModConfig c = service.config().copy();
				c.token = "fv_revoked_token_999";
				service.applyConfig(c);
			});
			ctx.waitFor(mc -> service.streamState() == FlipStream.State.STOPPED, 600);
			int before = fake.unauthorized.get();
			ctx.waitTicks(100);
			check(fake.unauthorized.get() == before, "kept retrying after 401");
			ctx.takeScreenshot("05_token_revoked_hud");

			// 7. version handshake: a server needing a newer mod turns flips off and says so (audit F-S5)
			ctx.runOnClient(mc -> {
				ModConfig c = service.config().copy();
				c.token = FakeViewer.TOKEN;
				service.applyConfig(c);
			});
			ctx.waitFor(mc -> service.streamState() == FlipStream.State.CONNECTED, 600);
			ctx.runOnClient(mc -> service.applyVersions("9.9.9", "9.9.9", "https://example.com/fates-flipper"));
			ctx.waitTicks(40);
			check(service.updateRequired() != null, "an outdated mod wasn't flagged");
			check(service.streamState() != FlipStream.State.CONNECTED, "flips kept streaming on an outdated mod");
			ctx.takeScreenshot("05b_update_required_hud");
		} finally {
			ctx.runOnClient(mc -> {
				ModConfig c = service.config().copy();
				c.token = "";
				service.applyConfig(c);
			});
			fake.stop();
		}
	}

	private static void run(Minecraft mc, String command) {
		var dispatcher = ClientCommands.getActiveDispatcher();
		check(dispatcher != null, "client commands not registered");
		try {
			dispatcher.execute(command, (FabricClientCommandSource) mc.getConnection().getSuggestionsProvider());
		} catch (Exception e) {
			throw new AssertionError("/" + command + " failed: " + e, e);
		}
	}

	private static List<String> tooltip(Minecraft mc, ItemStack stack) {
		return stack.getTooltipLines(Item.TooltipContext.EMPTY, mc.player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
	}

	/** A 54-slot "BIN Auction View" like Hypixel's: filler, the item in slot 13, the button in slot 31. */
	private static SimpleContainer auctionBox(long price, String lastLine) {
		SimpleContainer box = new SimpleContainer(54);
		ItemStack filler = new ItemStack(Items.GLASS_PANE);
		filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		for (int i = 0; i < 54; i++) box.setItem(i, filler.copy());
		box.setItem(13, named(Items.BOW, "Juju Shortbow"));
		box.setItem(31, named(Items.GOLD_NUGGET, "Buy Item Right Now", "", "Price: " + String.format(java.util.Locale.ROOT, "%,d", price) + " coins", "", lastLine));
		box.setItem(49, named(Items.BARRIER, "Close"));
		return box;
	}

	private static ContainerScreen auctionWindow(Minecraft mc, SimpleContainer box, String title, boolean confirm) {
		if (confirm) {
			ItemStack filler = new ItemStack(Items.GLASS_PANE);
			filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
			for (int i = 0; i < box.getContainerSize(); i++) box.setItem(i, filler.copy());
			box.setItem(11, named(Items.EMERALD, "Confirm"));
			box.setItem(13, named(Items.BOW, "Juju Shortbow"));
			box.setItem(15, named(Items.BARRIER, "Cancel"));
		}
		ChestMenu menu = confirm
				? ChestMenu.threeRows(0, mc.player.getInventory(), box)
				: ChestMenu.sixRows(0, mc.player.getInventory(), box);
		return new ContainerScreen(menu, mc.player.getInventory(), Component.literal(title));
	}

	private static ItemStack named(net.minecraft.world.item.Item item, String name, String... lore) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		if (lore.length > 0) {
			List<Component> lines = new java.util.ArrayList<>();
			for (String l : lore) lines.add(Component.literal(l));
			s.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
		}
		return s;
	}

	/** A long SkyBlock tooltip like a farming tool's: stats, enchants with descriptions, a level bar. */
	private static ItemStack richItem() {
		ItemStack s = new ItemStack(Items.DIAMOND_HOE);
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "FUNGI_CUTTER_2");
		tag.putString("uuid", FakeViewer.NEVER_UUID);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, Component.literal("Bountiful Fungi Cutter Mk. II").withStyle(net.minecraft.ChatFormatting.BLUE));
		List<Component> lore = new java.util.ArrayList<>();
		lore.add(Component.literal("Efficiency V").withStyle(net.minecraft.ChatFormatting.BLUE));
		lore.add(Component.literal("Farming Tool").withStyle(net.minecraft.ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(Component.literal("Farming Fortune: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("+79.5 ").withStyle(net.minecraft.ChatFormatting.GOLD)).append(Component.literal("(+3)").withStyle(net.minecraft.ChatFormatting.GREEN)));
		lore.add(Component.literal("Mushroom Fortune: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("+49").withStyle(net.minecraft.ChatFormatting.GOLD)));
		lore.add(Component.literal("Speed: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("+3 ").withStyle(net.minecraft.ChatFormatting.WHITE)).append(Component.literal("(+3)").withStyle(net.minecraft.ChatFormatting.GREEN)));
		lore.add(Component.literal("Farming Wisdom: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("+7").withStyle(net.minecraft.ChatFormatting.AQUA)));
		lore.add(Component.literal("Gemstones: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("[◇] [◇]").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)));
		lore.add(Component.empty());
		lore.add(Component.literal("Cultivating VII ").withStyle(net.minecraft.ChatFormatting.BLUE).append(Component.literal("1,893,885").withStyle(net.minecraft.ChatFormatting.GRAY)));
		lore.add(Component.literal("Gain +7 Farming Wisdom and +14 Farming Fortune.").withStyle(net.minecraft.ChatFormatting.GRAY));
		lore.add(Component.literal("Harvesting V").withStyle(net.minecraft.ChatFormatting.BLUE));
		lore.add(Component.literal("Grants +62.5 Farming Fortune, which increases your chance for multiple crops.").withStyle(net.minecraft.ChatFormatting.GRAY));
		lore.add(Component.literal("Efficiency V").withStyle(net.minecraft.ChatFormatting.BLUE));
		lore.add(Component.literal("Increases how quickly your tool breaks blocks.").withStyle(net.minecraft.ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(Component.literal("Level ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("22 -> 23").withStyle(net.minecraft.ChatFormatting.GREEN)));
		lore.add(Component.literal("━━━━━━━━").withStyle(net.minecraft.ChatFormatting.GREEN).append(Component.literal("━━━━━━━━━━━").withStyle(net.minecraft.ChatFormatting.WHITE)).append(Component.literal(" 42.42%").withStyle(net.minecraft.ChatFormatting.YELLOW)));
		lore.add(Component.empty());
		lore.add(Component.literal("Right-click to view recipes!").withStyle(net.minecraft.ChatFormatting.YELLOW));
		lore.add(Component.empty());
		lore.add(Component.literal("RARE FARMING TOOL").withStyle(net.minecraft.ChatFormatting.BLUE, net.minecraft.ChatFormatting.BOLD));
		s.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
		return s;
	}

	/** A very long tooltip (many enchants with descriptions), taller than the screen. */
	private static ItemStack longItem() {
		ItemStack s = new ItemStack(Items.DIAMOND_SWORD);
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "HYPERION");
		tag.putString("uuid", FakeViewer.ITEM_UUID);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, Component.literal("Heroic Hyperion ✪✪✪✪✪").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
		List<Component> lore = new java.util.ArrayList<>();
		for (String st : new String[] {"Damage", "Strength", "Crit Damage", "Bonus Attack Speed", "Intelligence", "Ferocity"}) {
			lore.add(Component.literal(st + ": ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("+" + (100 + st.length() * 7)).withStyle(net.minecraft.ChatFormatting.RED)));
		}
		lore.add(Component.empty());
		String[] ench = {"Sharpness VII", "Critical VII", "Giant Killer VII", "Ender Slayer VII", "Vampirism VI", "Looting V",
				"Scavenger V", "Luck VII", "Champion X", "Cleave VI", "Thunderlord VII", "Lethality VI", "Fire Aspect III",
				"Venomous VI", "Smite VII", "Bane of Arthropods VII", "Experience V", "First Strike V", "Execute VI", "Prosecute VI"};
		for (String e : ench) {
			lore.add(Component.literal(e).withStyle(net.minecraft.ChatFormatting.BLUE));
			lore.add(Component.literal("Increases damage dealt by a lot in situations that matter, with a long description.").withStyle(net.minecraft.ChatFormatting.GRAY));
		}
		lore.add(Component.empty());
		lore.add(Component.literal("Ability: Wither Impact RIGHT CLICK").withStyle(net.minecraft.ChatFormatting.GOLD));
		lore.add(Component.literal("Teleport 10 blocks ahead of you. Then implode dealing damage to nearby enemies.").withStyle(net.minecraft.ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(Component.literal("LEGENDARY DUNGEON SWORD").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
		s.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
		return s;
	}

	/** A short SkyBlock tooltip (a cosmetic): name, a description, an action line, the rarity. */
	private static ItemStack simpleItem() {
		ItemStack s = new ItemStack(Items.PAPER);
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "BARN_SKIN_6TH_ANNIVERSARY");
		tag.putString("uuid", FakeViewer.ITEM_UUID);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, Component.literal("6th Anniversary Barn Skin").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
		s.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
				Component.literal("Consume this item to unlock the ").withStyle(net.minecraft.ChatFormatting.GRAY)
						.append(Component.literal("6th Anniversary Barn Skin").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE))
						.append(Component.literal(" on ").withStyle(net.minecraft.ChatFormatting.GRAY))
						.append(Component.literal("The Garden").withStyle(net.minecraft.ChatFormatting.GREEN))
						.append(Component.literal("!").withStyle(net.minecraft.ChatFormatting.GRAY)),
				Component.empty(),
				Component.literal("Click to consume!").withStyle(net.minecraft.ChatFormatting.YELLOW),
				Component.empty(),
				Component.literal("LEGENDARY COSMETIC").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD))));
		return s;
	}

	private static ItemStack skyblockItem(String id, String uuid, String name) {
		ItemStack s = new ItemStack(Items.DIAMOND_SWORD);
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		tag.putString("uuid", uuid);
		tag.putInt("hot_potato_count", 10);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(net.minecraft.ChatFormatting.GOLD));
		s.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
				Component.literal("Damage: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("+270").withStyle(net.minecraft.ChatFormatting.RED)),
				Component.empty(),
				Component.literal("LEGENDARY SWORD").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD))));
		return s;
	}

	/** Screen position (window pixels) of a slot's centre in the open container screen. */
	private static double[] slotPos(ClientGameTestContext ctx, int index) {
		return ctx.computeOnClient(mc -> {
			var acc = (io.github.donttemptfate.fatesflipper.mixin.AbstractContainerScreenAccessor) mc.gui.screen();
			var slot = ((ContainerScreen) mc.gui.screen()).getMenu().slots.get(index);
			double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
			return new double[] {(acc.fatesflipper$getLeftPos() + slot.x + 8) * scale, (acc.fatesflipper$getTopPos() + slot.y + 8) * scale};
		});
	}

	/** A SkyBlock item (uuid may be null) with plain lore lines. */
	private static ItemStack sbItem(Item item, String id, String uuid, String name, int count, String... lore) {
		ItemStack s = new ItemStack(item, count);
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		if (uuid != null) tag.putString("uuid", uuid);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		List<Component> lines = new java.util.ArrayList<>();
		for (String l : lore) lines.add(Component.literal(l));
		s.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
		return s;
	}

	/** A travel scroll as the Auctions Browser shows it: its own lore, then Hypixel's listing lines with a live countdown. */
	private static ItemStack listedScroll(int secondsGone) {
		int left = 5 * 3600 + 49 * 60 + 49 - secondsGone;
		String ends = String.format(java.util.Locale.ROOT, "%02dh %02dm %02ds", left / 3600, left / 60 % 60, left % 60);
		ItemStack s = sbItem(Items.PAPER, "SPIDER_DEN_TOP_TRAVEL_SCROLL", null, "Travel Scroll to Spider's Den Top of Nest", 1,
				"Consume this item to add its", "destination to your Fast Travel", "options.", "", "Requires MVP+ to consume!", "",
				"Island: Spider's Den", "Teleport: Top of Nest", "", "EPIC TRAVEL SCROLL");
		List<Component> lore = new java.util.ArrayList<>(s.get(DataComponents.LORE).lines());
		lore.add(Component.literal("-----------------").withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.STRIKETHROUGH));
		lore.add(Component.literal("Seller: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("[MVP+] Invisity_").withStyle(net.minecraft.ChatFormatting.AQUA)));
		lore.add(Component.literal("Buy it now: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("1,000 coins").withStyle(net.minecraft.ChatFormatting.GOLD)));
		lore.add(Component.empty());
		lore.add(Component.literal("Ends in: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal(ends).withStyle(net.minecraft.ChatFormatting.YELLOW)));
		lore.add(Component.empty());
		lore.add(Component.literal("Click to inspect!").withStyle(net.minecraft.ChatFormatting.YELLOW));
		s.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
		return s;
	}

	/** A Bazaar product's menu item (Hypixel's Bazaar window). */
	private static ItemStack bazaarMenuItem() {
		return sbItem(Items.WHEAT_SEEDS, "SEEDS", null, "Seeds", 1, "Common commodity", "Buy price: 2.5 coins",
				"34M in 527 offers", "", "Sell price: 1.2 coins", "4.7M in 73 orders", "", "Click to view details!");
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}

	/** The newest chat lines as plain text (read by reflection: test only). */
	static String chatText(net.minecraft.client.Minecraft mc) {
		return chatText(mc, 8);
	}

	static String chatText(net.minecraft.client.Minecraft mc, int lines) {
		try {
			// the chat component sits on Gui or, in 26.x, on Gui.hud
			Object chat = findChat(mc.gui);
			if (chat == null) {
				for (java.lang.reflect.Field f : mc.gui.getClass().getDeclaredFields()) {
					if (f.getType().isPrimitive() || !f.getType().getName().startsWith("net.minecraft.client.gui")) continue;
					f.setAccessible(true);
					Object inner = f.get(mc.gui);
					if (inner != null && (chat = findChat(inner)) != null) break;
				}
			}
			java.lang.reflect.Field all = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("allMessages");
			all.setAccessible(true);
			java.util.List<?> msgs = (java.util.List<?>) all.get(chat);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < Math.min(lines, msgs.size()); i++) {
				Object m = msgs.get(i);
				var content = (net.minecraft.network.chat.Component) m.getClass().getMethod("content").invoke(m);
				sb.append(content.getString()).append('\n');
			}
			return sb.toString();
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static Object findChat(Object o) throws IllegalAccessException {
		for (java.lang.reflect.Field f : o.getClass().getDeclaredFields()) {
			if (net.minecraft.client.gui.components.ChatComponent.class.isAssignableFrom(f.getType())) {
				f.setAccessible(true);
				Object v = f.get(o);
				if (v != null) return v;
			}
		}
		return null;
	}
}
