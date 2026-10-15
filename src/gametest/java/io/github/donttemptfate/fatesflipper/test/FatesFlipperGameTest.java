package io.github.donttemptfate.fatesflipper.test;

import io.github.donttemptfate.fatesflipper.ConfigScreen;
import io.github.donttemptfate.fatesflipper.FatesFlipperClient;
import io.github.donttemptfate.fatesflipper.FlipService;
import io.github.donttemptfate.fatesflipper.core.FlipStream;
import io.github.donttemptfate.fatesflipper.core.ModConfig;
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

			// 3. open-best: one lookup, one /viewauction
			ctx.runOnClient(mc -> service.openNext("best"));
			ctx.waitFor(mc -> service.lastOpened() != null && FakeViewer.NEXT_ID.equals(service.lastOpened().auctionId()), 400);
			ctx.waitFor(mc -> service.lastOpenedPrice() != null, 400);

			// 4. auction overlay on a window titled like Hypixel's
			ctx.runOnClient(mc -> mc.gui.setScreen(auctionWindow(mc, "BIN Auction View", false)));
			ctx.waitTicks(20);
			ctx.takeScreenshot("02_auction_view_overlay");
			ctx.runOnClient(mc -> mc.gui.setScreen(auctionWindow(mc, "Confirm Purchase", true)));
			ctx.waitTicks(20);
			ctx.takeScreenshot("03_confirm_overlay");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

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

	private static List<String> tooltip(Minecraft mc, ItemStack stack) {
		return stack.getTooltipLines(Item.TooltipContext.EMPTY, mc.player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
	}

	private static ContainerScreen auctionWindow(Minecraft mc, String title, boolean confirm) {
		SimpleContainer box = new SimpleContainer(confirm ? 27 : 54);
		ItemStack filler = new ItemStack(Items.GLASS_PANE);
		filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		for (int i = 0; i < box.getContainerSize(); i++) box.setItem(i, filler.copy());
		if (confirm) {
			box.setItem(11, named(Items.EMERALD, "Confirm"));
			box.setItem(15, named(Items.BARRIER, "Cancel"));
		} else {
			box.setItem(13, named(Items.BOW, "Juju Shortbow"));
			box.setItem(31, named(Items.GOLD_NUGGET, "Buy Item Right Now"));
			box.setItem(49, named(Items.BARRIER, "Close"));
		}
		ChestMenu menu = confirm
				? ChestMenu.threeRows(0, mc.player.getInventory(), box)
				: ChestMenu.sixRows(0, mc.player.getInventory(), box);
		return new ContainerScreen(menu, mc.player.getInventory(), Component.literal(title));
	}

	private static ItemStack named(net.minecraft.world.item.Item item, String name) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return s;
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
