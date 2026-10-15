package io.github.donttemptfate.fatesflipper.core;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Runs the flip stream and the API against a tiny fake viewer on 127.0.0.1. */
public class FlipStreamTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";
	private HttpServer server;
	private FlipStream stream;

	@After
	public void tearDown() {
		if (stream != null) stream.shutdown();
		if (server != null) server.stop(0);
	}

	private ModConfig config(int port, String token) {
		ModConfig c = new ModConfig();
		c.serverUrl = "http://127.0.0.1:" + port;
		c.token = token;
		return c;
	}

	@Test
	public void receivesFlipsAndProfile() throws Exception {
		AtomicReference<String> auth = new AtomicReference<>();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/mod/flips", ex -> {
			auth.set(ex.getRequestHeaders().getFirst("Authorization"));
			ex.getResponseHeaders().add("Content-Type", "text/event-stream");
			ex.sendResponseHeaders(200, 0);
			OutputStream out = ex.getResponseBody();
			String body = ": keep-alive\n\n"
					+ "event: profile\ndata: {\"profile\":{\"id\":1,\"name\":\"Main\"}}\n\n"
					+ "event: flip\ndata: {\"card\":{\"uuid\":\"" + ID + "\",\"name\":\"X\",\"price\":5}}\n\n";
			out.write(body.getBytes(StandardCharsets.UTF_8));
			out.flush();
			try {
				Thread.sleep(3_000);
			} catch (InterruptedException ignored) {
			}
			out.close();
		});
		server.start();
		int port = server.getAddress().getPort();
		ModConfig cfg = config(port, "fv_testtoken123");
		ViewerApi api = new ViewerApi(() -> cfg, "test");
		List<JsonObject> flips = new CopyOnWriteArrayList<>();
		List<JsonObject> profiles = new CopyOnWriteArrayList<>();
		CountDownLatch got = new CountDownLatch(2);
		stream = new FlipStream(api, new FlipStream.Listener() {
			public void onState(FlipStream.State s, String d) {}

			public void onFlip(JsonObject f) {
				flips.add(f);
				got.countDown();
			}

			public void onProfile(JsonObject p) {
				profiles.add(p);
				got.countDown();
			}
		}, () -> true);
		stream.start();
		assertTrue(got.await(10, TimeUnit.SECONDS));
		assertEquals("Bearer fv_testtoken123", auth.get());
		assertEquals(ID, FlipEvent.parse(flips.get(0)).auctionId());
		assertEquals("Main", Json.str(Json.obj(profiles.get(0), "profile"), "name"));
	}

	@Test
	public void stopsOn401UntilRestart() throws Exception {
		AtomicInteger hits = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/mod/flips", ex -> {
			hits.incrementAndGet();
			byte[] b = "{\"error\":\"log in first\",\"login\":true}".getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(401, b.length);
			ex.getResponseBody().write(b);
			ex.close();
		});
		server.start();
		ModConfig cfg = config(server.getAddress().getPort(), "fv_revokedtoken1");
		ViewerApi api = new ViewerApi(() -> cfg, "test");
		CountDownLatch stopped = new CountDownLatch(1);
		stream = new FlipStream(api, new FlipStream.Listener() {
			public void onState(FlipStream.State s, String d) {
				if (s == FlipStream.State.STOPPED) stopped.countDown();
			}

			public void onFlip(JsonObject f) {}

			public void onProfile(JsonObject p) {}
		}, () -> true);
		stream.start();
		assertTrue(stopped.await(10, TimeUnit.SECONDS));
		Thread.sleep(6_000);
		assertEquals("no retries after 401", 1, hits.get());
		assertTrue(stream.detail().contains("token"));
		stream.restart();
		Thread.sleep(1_500);
		assertEquals(2, hits.get());
	}

	@Test
	public void waitsWhenNotAllowedOrNotConfigured() throws Exception {
		ModConfig cfg = new ModConfig();
		ViewerApi api = new ViewerApi(() -> cfg, "test");
		AtomicReference<FlipStream.State> last = new AtomicReference<>();
		stream = new FlipStream(api, new FlipStream.Listener() {
			public void onState(FlipStream.State s, String d) {
				last.set(s);
			}

			public void onFlip(JsonObject f) {}

			public void onProfile(JsonObject p) {}
		}, () -> true);
		stream.start();
		Thread.sleep(500);
		assertEquals(FlipStream.State.WAITING, last.get());
	}

	@Test
	public void apiRateLimitPausesRequests() throws Exception {
		AtomicInteger hits = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/mod/ping", ex -> {
			hits.incrementAndGet();
			ex.getResponseHeaders().add("Retry-After", "7");
			ex.sendResponseHeaders(429, -1);
			ex.close();
		});
		server.start();
		ModConfig cfg = config(server.getAddress().getPort(), "fv_testtoken123");
		ViewerApi api = new ViewerApi(() -> cfg, "test");
		ViewerApi.Result r = api.ping().get(10, TimeUnit.SECONDS);
		assertEquals(ViewerApi.Status.RATE_LIMITED, r.status());
		assertEquals(7_000, r.retryAfterMs());
		ViewerApi.Result r2 = api.ping().get(10, TimeUnit.SECONDS);
		assertEquals(ViewerApi.Status.RATE_LIMITED, r2.status());
		assertEquals("second call must not reach the server", 1, hits.get());
	}

	@Test
	public void apiRefusesPlainHttpToPublicHosts() throws Exception {
		ModConfig cfg = new ModConfig();
		cfg.serverUrl = "http://flips.example.com";
		cfg.token = "fv_testtoken123";
		ViewerApi api = new ViewerApi(() -> cfg, "test");
		assertEquals(ViewerApi.Status.NOT_CONFIGURED, api.ping().get(5, TimeUnit.SECONDS).status());
	}

	@Test
	public void apiNeverFollowsRedirects() throws Exception {
		AtomicInteger other = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/mod/ping", ex -> {
			ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/steal");
			ex.sendResponseHeaders(302, -1);
			ex.close();
		});
		server.createContext("/steal", ex -> {
			other.incrementAndGet();
			ex.sendResponseHeaders(200, -1);
			ex.close();
		});
		server.start();
		ModConfig cfg = config(server.getAddress().getPort(), "fv_testtoken123");
		ViewerApi api = new ViewerApi(() -> cfg, "test");
		ViewerApi.Result r = api.ping().get(10, TimeUnit.SECONDS);
		assertEquals(ViewerApi.Status.ERROR, r.status());
		assertEquals(0, other.get());
	}
}
