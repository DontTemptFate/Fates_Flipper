# Changelog

Player-facing changes per release. The Release workflow copies the section for the version being
released into the GitHub Release (and Modrinth); with no section it falls back to the commit list.
Versions ending in `-beta.N` are pre-releases.

## 0.4.0-beta.1

First public test build.

- **Themes:** four colour themes (Fates, Midnight, Ember, Vanilla) in settings or `/ff theme`. The
  theme and the Chakra Petch font are shared with Fates OFA: change them in either mod.
- **Profile chat settings:** your flip profile's chat options on the website now work in game:
  countdown (how long ago it was listed), a `[SELLER]` button (puts `/ah <seller>` in your chat box),
  the item's lore on hover, and short "just profit" lines. Seller and lore show once the website
  sends them.
- **DUPED?!** in bold red on the chat line, hover card, auction panel and item tooltip.
- **`/ff verify`** (optional): links this Minecraft account to your website account for My flips.
  Asks you to type CONFIRM and explains exactly what is sent (your name and a one-use code; your
  login never leaves Minecraft). See "What /ff verify sends" in the README.
- **Safe mode:** a feature that keeps erroring switches itself off until you restart, and tells you
  once; `/ff safemode reset` turns it back on.
- **Alert limits and quiet hours** from your flip profile are explained in chat (and on the status
  card); each alert has a `[mute 1h]` button, plus `/ff mute hour|today|forever`,
  `/ff mute category …` and `/ff unmute [all]`. Needs the website's next update.
- Settings screen: Dungeons toggle; Theme button.
- Under the hood: shared library with Fates OFA (Fates Common), all text ready for translation.

## 0.3.4

- Other mods' tooltip lines show again under Fates tooltips (MODS section).
- Alerts only in SkyBlock by default (option for dungeons and Kuudra); no double alerts.
- Old chat lines won't open auctions that already sold.
- Mod token hidden in settings and kept out of the settings file and chat history.
- LAN and Tailscale addresses allowed over http; Mod Menu button; update notices from the website.

## 0.3.3

- Auctions Browser tooltips no longer flash or resize; Bazaar items are labelled; mouse buttons work
  on 26.3.

## 0.3.2

- Auction panel on the left of the window; long tooltips scroll with the mouse wheel.

## 0.3.1

- Small text in Minecraft's own font for readability.

## 0.3.0

- The Fates look: new cards for the auction panel, chat hover and item tooltips; Chakra Petch font.

## 0.2.1

- The overlay only appears on the flip you opened; Buy button coloured by state; press the open key
  again to close the flip.

## 0.2.0

- Prices for items that never traded, by their exact configuration.

## 0.1.0

- First build: flip alerts, open best / safest keys, auction overlay, hover prices, cursor assist
  (opt-in).
