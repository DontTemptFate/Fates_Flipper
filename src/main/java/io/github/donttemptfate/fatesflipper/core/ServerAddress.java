package io.github.donttemptfate.fatesflipper.core;

import java.net.URI;
import java.util.Locale;

/**
 * Checks the viewer address the player typed in. The mod token is sent to this address on every
 * request, so plain {@code http://} is only allowed to this machine and to Tailscale addresses.
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
		if (input == null || input.isBlank()) return new Check(null, "No server address set. Use /ff config or /ff server <address>.");
		String s = input.trim();
		if (!s.contains("://")) s = "https://" + s;
		while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
		URI uri;
		try {
			uri = new URI(s);
		} catch (Exception e) {
			return new Check(null, "That isn't a valid address.");
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		String host = uri.getHost();
		if (host == null || host.isBlank()) return new Check(null, "That address has no host name.");
		if (uri.getRawUserInfo() != null) return new Check(null, "Addresses with a user name or password aren't allowed.");
		if (uri.getRawQuery() != null || uri.getRawFragment() != null) return new Check(null, "Leave out ? and # parts of the address.");
		String path = uri.getRawPath() == null ? "" : uri.getRawPath();
		if (!path.isEmpty() && !path.equals("/")) return new Check(null, "Use just the address, without a path (e.g. https://flips.example.com).");
		switch (scheme) {
			case "https" -> {
				// fine anywhere
			}
			case "http" -> {
				if (!isPrivateHost(host)) {
					return new Check(null, "Plain http:// would send your mod token unencrypted. Use https://, or http:// only for this computer or a Tailscale address.");
				}
			}
			default -> {
				return new Check(null, "The address must start with https://.");
			}
		}
		String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
		String hostPart = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
		return new Check(scheme + "://" + hostPart.toLowerCase(Locale.ROOT) + port, null);
	}

	/** This computer, or Tailscale's 100.64.0.0/10 range. */
	static boolean isPrivateHost(String host) {
		String h = host.toLowerCase(Locale.ROOT);
		if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
		if (h.equals("localhost") || h.equals("::1")) return true;
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
		if (n[0] == 127) return true;
		return n[0] == 100 && n[1] >= 64 && n[1] <= 127;
	}

	/** True when the token looks like a viewer mod token. */
	public static boolean looksLikeToken(String token) {
		return token != null && token.matches("^fv_[A-Za-z0-9_\\-]{8,200}$");
	}
}
