package io.github.donttemptfate.fatesflipper.core;

import io.github.donttemptfate.fatescommon.skyblock.LoreParts;
import io.github.donttemptfate.fatescommon.text.Coins;
import io.github.donttemptfate.fatescommon.text.LegacyText;
import io.github.donttemptfate.fatescommon.text.Versions;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class CoreTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";

	@Test
	public void auctionIds() {
		assertTrue(AuctionIds.isValid(ID));
		assertFalse(AuctionIds.isValid(ID.toUpperCase()));
		assertFalse(AuctionIds.isValid(ID + "0"));
		assertFalse(AuctionIds.isValid("0123456789abcdef0123456789abcdeg"));
		assertEquals(ID, AuctionIds.normalize("01234567-89AB-CDEF-0123-456789ABCDEF"));
		assertNull(AuctionIds.normalize("x; /pay someone 1000"));
		assertEquals("viewauction " + ID, AuctionIds.viewAuctionCommand(ID));
		assertTrue(AuctionIds.isExpectedViewCommand("/viewauction " + ID, ID));
		assertTrue(AuctionIds.isExpectedViewCommand("viewauction " + ID, ID));
		assertFalse(AuctionIds.isExpectedViewCommand("/viewauction " + ID + " extra", ID));
		assertFalse(AuctionIds.isExpectedViewCommand("/pay x 1", ID));
		assertFalse(AuctionIds.isExpectedViewCommand("/viewauction " + ID.replace('0', '1'), ID));
	}

	@Test(expected = IllegalArgumentException.class)
	public void viewCommandRefusesBadIds() {
		AuctionIds.viewAuctionCommand("abc; /trade x");
	}

	@Test
	public void serverAddress() {
		assertEquals("https://flips.example.com", ServerAddress.check("flips.example.com").baseUrl());
		assertEquals("https://flips.example.com", ServerAddress.check("https://Flips.Example.com/").baseUrl());
		assertEquals("https://box.tail1234.ts.net:8443", ServerAddress.check("https://box.tail1234.ts.net:8443").baseUrl());
		assertEquals("http://127.0.0.1:8738", ServerAddress.check("http://127.0.0.1:8738").baseUrl());
		assertEquals("http://100.101.102.103:8738", ServerAddress.check("http://100.101.102.103:8738").baseUrl());
		assertEquals("http://localhost:8738", ServerAddress.check("http://localhost:8738").baseUrl());
		assertFalse(ServerAddress.check("http://flips.example.com").ok());
		assertFalse(ServerAddress.check("http://100.200.1.1").ok());
		assertTrue(ServerAddress.check("http://10.0.0.5").ok()); // LAN allowed since 0.3.4 (audit F-S3)
		assertFalse(ServerAddress.check("ftp://x.com").ok());
		assertFalse(ServerAddress.check("https://user:pw@x.com").ok());
		assertFalse(ServerAddress.check("https://x.com/mod").ok());
		assertFalse(ServerAddress.check("https://x.com/?a=b").ok());
		assertFalse(ServerAddress.check("").ok());
		assertFalse(ServerAddress.check(null).ok());
		assertFalse(ServerAddress.check("http://127.0.0.1.:8738").ok());
		assertFalse(ServerAddress.check("http://100.64.0.1.").ok());
		assertTrue(ServerAddress.looksLikeToken("fv_abcDEF123_-xyz"));
		assertFalse(ServerAddress.looksLikeToken("abc"));
	}

	@Test
	public void sse() {
		SseParser p = new SseParser();
		assertNull(p.feed(": keep-alive"));
		assertNull(p.feed("event: flip"));
		assertNull(p.feed("id: 7"));
		assertNull(p.feed("data: {\"a\":"));
		assertNull(p.feed("data: 1}"));
		SseParser.Event e = p.feed("");
		assertNotNull(e);
		assertEquals("flip", e.event());
		assertEquals("{\"a\":\n1}", e.data());
		assertEquals("7", e.id());
		// event name resets; default is "message"
		assertNull(p.feed("data:x"));
		e = p.feed("");
		assertEquals("message", e.event());
		assertEquals("x", e.data());
		// blank line without data dispatches nothing
		assertNull(p.feed("event: profile"));
		assertNull(p.feed(""));
		assertNull(p.feed(""));
	}

	@Test
	public void sseDropsHugeEvents() {
		SseParser p = new SseParser();
		String chunk = "x".repeat(100_000);
		for (int i = 0; i < 10; i++) p.feed("data: " + chunk);
		assertNull(p.feed(""));
		p.feed("data: ok");
		assertEquals("ok", p.feed("").data());
	}

	@Test
	public void boundedReads() throws Exception {
		java.io.BufferedReader r = new java.io.BufferedReader(new java.io.StringReader("a\r\nb\nc\rd"));
		assertEquals("a", FlipStream.readLine(r));
		assertEquals("b", FlipStream.readLine(r));
		assertEquals("c", FlipStream.readLine(r));
		assertEquals("d", FlipStream.readLine(r));
		assertNull(FlipStream.readLine(r));
		java.io.BufferedReader endless = new java.io.BufferedReader(new java.io.StringReader("x".repeat(FlipStream.MAX_LINE_CHARS + 10)));
		try {
			FlipStream.readLine(endless);
			fail("an endless line must be refused");
		} catch (java.io.IOException expected) {
			// ok
		}
		assertEquals("{}", ViewerApi.readLimited(new java.io.ByteArrayInputStream("{}".getBytes())));
		assertNull(ViewerApi.readLimited(new java.io.ByteArrayInputStream(new byte[ViewerApi.MAX_BODY_BYTES + 1])));
	}

	@Test
	public void legacyText() {
		List<LegacyText.Run> runs = LegacyText.parse("§6FLIP §lHyperion §r§7for §e1.2B");
		assertEquals(4, runs.size());
		assertEquals("FLIP ", runs.get(0).text());
		assertEquals('6', runs.get(0).color());
		assertEquals("Hyperion ", runs.get(1).text());
		assertTrue(runs.get(1).bold());
		assertEquals('6', runs.get(1).color());
		assertEquals("for ", runs.get(2).text());
		assertFalse(runs.get(2).bold());
		assertEquals('7', runs.get(2).color());
		assertEquals("FLIP Hyperion for 1.2B", LegacyText.strip("§6FLIP §lHyperion §r§7for §e1.2B"));
		// colour code resets bold; unknown codes stay as text; control chars and newlines removed
		assertEquals("a bzc", LegacyText.strip("a\nb§zc\u0007"));
		assertEquals(LegacyText.MAX_CHARS, LegacyText.strip("y".repeat(5000)).length());
		assertTrue(LegacyText.parse("§l§aX").get(0).color() == 'a' && !LegacyText.parse("§l§aX").get(0).bold());
		assertEquals("end", LegacyText.strip("end§"));
	}

	@Test
	public void coins() {
		assertEquals("950", Coins.format(950));
		assertEquals("12.5k", Coins.format(12_500));
		assertEquals("1.23M", Coins.format(1_234_567));
		assertEquals("100M", Coins.format(100_000_000));
		assertEquals("1.2B", Coins.format(1_200_000_000));
		assertEquals("-2M", Coins.format(-2_000_000));
		assertEquals("?", Coins.format(Double.NaN));
		assertEquals("45s", Coins.age(45_000));
		assertEquals("3m", Coins.age(185_000));
		assertEquals("2h 5m", Coins.age(7_500_000));
		assertEquals("34%", Coins.percent(0.34));
	}

	@Test
	public void flipEventParsing() {
		JsonObject flip = Json.parseObject("""
				{"card":{"uuid":"%s","name":"§dHyperion","price":1000000000,"profit":50000000,
				 "safe_profit":30000000,"safe_pct":0.03,"safe_resale":1040000000,"dupe":null,"first_seen_at":1700000000000,
				 "estimate":{"confidence":"HIGH","resale":1060000000,"volume_per_day":4.5,"exact":{"n_used":12}}},
				 "score":{"exp":25000000,"chance":0.8},"chat":"§6FLIP §dHyperion","command":"/viewauction %s",
				 "profile":{"id":3,"name":"Main"}}
				""".formatted(ID, ID));
		FlipEvent f = FlipEvent.parse(flip);
		assertNotNull(f);
		assertEquals(ID, f.auctionId());
		assertEquals("HIGH", f.confidence());
		assertEquals(12, f.salesUsed());
		assertEquals(0.8, f.chance(), 1e-9);
		assertTrue(f.commandMatches());
		assertEquals("Main", f.profileName());
		assertTrue(f.fallbackChat().contains("1B"));

		JsonObject evil = Json.parseObject("{\"card\":{\"uuid\":\"%s\"},\"command\":\"/pay attacker 1000000\"}".formatted(ID));
		assertFalse(FlipEvent.parse(evil).commandMatches());
		assertNull(FlipEvent.parse(Json.parseObject("{\"card\":{\"uuid\":\"not-an-id\"}}")));
		assertNull(FlipEvent.parse(Json.parseObject("{\"nocard\":1}")));
		assertNull(FlipEvent.parse(null));
	}

	@Test
	public void timing() {
		Timing.Cooldown c = new Timing.Cooldown(500);
		assertTrue(c.tryAcquire(1000));
		assertFalse(c.tryAcquire(1200));
		assertTrue(c.tryAcquire(1500));
		Timing.Backoff b = new Timing.Backoff();
		assertEquals(1000, b.nextDelayMs());
		assertEquals(2000, b.nextDelayMs());
		assertEquals(5000, b.nextDelayMs());
		assertEquals(10000, b.nextDelayMs());
		assertEquals(30000, b.nextDelayMs());
		assertEquals(30000, b.nextDelayMs());
		b.reset();
		assertEquals(1000, b.nextDelayMs());
		Timing.RateLimiter r = new Timing.RateLimiter(2, 2, 0);
		assertTrue(r.tryAcquire(0));
		assertTrue(r.tryAcquire(0));
		assertFalse(r.tryAcquire(0));
		assertTrue(r.tryAcquire(500));
	}

	@Test
	public void retryAfter() {
		assertEquals(30_000, ViewerApi.parseRetryAfter(null));
		assertEquals(5_000, ViewerApi.parseRetryAfter("5"));
		assertEquals(600_000, ViewerApi.parseRetryAfter("99999"));
		assertEquals(30_000, ViewerApi.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"));
		assertTrue(ViewerApi.forbiddenMessage(Json.parseObject("{\"private_only\":true}"), null).contains("private"));
	}

	@Test
	public void priceCache() {
		AtomicLong now = new AtomicLong(0);
		AtomicInteger calls = new AtomicInteger();
		CompletableFuture<ViewerApi.Result> pending = new CompletableFuture<>();
		PriceCache cache = new PriceCache(k -> {
			calls.incrementAndGet();
			return pending;
		}, now::get, 4);
		PriceCache.Entry e = cache.get("a");
		assertEquals(PriceCache.State.LOADING, e.state());
		assertEquals(PriceCache.State.LOADING, cache.get("a").state());
		assertEquals(1, calls.get()); // one request in flight per item
		pending.complete(new ViewerApi.Result(ViewerApi.Status.OK, 200, Json.parseObject("{\"found\":true,\"market\":5}"), null, 0));
		assertEquals(PriceCache.State.FOUND, cache.get("a").state());
		now.set(59_000);
		cache.get("a");
		assertEquals(1, calls.get());
		now.set(61_000);
		cache.get("a"); // expired: refreshed
		assertEquals(2, calls.get());
	}

	@Test
	public void priceCachePerRequestAndCodes() {
		AtomicLong now = new AtomicLong(0);
		PriceCache cache = new PriceCache(k -> { throw new AssertionError("default fetch must not run"); }, now::get, 4);
		PriceCache.Entry e = cache.get("b:x", () -> CompletableFuture.completedFuture(
				new ViewerApi.Result(ViewerApi.Status.ERROR, 405, null, "no", 0)));
		assertEquals(PriceCache.State.FAILED, e.state());
		assertEquals(405, e.httpCode());
		PriceCache.Entry n = cache.get("b:y", () -> null);
		assertEquals(PriceCache.State.FAILED, n.state());
	}

	@Test
	public void priceCacheRateLimit() {
		AtomicLong now = new AtomicLong(0);
		AtomicInteger calls = new AtomicInteger();
		PriceCache cache = new PriceCache(k -> {
			calls.incrementAndGet();
			return new CompletableFuture<>();
		}, now::get, 2);
		for (int i = 0; i < 20; i++) cache.get("k" + i);
		assertEquals(4, calls.get()); // burst of 2 × perSecond
		now.set(1_000);
		for (int i = 20; i < 40; i++) cache.get("k" + i);
		assertEquals(6, calls.get());
	}

	@Test
	public void configRoundTrip() throws Exception {
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ffcfg");
		java.nio.file.Path f = dir.resolve("fatesflipper.json");
		ModConfig c = ModConfig.load(f);
		assertFalse(c.cursorAssist);
		assertTrue(c.alerts);
		assertFalse(c.isReady());
		c.serverUrl = "https://flips.example.com";
		c.token = "fv_abcdefghijkl";
		c.save();
		ModConfig d = ModConfig.load(f);
		assertTrue(d.isReady());
		assertEquals("fv_abcdefghijkl", d.token);
		assertFalse(ModConfig.maskToken(d.token).contains("ghijkl"));
		java.nio.file.Files.writeString(f, "{not json");
		assertFalse(ModConfig.load(f).isReady()); // broken file falls back to defaults
	}

	@Test
	public void auctionWindowButtons() {
		// as seen in game on 26.3 (2026-10-17)
		assertEquals(AuctionWindow.ButtonState.BUYABLE, AuctionWindow.classify("Buy Item Right Now",
				List.of("", "Price: 499,999 coins", "", "Click to purchase!")));
		assertEquals(AuctionWindow.ButtonState.CANT_AFFORD, AuctionWindow.classify("Buy Item Right Now",
				List.of("", "Price: 5,454,545,454 coins", "", "Cannot afford bid!")));
		assertEquals(AuctionWindow.ButtonState.SOLD, AuctionWindow.classify("Collect Auction",
				List.of("", "Someone else purchased the item!")));
		assertEquals(AuctionWindow.ButtonState.SOLD, AuctionWindow.classify("§6Collect Auction", List.of()));
		assertEquals(AuctionWindow.ButtonState.NOT_YET, AuctionWindow.classify("Buy Item Right Now", List.of("Price: 10 coins", "Can be bought in 5s")));
		assertEquals(AuctionWindow.ButtonState.UNKNOWN, AuctionWindow.classify("Something New", List.of()));
		assertTrue(AuctionWindow.isButtonName("Buy Item Right Now"));
		assertTrue(AuctionWindow.isButtonName("Collect Auction"));
		assertFalse(AuctionWindow.isButtonName("Close"));
	}

	@Test
	public void auctionWindowPriceAndMatch() {
		assertEquals(499_999L, AuctionWindow.price(List.of("", "Price: 499,999 coins", "Click to purchase!")));
		assertEquals(5_454_545_454L, AuctionWindow.price(List.of("Price: 5,454,545,454 coins")));
		assertEquals(-1L, AuctionWindow.price(List.of("Someone else purchased the item!")));
		assertEquals(-1L, AuctionWindow.price(null));
		// the price decides when it's on the button
		assertEquals(Boolean.TRUE, AuctionWindow.matches(499_999, "Candy the Fish", 499_999, "Candy the Fish"));
		assertEquals(Boolean.FALSE, AuctionWindow.matches(1_450_000, "Water Hydra Head", 5_454_545_454L, "Water Hydra Head"));
		// sold: no price on the button, so the item's name decides
		assertEquals(Boolean.TRUE, AuctionWindow.matches(300_000, "Candy the Fish", -1, "Candy the Fish"));
		assertEquals(Boolean.TRUE, AuctionWindow.matches(1e9, "Heroic Hyperion", -1, "§d✪✪✪ Heroic Hyperion §6✪"));
		assertEquals(Boolean.FALSE, AuctionWindow.matches(1e6, "Spooky Hook", -1, "Titanium Cloak"));
		// not filled in yet
		assertNull(AuctionWindow.matches(1e6, "Spooky Hook", -1, ""));
		assertEquals("heroic hyperion", AuctionWindow.normalize("✪✪ Heroic  Hyperion ✪"));
		// same price, different item (audit F-R3): not the flip
		assertEquals(Boolean.FALSE, AuctionWindow.matches(100_000, "Spooky Hook", 100_000, "Titanium Cloak"));
		// same price, item slot still empty: price alone decides
		assertEquals(Boolean.TRUE, AuctionWindow.matches(100_000, "Spooky Hook", 100_000, ""));
	}
	@Test
	public void loreListingFooter() {
		List<String> scroll = List.of("Consume this item to add its", "destination to your Fast Travel", "options.", "",
				"Requires MVP+ to consume!", "", "Island: Spider's Den", "Teleport: Top of Nest", "", "EPIC TRAVEL SCROLL",
				"-----------------", "Seller: [MVP+] Invisity_", "Buy it now: 1,000 coins", "", "Ends in: 05h 49m 49s", "",
				"Click to inspect!");
		assertEquals(10, LoreParts.listingStart(scroll));
		// the countdown ticking doesn't change where the item's own lore ends
		List<String> later = new java.util.ArrayList<>(scroll);
		later.set(14, "Ends in: 05h 49m 23s");
		assertEquals(scroll.subList(0, 10), later.subList(0, LoreParts.listingStart(later)));
		// auctions with bids, no separator: cut at the blank line above "Seller:"
		assertEquals(1, LoreParts.listingStart(List.of("RARE SWORD", "", "Seller: x", "Starting bid: 5 coins", "Bids: 3 bids")));
		// ordinary items keep everything, including progress bars and stat lines
		assertEquals(-1, LoreParts.listingStart(List.of("Damage: +270", "━━━━━━━━━━━━ 42%", "", "LEGENDARY SWORD")));
		assertEquals(-1, LoreParts.listingStart(List.of()));
		assertTrue(LoreParts.isSeparator("-----------------"));
		assertFalse(LoreParts.isSeparator("━━━━━━━━ 42.42%"));
	}

	@Test
	public void bazaarProducts() throws Exception {
		assertTrue(LoreParts.isBazaarProduct(List.of("Common commodity", "", "Buy price: 2.5 coins")));
		assertTrue(LoreParts.isBazaarProduct(List.of("Buy price: 2.5 coins", "Sell price: 1.2 coins")));
		assertFalse(LoreParts.isBazaarProduct(List.of("Buy it now: 320 coins", "COMMON")));
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ffbz");
		java.nio.file.Path f = dir.resolve("bz.txt");
		BazaarIds ids = new BazaarIds(f);
		assertTrue(ids.learn("SEEDS"));
		assertFalse(ids.learn("SEEDS"));
		assertFalse(ids.learn("ENCHANTED_BOOK")); // generic id: AH-only books share it
		assertFalse(ids.learn("bad id; /pay"));
		ids.saveIfDirty();
		BazaarIds again = new BazaarIds(f);
		assertTrue(again.contains("SEEDS"));
		assertEquals(1, again.size());
	}

	@Test
	public void coinsRoundBeforeUnit() {
		assertEquals("1M", Coins.format(999_999.5));
		assertEquals("1M", Coins.format(999_950));
		assertEquals("1k", Coins.format(999.6));
		assertEquals("999", Coins.format(999.4));
		assertEquals("1B", Coins.format(999_999_999));
		assertEquals("1.5M", Coins.format(1_500_000));
		assertEquals("-2.5k", Coins.format(-2_500));
	}

	@Test
	public void versions() {
		assertTrue(Versions.compare("0.3.3", "0.3.4") < 0);
		assertTrue(Versions.compare("0.3.10", "0.3.4") > 0);
		assertTrue(Versions.compare("0.3.4-beta.2", "0.3.4") < 0);
		assertTrue(Versions.compare("0.3.4-beta.2", "0.3.4-beta.10") < 0);
		assertEquals(0, Versions.compare("0.3.4", "v0.3.4"));
		assertEquals(0, Versions.compare("garbage", "0.3.4")); // never blocks on an unreadable value
		assertEquals("0.3.4", Versions.plain("0.3.4+mc26.3"));
		assertEquals("https://modrinth.com/mod/x", Versions.safeUrl("https://modrinth.com/mod/x"));
		assertNull(Versions.safeUrl("http://evil.example"));
		assertNull(Versions.safeUrl("https://a b"));
	}

	@Test
	public void eventIdsAndTokenFile() throws Exception {
		assertEquals("123", FlipStream.safeEventId("123"));
		assertNull(FlipStream.safeEventId("bad id"));
		assertNull(FlipStream.safeEventId("x\r\nInjected: 1"));
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ffcfg");
		java.nio.file.Path f = dir.resolve("fatesflipper.json");
		// 0.3.3 kept the token in the settings file: moved to its own file on load
		java.nio.file.Files.writeString(f, "{\"serverUrl\":\"https://flips.example.com\",\"token\":\"fv_secret123\"}");
		ModConfig c = ModConfig.load(f);
		assertEquals("fv_secret123", c.token);
		assertFalse(java.nio.file.Files.readString(f).contains("fv_secret123"));
		assertTrue(java.nio.file.Files.readString(dir.resolve("fatesflipper-token.txt")).contains("fv_secret123"));
		assertEquals("fv_secret123", ModConfig.load(f).token);
		assertEquals("fv_secret123", c.copy().token);
	}

	@Test
	public void privateHosts() {
		for (String h : new String[] {"http://10.0.0.5:8738", "http://172.16.1.2:8738", "http://192.168.1.20:8738", "http://[fd7a:115c:a1e0::1]:8738"}) {
			assertTrue(h, ServerAddress.check(h).ok());
		}
		assertFalse(ServerAddress.check("http://172.32.0.1:8738").ok());
		assertFalse(ServerAddress.check("http://8.8.8.8").ok());
		assertTrue(ServerAddress.isTokenCommand("/ff token fv_abc"));
		assertTrue(ServerAddress.isTokenCommand("ff TOKEN fv_abc"));
		assertFalse(ServerAddress.isTokenCommand("/ff status"));
	}

	@Test
	public void everyTextKeyExists() throws Exception {
		// every "fatesflipper.x.y" key in the code has English text (audit B-6)
		java.util.regex.Pattern key = java.util.regex.Pattern.compile("\"(fatesflipper\\.[a-z0-9_]+\\.[a-z0-9_.]*[a-z0-9_])\"");
		java.util.List<String> missing = new java.util.ArrayList<>();
		try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java"))) {
			for (java.nio.file.Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
				var m = key.matcher(java.nio.file.Files.readString(f));
				while (m.find()) if (!Tr.has(m.group(1))) missing.add(f.getFileName() + ": " + m.group(1));
			}
		}
		assertTrue("keys without text: " + missing, missing.isEmpty());
		assertEquals("Server set to x", Tr.tr("fatesflipper.cmd.server.set", "x"));
		assertEquals("no.such.key", Tr.tr("no.such.key"));
	}
}
