# Fates Flipper

The in-game mod of the SkyBlock Market Intelligence Suite: a Fabric client mod for Hypixel SkyBlock
that shows flips from **Fates Viewer** in chat, opens auctions on a key press, adds price tooltips and
cleans up the auction window.

**Display only.** The mod never clicks, buys, confirms, relists or repeats anything for you. One key
press or one click sends at most one `/viewauction`, with a half-second cooldown. Every purchase is
your own two clicks.

## Requirements

- Minecraft **26.2** or **26.3** with **Fabric Loader** 0.19.3 or newer (one jar per version)
- **Fabric API** for the same version
- A Fates Viewer account and a **mod token** (web page → account menu → Mod tokens)

## Install

1. Install Fabric Loader for your version (Fabric installer, or a launcher like Prism / Modrinth App).
2. Put the jar for your Minecraft version (`fatesflipper-<version>+mc26.2.jar` or `…+mc26.3.jar`)
   and Fabric API into the game's `mods` folder.
3. Start the game, join Hypixel, type `/ff config`, fill in the viewer address and your mod token,
   press **Test connection**, then **Save**.
4. Bind the keys in Options → Controls → Key Binds → *Fates Flipper* (unbound by default).

## Features

| Feature | How |
|---|---|
| Flip alerts in chat | Pushed by the viewer for your active flip profile. Click a line to open the auction. Hover for details. |
| Open best / safest flip | Key binds or `/ff best`, `/ff safest`. Skips auctions that already sold. |
| Auction window overlay | On auctions opened through the mod: filler panes dimmed, item and Buy / Confirm outlined, side panel with price, profit, confidence and recent sales of the exact item. Drawn on top of Hypixel's window; clicks always go to Hypixel's slots. |
| Price tooltips | Value, safe value, confidence, lowest BIN, sales per day and dupe flag for any item with a SkyBlock uuid that has traded on the AH. |
| Status display | Top-left line when connecting, offline or misconfigured. |
| Cursor assist | **Opt-in, off by default.** Moves the cursor once onto Buy, then once onto Confirm, for an auction you just opened from a flip. Never clicks, no jitter, and never if you're already moving the mouse. Hypixel doesn't certify mods: use at your own risk. |

## Commands

`/ff config` · `/ff status` · `/ff best` · `/ff safest` · `/ff recent` · `/ff profiles` ·
`/ff profile <name>|off` · `/ff on|off` · `/ff sound|tooltips|overlay|hud on|off` ·
`/ff cursorassist on|off` · `/ff reconnect` · `/ff server <address>` · `/ff token <token>`

## Privacy and safety

- The mod talks only to the viewer address you set. Your mod token is stored in
  `config/fatesflipper.json`, sent only to that address (redirects are never followed), and never
  written to logs. Plain `http://` is only allowed to this computer and Tailscale addresses.
- The mod never reads, stores or sends your Minecraft login or session.
- Data from the server is only ever shown. The only command the mod sends because of server data is
  `/viewauction <32-hex id>`, checked locally; anything else is refused.
- No auto-updater. Download releases only from this repository.

## Build

```
./gradlew build -Pmc=26.2                 # jar in build/libs (or -Pmc=26.3)
./gradlew runClientGametest -Pmc=26.2     # in-game smoke test (starts a real client)
```

Version-specific numbers are in `versions/<mc>.properties`; the little version-specific code
(26.3 uses SDL3 instead of GLFW) is in `src/mc<mc>/java`.

Needs JDK 25. Every push to `main` is built and tested for both versions by GitHub Actions; the jars
are attached to the run, and per version the build log, game-test log, screenshots and jar are
published to the `ci-results` branch.

## Licence

GPL-3.0-only. See [LICENSE](LICENSE).
