# Repeater Tab Renamer

A Burp Suite extension (Montoya API) that gives your Repeater tabs real names instead of `1`, `2`, `3`.

![Renaming a Repeater tab from selected text](docs/Demo.gif)

[**Download the latest release**](https://github.com/falasi/RepeaterTabRenamer/releases/latest)

## Install

Burp Suite → **Extensions → Installed → Add** → Extension type: **Java** → select the jar.

## Usage

Four ways to get a named tab:

- **Automatically** — when you send a request from Repeater, the tab is named from the request (path, or a body field, or the host). See [Naming rules](#naming-rules).
- **From a selection in Repeater** — select text, then press `Ctrl+Alt+R` or right-click → **Extensions → Repeater Tab Renamer → Use selection as Repeater tab name**.
- **From several selections** — stage two or three pieces with `Ctrl+Alt+1` / `2` / `3`, then press `Ctrl+Alt+R` to combine them. See [Multi-part names](#multi-part-names).
- **From Proxy HTTP history** — select text (or just a row) and press a hotkey to send it straight to a new, already-named Repeater tab.

Auto-naming only touches tabs that still look auto-generated (a plain number, "Untitled", or empty), so a name you set by hand is never overwritten. The hotkeys always overwrite — they're explicit actions.

### Hotkeys

| Hotkey | Where | What it does |
| --- | --- | --- |
| `Ctrl+Alt+R` | Any focused message editor | In Repeater: renames the active tab from your selection. Anywhere else: sends the message to a new, named Repeater tab. |
| `Ctrl+Alt+1` `Ctrl+Alt+2` `Ctrl+Alt+3` | Repeater | Stages your selection as that numbered piece of the next name. Press one with nothing selected to clear just that piece. |
| `Ctrl+Alt+S` | Proxy HTTP history, row selected | Sends the selected request to a new, named Repeater tab. Use this when you've clicked a row but not into its viewer pane. |

If another extension already claims a combo, registration falls back to `Ctrl+Shift+…` with the same final key. Either way, both commands are rebindable under **Settings → Hotkeys** (search for "Repeater tab name" or "Send to Repeater").

The extension's **Output** tab logs which combo each hotkey actually registered with at load time. If you've rebound one since, Settings → Hotkeys is the source of truth.

### Multi-part names

To build a name out of pieces that aren't next to each other in the request:

1. Select `POST` → `Ctrl+Alt+1`
2. Select `users` → `Ctrl+Alt+2`
3. Select `admin` → `Ctrl+Alt+3` *(optional)*
4. `Ctrl+Alt+R` → the tab becomes `POST-users-admin`

Notes:

- Staged pieces belong to the Repeater tab you staged them in, so they can't leak into another tab. Closing the tab discards them.
- Re-press a number to replace that piece; press it with nothing selected to clear it.
- `Ctrl+Alt+R` consumes the pieces, so the next press is back to plain selection renaming.
- The extension's **Output** tab echoes each piece as you stage it, and the right-click menu shows the pending name while pieces are staged.

## Settings

**Settings → Extensions → Repeater Tab Renamer** has one preference: how generated name pieces are joined.

| Option | Example |
| --- | --- |
| Hyphen (default) | `api-users`, `api-users-2` |
| Space | `api users`, `api users 2` |
| Underscore | `api_users`, `api_users_2` |

It applies everywhere the extension builds a name — automatic naming, sanitized selections, duplicate numbering, and multi-part names — and takes effect immediately, no reload. It only affects separators the extension *inserts*: a path segment that already reads `user-profile` stays `user-profile`.

The choice is stored in Burp's user settings, so it persists across projects and restarts.

## Duplicate names

If a generated name is already on another Repeater tab, a number is appended:

```
api-users
api-users-2
api-users-3
```

This is decided against the tab titles that exist at that moment, not a running tally, which keeps it predictable:

- Close `api-users` and the next matching request takes the plain name again.
- Close `api-users-2` and the gap is reused before `-4` is handed out.
- A name you typed by hand is just another existing title, so numbering routes around it and never rewrites it.
- Re-sending inside a tab that already holds the name changes nothing — no `-2`, `-3`, `-4` creep.

Numbering applies to names created by "send to Repeater" too, so sending three matching history rows gives three distinguishable tabs.

## Requirements

- **Burp 2023.9 or later** — auto-naming and the right-click menu item.
- **Burp shipping montoya-api 2025.12 or later** — the hotkeys. On an older release they silently don't register; everything else still works.
- **A Burp with the extension settings panel** — the separator preference. Without it, names are joined with `-`.
- **JDK 17+** — to build from source only. Not needed to load the jar.

## Naming rules

1. **Path** — the last segment of the request path, e.g. `/api/users/login` → `login`. Applies to every method.
2. **Body** — if the path is too generic to distinguish requests (`/`, `/api`, `/graphql`, `/v1`, ...) and the request has a body, a likely-identifying field is used instead: `operationName`, `action`, `method`, `type`, `event`, `name`, `username`, `email`, or `id` for JSON; the first `key=value` pair for form-encoded bodies.
3. **Host** — if neither yields anything usable, the tab is named after the target host, so tabs stay distinguishable across several hosts' root paths.

Names are sanitized (invalid characters → your chosen [separator](#settings)) and capped at 40 characters. Duplicates are numbered — see [Duplicate names](#duplicate-names).

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
