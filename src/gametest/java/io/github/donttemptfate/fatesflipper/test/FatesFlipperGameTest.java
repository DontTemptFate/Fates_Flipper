package io.github.donttemptfate.fatesflipper.test;

import io.github.donttemptfate.fatesflipper.AuctionOverlay;
import io.github.donttemptfate.fatesflipper.ConfigScreen;
import io.github.donttemptfate.fatesflipper.FatesFlipperClient;
import io.github.donttemptfate.fatesflipper.FlipService;
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

			// 1b. the /ff commands run without errors
			for (String cmd : List.of("ff help", "ff status", "ff profiles", "ff recent", "ff open not-an-id")) {
				ctx.runOnClient(mc -> run(mc, cmd));
				ctx.waitTicks(10);
			}
			ctx.waitTicks(20);
			ctx.takeScreenshot("01b_commands");

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
			// 4d. pressing the open key again closes the flip's window (like Esc)
			ctx.waitTicks(12); // past the half-second key cooldown
			ctx.runOnClient(mc -> service.closeFlipWindow(mc.gui.screen()));
			ctx.waitTicks(5);
			check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "the close key didn't close the flip's window");
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
			// 4f. a real hovered item in a chest: our tooltip replaces vanilla's
			SimpleContainer chest = new SimpleContainer(27);
			chest.setItem(10, rich);
			chest.setItem(12, simple);
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

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
