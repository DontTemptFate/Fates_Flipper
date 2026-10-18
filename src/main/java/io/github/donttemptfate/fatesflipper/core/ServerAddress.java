package io.github.donttemptfate.fatesflipper.core;

import java.net.URI;
import java.util.Locale;

/**
 * Checks the viewer address the player typed in. The mod token is sent to this address on every
 * request, so plain {@code http://} is only allowed to this machine, private (LAN) networks and Tailscale.
 */
public final class ServerAddress {
	private ServerAddress() {}

	/** Result of checking an address: the cleaned base URL, or a reason it can't be used. */
	public record Check(String baseUrl, String problem) {
		public boolean ok() {
			return problem == null;
		}
	}

	public static Check check(String input) {
		if (input == null || input.isBlank()) return new Check(null, Tr.tr("fatesflipper.addr.none"));
		String s = input.trim();
		if (!s.contains("://")) s = "https://" + s;
		while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
		URI uri;
		try {
			uri = new URI(s);
		} catch (Exception e) {
			return new Check(null, Tr.tr("fatesflipper.addr.invalid"));
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		String host = uri.getHost();
		if (host == null || host.isBlank()) return new Check(null, Tr.tr("fatesflipper.addr.no_host"));
		if (uri.getRawUserInfo() != null) return new Check(null, Tr.tr("fatesflipper.addr.userinfo"));
		if (uri.getRawQuery() != null || uri.getRawFragment() != null) return new Check(null, Tr.tr("fatesflipper.addr.query"));
		String path = uri.getRawPath() == null ? "" : uri.getRawPath();
		if (!path.isEmpty() && !path.equals("/")) return new Check(null, Tr.tr("fatesflipper.addr.path"));
		switch (scheme) {
			case "https" -> {
				// fine anywhere
			}
			case "http" -> {
				if (!isPrivateHost(host)) {
					return new Check(null, Tr.tr("fatesflipper.addr.http"));
				}
			}
			default -> {
				return new Check(null, Tr.tr("fatesflipper.addr.https"));
			}
		}
		String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
		String hostPart = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
		return new Check(scheme + "://" + hostPart.toLowerCase(Locale.ROOT) + port, null);
	}

	/**
	 * This computer, a home / LAN network (10/8, 172.16/12, 192.168/16; audit F-S3), or Tailscale
	 * (100.64.0.0/10 and fd7a:115c:a1e0::/48).
	 */
	static boolean isPrivateHost(String host) {
		String h = host.toLowerCase(Locale.ROOT);
		if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
		if (h.equals("localhost") || h.equals("::1")) return true;
		if (h.startsWith("fd7a:115c:a1e0:")) return true;
		if (h.endsWith(".")) return false;
		String[] parts = h.split("\\.");
		if (parts.length != 4) return false;
		int[] n = new int[4];
		for (int i = 0; i < 4; i++) {
			try {
				n[i] = Integer.parseInt(parts[i]);
			} catch (NumberFormatException e) {
				return false;
			}
			if (n[i] < 0 || n[i] > 255) return false;
		}
		if (n[0] == 127 || n[0] == 10) return true;
		if (n[0] == 172 && n[1] >= 16 && n[1] <= 31) return true;
		if (n[0] == 192 && n[1] == 168) return true;
		return n[0] == 100 && n[1] >= 64 && n[1] <= 127;
	}

	/** {@code /ff token …}: kept out of chat and command history (audit F-S2). */
	public static boolean isTokenCommand(String s) {
		if (s == null) return false;
		String t = s.trim().toLowerCase(Locale.ROOT);
		if (t.startsWith("/")) t = t.substring(1);
		return t.startsWith("ff token ") || t.equals("ff token");
	}

	/** True when the token looks like a viewer mod token. */
	public static boolean looksLikeToken(String token) {
		return token != null && token.matches("^fv_[A-Za-z0-9_\\-]{8,200}$");
	}
}
