package io.github.donttemptfate.fatesflipper;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/** /ff verify (Fate's condition: auditable; it only ever sends a name and a one-use code). */
public class VerifyTest {
	@Test
	public void bodyHasExactlyNameAndServerId() {
		JsonObject b = Verify.body("Fate_123", "0123456789abcdef0123456789abcdef01234567");
		assertEquals(Set.of("name", "server_id"), b.keySet());
		assertEquals("Fate_123", b.get("name").getAsString());
		assertEquals("0123456789abcdef0123456789abcdef01234567", b.get("server_id").getAsString());
	}

	@Test
	public void codesAndNamesAreChecked() {
		assertTrue(Verify.SERVER_ID.matcher("0123456789abcdef0123456789abcdef01234567").matches());
		assertTrue(Verify.SERVER_ID.matcher("-5a1b").matches());
		assertFalse(Verify.SERVER_ID.matcher("abc def").matches());
		assertFalse(Verify.SERVER_ID.matcher("abc/../x").matches());
		assertFalse(Verify.SERVER_ID.matcher("x".repeat(65)).matches());
		assertTrue(Verify.NAME.matcher("Fate_123").matches());
		assertFalse(Verify.NAME.matcher("bad name").matches());
	}

	/** The access token is touched in exactly one place in the whole mod: Verify's call into Minecraft. */
	@Test
	public void accessTokenOnlyInVerify() throws Exception {
		Path root = Path.of("src/main/java");
		if (!Files.isDirectory(root)) return;
		try (Stream<Path> files = Files.walk(root)) {
			long others = files.filter(p -> p.toString().endsWith(".java") && !p.endsWith("Verify.java"))
					.filter(p -> {
						try {
							String s = Files.readString(p);
							return s.contains("getAccessToken") || s.contains("joinServer");
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}).count();
			assertEquals(0, others);
		}
		String v = Files.readString(root.resolve("io/github/donttemptfate/fatesflipper/Verify.java"));
		assertEquals(1, v.split("getAccessToken\\(\\)", -1).length - 1);
	}
}
