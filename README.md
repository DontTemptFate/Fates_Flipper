# Fates Flipper

A Fabric client mod for Hypixel SkyBlock, part of the Fates Flipper suite. It shows flips from the
**Fates Flipper** website (the "viewer") in chat, opens auctions on a key press, prices items in
their tooltips and cleans up the auction window.

**Display only.** The mod never clicks, buys, confirms, relists or repeats anything for you. One key
press or one click sends at most one `/viewauction`, with a half-second cooldown. Every purchase is
your own two clicks.

## Requirements

- Minecraft **26.2** or **26.3** with **Fabric Loader** 0.19.3 or newer (one jar per version)
- **Fabric API** for the same version (at least the version the jar was built with)
- A Fates Flipper account and a **mod token** (website → account menu → Mod tokens)
- Optional: **Mod Menu** (the gear button opens the settings)

## Install and first run

1. Install Fabric Loader for your Minecraft version (the Fabric installer, or a launcher such as
   Prism or the Modrinth App).
2. Download the jar for your version from the official
   [Releases page](https://github.com/DontTemptFate/Fates_Flipper/releases)
   (`fatesflipper-<version>+mc26.2.jar` or `…+mc26.3.jar`) and check its SHA-256 against
   `SHA256SUMS.txt` on the same page. Put it and Fabric API into the game's `mods` folder.
3. Start the game and type `/ff config` (or use Mod Menu's gear button).
4. Paste the website address and your mod token, press **Test connection**, then **Save**. The token
   is shown as dots; **Show** reveals it.
5. Bind the keys: Options → Controls → Key Binds → *Fates Flipper* (all unbound by default).
6. Join Hypixel and go to SkyBlock. "Connected to Fates Viewer" appears in chat, and flips for your
   active flip profile follow.

## Features

| Feature | How |
|---|---|
| Flip alerts in chat | Pushed by the website for your active flip profile, only while you're in SkyBlock (not in dungeons or Kuudra unless you turn that on). Hover a line for the flip card; click it to open the auction (sold ones are refused). A flip never shows twice, and flips missed during a short disconnect are sent again. |
| Open best / safest flip | Key binds or `/ff best`, `/ff safest`. Skips auctions you already opened or that already sold. Press the same key again inside that auction to close it. |
| Auction window overlay | On the flip you opened (checked by price **and** item name): filler dimmed, the item and Buy / Confirm outlined by state (green buy, orange can't afford, red sold), side panel with price, profit, confidence and recent sales of the exact item. Drawn on top of Hypixel's window; your clicks always go to Hypixel's slots. |
| Item tooltips | SkyBlock items get a cleaner tooltip: stats, enchants, the auction listing (seller, price, time left), lines other mods add (under **MODS**) and the Fates price block (value, safe value, confidence, lowest BIN, sales per day, dupe flag). Bazaar items say so. Long tooltips scroll with the mouse wheel. |
| Status display | Top-left card when connecting, offline, stopped (with the reason) or when an update is required. |
| Update notice | When the website says a newer version is out, one chat line. When it requires a newer version, flips stop until you update (hover prices keep working). The mod never downloads anything itself. |
| Cursor assist | **Opt-in, off by default, and turning it on asks you to type CONFIRM.** Moves the cursor once onto Buy, then once onto Confirm, for an auction you just opened from a flip, only when Buy is green. Never clicks, no jitter, and never if you're already moving the mouse. Hypixel doesn't certify mods: use at your own risk. |

## Commands

| Command | What |
|---|---|
| `/ff config` | settings screen (server address, token, toggles) |
| `/ff status` | connection, profile, version and settings |
| `/ff best` · `/ff safest` | open the best / safest flip (or bind keys in Controls) |
| `/ff recent` | the last flips, clickable |
| `/ff profiles` · `/ff profile <name>` · `/ff profile off` | list / switch flip profiles |
| `/ff on` · `/ff off` | flip alerts on / off |
| `/ff sound\|tooltips\|overlay\|hud on\|off` | toggles |
| `/ff skyblock on\|off` | alerts only in SkyBlock (on) |
| `/ff dungeons on\|off` | alerts in dungeons and Kuudra too (off) |
| `/ff closekey on\|off` | press the open key again to close the flip (on) |
| `/ff look\|fatestooltips on\|off` | the Fates look; Fates item tooltips |
| `/ff font chakra\|vanilla` | Chakra Petch or Minecraft's font |
| `/ff cursorassist on\|off` | opt-in, off by default; turning it on asks you to confirm |
| `/ff reconnect` | connect again now |
| `/ff server <address>` · `/ff token <token>` | set the address / token without the settings screen |

## Privacy and safety

- The mod talks only to the website address you set. Your mod token is kept in its own file,
  `config/fatesflipper-token.txt`, separate from the other settings in `config/fatesflipper.json`,
  so sharing your settings never shares the token. **Never share your config folder or that file.**
  The token is sent only to your address (redirects are never followed) and never written to logs.
- Plain `http://` is only allowed to this computer, your home network (10.x, 172.16–31.x,
  192.168.x) and Tailscale addresses; anything else needs `https://`.
- The mod never reads, stores or sends your Minecraft login or session.
- Data from the server is only ever shown. The only command the mod sends because of server data is
  `/viewauction <32-hex id>`, checked locally; anything else is refused. Download links from the
  server are shown as text only (https).
- Price lookups send only the item itself, never Hypixel's listing lines (no seller names).
- No auto-updater. Download releases only from this repository's Releases page (or the official
  Modrinth page once it exists).
- A crash report includes a short "Fates Flipper" section (version and which features were on),
  never the token or the server address.

## Build

```
./gradlew build -Pmc=26.2                 # jar in build/libs (or -Pmc=26.3)
./gradlew runClientGametest -Pmc=26.2     # in-game smoke test (starts a real client)
```

Version-specific numbers are in `versions/<mc>.properties`; the little version-specific code
(26.3 uses SDL3 instead of GLFW) is in `src/mc<mc>/java`. Needs JDK 25.

Every push to `main` is built and tested for both versions by GitHub Actions (unit tests and an
in-game test against a fake website, with screenshots); results are published to the `ci-results`
branch. Pushing a tag `v<version>` (e.g. `v0.3.4`, or `v0.3.4-beta.1` for a beta) builds both jars
and publishes a GitHub Release with `SHA256SUMS.txt` and the changelog, and uploads to Modrinth when
the project is set up there.

## Licence

GPL-3.0-only. See [LICENSE](LICENSE). Bundles the Chakra Petch font (SIL Open Font License 1.1,
`licenses/chakra-petch-OFL.txt` in the jar).
