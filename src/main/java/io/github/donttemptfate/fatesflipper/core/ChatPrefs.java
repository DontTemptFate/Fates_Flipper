package io.github.donttemptfate.fatesflipper.core;

import com.google.gson.JsonObject;

/**
 * The active flip profile's chat settings (viewer {@code settings.chat}, sent on {@code /mod/profiles}
 * since viewer v0.30.0). A missing setting keeps the mod's plain behaviour.
 *
 * @param sound        ding on each alert (also needs the mod's own "Alert sound")
 * @param countdown    add how long ago the auction was listed to the chat line, e.g. {@code (12s)}
 * @param sellerButton add a {@code [SELLER]} button after the line that puts {@code /ah <seller>} in the
 *                     chat box (you press Enter; never sent by the mod). Needs the card's seller name
 * @param loreOnHover  the alert's hover card also shows the item's lore. Needs the card's lore
 * @param justProfit   a short chat line: item name and cautious profit only
 */
public record ChatPrefs(boolean sound, boolean countdown, boolean sellerButton, boolean loreOnHover, boolean justProfit) {
	public static final ChatPrefs DEFAULT = new ChatPrefs(true, false, false, false, false);

	public static ChatPrefs of(JsonObject chat) {
		if (chat == null) return DEFAULT;
		return new ChatPrefs(
				Json.bool(chat, "sound", true),
				Json.bool(chat, "countdown", false),
				Json.bool(chat, "seller_button", false),
				Json.bool(chat, "lore_on_hover", false),
				Json.bool(chat, "just_profit", false));
	}
}
