# Repeater Tab Renamer

A Burp Suite extension (Montoya API) that gives your Repeater tabs real names instead of `1`, `2`, `3`.

![Renaming a Repeater tab from selected text](docs/Demo.gif)

[**Download the latest release**](https://github.com/falasi/RepeaterTabRenamer/releases/latest)

## Install

Burp Suite → **Extensions → Installed → Add** → Extension type: **Java** → select the jar.

## Usage

Three ways to get a named tab:

- **Automatically** — when you send a request from Repeater, the tab is named from the request (path, or a body field, or the host). See [Naming rules](#naming-rules).
- **From a selection in Repeater** — select text, then press `Ctrl+Alt+R` or right-click → **Extensions → Repeater Tab Renamer → Use selection as Repeater tab name**.
- **From Proxy HTTP history** — select text (or just a row) and press a hotkey to send it straight to a new, already-named Repeater tab.

Auto-naming only touches tabs that still look auto-generated (a plain number, "Untitled", or empty), so a name you set by hand is never overwritten. The hotkeys always overwrite — they're explicit actions.

### Hotkeys

| Hotkey | Where | What it does |
| --- | --- | --- |
| `Ctrl+Alt+R` | Any focused message editor | In Repeater: renames the active tab from your selection. Anywhere else: sends the message to a new, named Repeater tab. |
| `Ctrl+Alt+S` | Proxy HTTP history, row selected | Sends the selected request to a new, named Repeater tab. Use this when you've clicked a row but not into its viewer pane. |

If another extension already claims a combo, registration falls back to `Ctrl+Shift+R` / `Ctrl+Shift+S`. Either way, both commands are rebindable under **Settings → Hotkeys** (search for "Use selection as Repeater tab name" or "Send to Repeater (named from selection)").

The extension's **Output** tab logs which combo each hotkey actually registered with at load time. If you've rebound one since, Settings → Hotkeys is the source of truth.

## Requirements

- **Burp 2023.9 or later** — auto-naming and the right-click menu item.
- **Burp shipping montoya-api 2025.12 or later** — the two hotkeys. On an older release they silently don't register; everything else still works.
- **JDK 17+** — to build from source only. Not needed to load the jar.

## Naming rules

1. **Path** — the last segment of the request path, e.g. `/api/users/login` → `login`. Applies to every method.
2. **Body** — if the path is too generic to distinguish requests (`/`, `/api`, `/graphql`, `/v1`, ...) and the request has a body, a likely-identifying field is used instead: `operationName`, `action`, `method`, `type`, `event`, `name`, `username`, `email`, or `id` for JSON; the first `key=value` pair for form-encoded bodies.
3. **Host** — if neither yields anything usable, the tab is named after the target host, so tabs stay distinguishable across several hosts' root paths.

Names are sanitized (invalid characters → `-`) and capped at 40 characters.

## Limitations

Burp's public API only lets an extension name a tab **it** creates. There's no supported way to rename an existing tab — including the common case of one created by "Send to Repeater" from Proxy or Target.

To handle that, the extension walks Burp's Swing UI tree to find and retitle the active Repeater tab when you hit Send. Two consequences:

- Renaming happens on **first send**, not when the tab is created.
- It depends on Burp's internal UI structure, not a documented API. If auto-renaming silently stops working after a Burp update, check the extension's **Output** and **Errors** tabs first.

The hotkeys and menu item don't have this problem — they're built entirely on documented Montoya APIs.

Note that Burp nests every extension's context-menu items under an **Extensions** submenu rather than the top-level right-click menu. That's standard Burp behavior, not something an extension can opt out of; the hotkey skips the navigation.

## Build

```bash
./gradlew build     # jar at build/libs/repeater-tab-renamer-<version>.jar
./gradlew test      # tests only
```

Built against `montoya-api:2025.12` as `compileOnly` — Burp provides those classes at runtime, so nothing is shaded or bundled. Building against an older API jar will fail to compile the hotkey handlers.

## License

[MIT](LICENSE)
