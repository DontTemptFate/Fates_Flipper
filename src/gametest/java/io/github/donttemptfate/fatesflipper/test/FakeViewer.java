package io.github.donttemptfate.fatesflipper.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** A tiny stand-in for Fates Viewer's mod service, for the in-game test only. */
final class FakeViewer {
	static final String TOKEN = "fv_gametest_token_123";
	static final String FLIP_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
	static final String NEXT_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
	static final String EVIL_ID = "cccccccccccccccccccccccccccccccc";
	static final String ITEM_UUID = "361de2df-1769-4052-9795-fb21a1d0f344";
	/** Has a uuid but never traded: the mod must then send the whole item. */
	static final String NEVER_UUID = "11111111-2222-3333-4444-555555555555";
	/** Too few sales: the viewer answers learning + a rough value. */
	static final String ROUGH_UUID = "99999999-8888-7777-6666-555555555555";

	/** The last item the mod sent whole (decoded), for the test to inspect. */
	final java.util.concurrent.atomic.AtomicReference<net.minecraft.nbt.CompoundTag> lastItem = new java.util.concurrent.atomic.AtomicReference<>();
	final AtomicInteger itemPosts = new AtomicInteger();

	final AtomicInteger unauthorized = new AtomicInteger();
	final AtomicInteger priceCalls = new AtomicInteger();
	private final HttpServer server;

	private FakeViewer(HttpServer server) {
		this.server = server;
	}

	static FakeViewer start() throws IOException {
		HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		s.setExecutor(Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "fake-viewer");
			t.setDaemon(true);
			return t;
		}));
		FakeViewer v = new FakeViewer(s);
		s.createContext("/", v::handle);
		s.start();
		return v;
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	void stop() {
		server.stop(0);
	}

	static String card(String id, String name, long price, long profit) {
		return "{\"uuid\":\"" + id + "\",\"name\":\"" + name + "\",\"rarity\":\"LEGENDARY\",\"price\":" + price
				+ ",\"profit\":" + (profit + profit / 2) + ",\"safe_profit\":" + profit + ",\"safe_pct\":0.08,\"safe_resale\":" + (price + profit * 2)
				+ ",\"dupe\":null,\"first_seen_at\":" + (System.currentTimeMillis() - 4_000)
				+ ",\"summary\":\"5★ · Wisdom V\",\"estimate\":{\"confidence\":\"HIGH\",\"resale\":" + (price + profit * 2)
				+ ",\"volume_per_day\":14.2,\"exact\":{\"n_used\":23}}}";
	}

	static String flip(String id, String name, long price, long profit, String command) {
		return "{\"card\":" + card(id, name, price, profit) + ",\"score\":{\"exp\":" + profit + ",\"chance\":0.82,\"target\":100000,\"n\":40,\"basis\":\"HIGH\"},"
				+ "\"chat\":\"§6§lFLIP §r§d" + name + " §7for §e" + (price / 1_000_000) + "M §7→ §a+" + (profit / 1_000_000) + "M §8HIGH\","
				+ "\"command\":\"" + command + "\",\"profile\":{\"id\":1,\"name\":\"Test profile\"}}";
	}

	private void handle(HttpExchange ex) throws IOException {
		String path = ex.getRequestURI().getPath();
		String query = ex.getRequestURI().getQuery() == null ? "" : ex.getRequestURI().getQuery();
		if (!("Bearer " + TOKEN).equals(ex.getRequestHeaders().getFirst("Authorization"))) {
			unauthorized.incrementAndGet();
			json(ex, 401, "{\"error\":\"log in first\",\"login\":true}");
			return;
		}
		switch (path) {
			case "/mod/ping" -> json(ex, 200, "{\"ok\":true,\"version\":\"0.19.0-test\",\"fp_version\":4,\"collector_ok\":true,\"latest_card_at\":0,\"live_feed\":true,\"profile\":{\"id\":1,\"name\":\"Test profile\"}}");
			case "/mod/profiles" -> json(ex, 200, "{\"active\":1,\"profiles\":[{\"id\":1,\"name\":\"Test profile\",\"color\":\"#ffaa00\"}]}");
			case "/api/profiles" -> json(ex, 200, "{\"active\":1,\"profiles\":[{\"id\":1,\"name\":\"Test profile\",\"settings\":{\"chat\":{\"sound\":true}}}]}");
			case "/mod/profile" -> json(ex, 200, "{\"ok\":true,\"profile\":{\"id\":1,\"name\":\"Test profile\"}}");
			case "/mod/next" -> json(ex, 200, "{\"mode\":\"best\",\"waiting\":0,\"flip\":" + flip(NEXT_ID, "Juju Shortbow", 30_000_000, 3_000_000, "/viewauction " + NEXT_ID) + "}");
			case "/mod/flips" -> stream(ex);
			case "/mod/price" -> {
				priceCalls.incrementAndGet();
				if ("POST".equals(ex.getRequestMethod())) {
					itemPosts.incrementAndGet();
					String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
					try {
						String b64 = com.google.gson.JsonParser.parseString(body).getAsJsonObject().get("item_bytes").getAsString();
						byte[] gz = java.util.Base64.getDecoder().decode(b64);
						lastItem.set(net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(gz), net.minecraft.nbt.NbtAccounter.unlimitedHeap()));
					} catch (Exception e) {
						json(ex, 400, "{\"error\":\"bad item_bytes: " + e.getClass().getSimpleName() + "\"}");
						return;
					}
					json(ex, 200, "{\"found\":true,\"by\":\"item_bytes\",\"confidence\":\"MEDIUM\",\"learning\":false,\"market\":77000000,\"cautious\":70000000,\"lowest_bin\":79000000,\"volume_per_day\":2.0,\"sales_used\":6,\"dupe\":null}");
					return;
				}
				if (query.contains(NEVER_UUID.replace("-", ""))) {
					json(ex, 200, "{\"found\":false,\"reason\":\"never traded\",\"collector_since\":0}");
					return;
				}
				if (query.contains(ROUGH_UUID.replace("-", ""))) {
					json(ex, 200, "{\"found\":true,\"confidence\":\"LEARNING\",\"learning\":true,\"rough\":{\"price\":5000000,\"confidence\":\"ROUGH\",\"display_only\":true}}");
					return;
				}
				json(ex, 200, "{\"found\":true,\"by\":\"" + (query.startsWith("auction") ? "auction" : "item_uuid") + "\",\"item_id\":\"HYPERION\",\"name\":\"Heroic Hyperion\","
						+ "\"confidence\":\"HIGH\",\"learning\":false,\"market\":1250000000,\"resale\":1240000000,\"cautious\":1190000000,"
						+ "\"volume_per_day\":4.5,\"sales_used\":23,\"lowest_bin\":1230000000,\"listed_now\":7,\"last_traded_at\":" + (System.currentTimeMillis() - 3_600_000)
						+ ",\"dupe\":null,\"recent_sales\":["
						+ "{\"price\":1240000000,\"ts\":" + (System.currentTimeMillis() - 600_000) + ",\"bin\":true,\"ignored\":false},"
						+ "{\"price\":1215000000,\"ts\":" + (System.currentTimeMillis() - 3_000_000) + ",\"bin\":true,\"ignored\":false},"
						+ "{\"price\":640000000,\"ts\":" + (System.currentTimeMillis() - 5_000_000) + ",\"bin\":true,\"ignored\":true}]}");
			}
			default -> {
				if (path.startsWith("/mod/auction/")) json(ex, 200, "{\"state\":\"active\",\"price\":30000000,\"seen_at\":" + System.currentTimeMillis() + "}");
				else json(ex, 404, "{\"error\":\"not found\"}");
			}
		}
	}

	private static void stream(HttpExchange ex) throws IOException {
		ex.getResponseHeaders().add("Content-Type", "text/event-stream");
		ex.sendResponseHeaders(200, 0);
		try (OutputStream out = ex.getResponseBody()) {
			write(out, "event: profile\ndata: {\"profile\":{\"id\":1,\"name\":\"Test profile\"}}\n\n");
			Thread.sleep(500);
			write(out, "event: flip\ndata: " + flip(FLIP_ID, "Heroic Hyperion", 1_150_000_000L, 42_000_000L, "/viewauction " + FLIP_ID) + "\n\n");
			Thread.sleep(300);
			// a flip whose command isn't /viewauction <id>: the mod must ignore it
			write(out, "event: flip\ndata: " + flip(EVIL_ID, "Evil Item", 1_000_000L, 500_000L, "/pay attacker 1000000") + "\n\n");
			for (int i = 0; i < 90; i++) {
				Thread.sleep(2_000);
				write(out, ": keep-alive\n\n");
			}
		} catch (InterruptedException | IOException ignored) {
			// client went away
		}
	}

	private static void write(OutputStream out, String s) throws IOException {
		out.write(s.getBytes(StandardCharsets.UTF_8));
		out.flush();
	}

	private static void json(HttpExchange ex, int code, String body) throws IOException {
		byte[] b = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json");
		ex.sendResponseHeaders(code, b.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(b);
		}
	}
}
