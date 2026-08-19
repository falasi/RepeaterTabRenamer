# Repeater Tab Renamer

A Burp Suite extension (Montoya API) that gives your Repeater tabs real names instead of `1`, `2`, `3`.

![Renaming a Repeater tab from selected text](docs/Demo.gif)

[**Download the latest release**](https://github.com/falasi/RepeaterTabRenamer/releases/latest)

## Install

Burp Suite → **Extensions → Installed → Add** → Extension type: **Java** → select the jar.

## Usage

Four ways to get a named tab:

- **Automatically** — when you send a request from Repeater, the tab is named from the request, by default as `POST | /apps/emailShare/postMessage`. See [Naming rules](#naming-rules).
- **From a selection in Repeater** — select text, then press `Ctrl+Alt+R` or right-click → **Extensions → Repeater Tab Renamer → Use selection as Repeater tab name**.
- **From several selections** — stage two or three pieces with `Ctrl+Alt+1` / `2` / `3`, then press `Ctrl+Alt+R` to combine them, joined with your chosen separator. See [Multi-part names](#multi-part-names).
- **From Proxy HTTP history** — select text (or just a row) and press a hotkey to send it straight to a new, already-named Repeater tab.

Auto-naming only touches tabs that still look auto-generated (a plain number, "Untitled", or empty), so a name you set by hand is never overwritten. The hotkeys always overwrite — they're explicit actions.

### Hotkeys

| Hotkey | Where | What it does |
| --- | --- | --- |
| `Ctrl+Alt+R` | Any focused message editor | In Repeater: renames the active tab from your selection. Anywhere else: sends the message to a new, named Repeater tab. |
| `Ctrl+Alt+1` `Ctrl+Alt+2` `Ctrl+Alt+3` | Repeater | Stages your selection as that numbered piece of the next name. Press one with nothing selected to clear just that piece. |
| `Ctrl+Alt+Shift+R` | Proxy HTTP history, row selected | Sends the selected request to a new, named Repeater tab. Use this when you've clicked a row but not into its viewer pane. |

Combos are claimed first-come, so another extension may already hold one — Hackvertor takes `Ctrl+Alt+S`, which is why that is no longer the default here. Each shortcut has a fallback chain and registration walks it until Burp accepts one, so a clash costs you a different key, never the feature. Whatever actually registered is printed to the **Output** tab at load. All commands are rebindable under **Settings → Hotkeys** (search for "Repeater tab name" or "Send to Repeater").

The extension's **Output** tab logs which combo each hotkey actually registered with at load time. If you've rebound one since, Settings → Hotkeys is the source of truth.

If every candidate for a shortcut is taken, it shows as `(unavailable)` rather than advertising a key that does nothing; bind it by hand under Settings → Hotkeys.

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
- The right-click menu shows the pending name while pieces are staged. Staging doesn't write to the **Output** tab.

## Settings

**Settings → Extensions → Repeater Tab Renamer** has two preferences. Both take effect immediately, with no reload, and are stored in Burp's user settings so they persist across projects and restarts.

### Automatic naming format

Controls only how tabs are named *automatically*, when you send a request. Names you build from a selection or from staged parts are always exactly what you selected.

| Option | `POST /apps/emailShare/postMessage?mailboxid=123` becomes |
| --- | --- |
| `Method and path` (default) | `POST \| /apps/emailShare/postMessage` |
| `Last path segment` | `postMessage` |
| `Path, body field, or host` | `postMessage` — see [Naming rules](#naming-rules) |

`Method and path` is the default because it stays useful as a session grows: the method tells a `GET` from the `POST` to the same endpoint, and the full path tells `/users/create` from `/admin/create` — exactly where last-segment naming starts producing tabs you can't tell apart. The query string, scheme and host are never included, and a redundant trailing `/` is dropped.

### Name separator

Joins the pieces of a generated name, and replaces characters that aren't legal in a tab name.

| Option | Example |
| --- | --- |
| `Hyphen` (default) | `POST-users-admin` |
| `Space` | `POST users admin` |
| `Underscore` | `POST_users_admin` |
| `Pipe` | `POST \| users \| admin` |

`Pipe` pads itself with spaces, because `POST | users | admin` reads better than `POST|users|admin`. It replaces illegal characters with a *space* rather than a pipe — a pipe implies a boundary, and stamping one over stray punctuation inside a single value would invent structure that isn't there.

The separator only affects characters the extension *inserts*: a path segment that already reads `user-profile` stays `user-profile`. Two things ignore it entirely, because both are structure rather than words:

- the duplicate suffix, always `name (2)`;
- the divider in `Method and path`, always ` | ` — `POST | /apps/emailShare` is far easier to read than `POST-/apps/emailShare`.

## Duplicate names

If a generated name is already on another Repeater tab, a number is appended:

```
api-users
api-users (2)
api-users (3)
```

This is decided against the tab titles that exist at that moment, not a running tally, which keeps it predictable:

- Close `api-users` and the next matching request takes the plain name again.
- Close `api-users (2)` and the gap is reused before `(4)` is handed out.
- A name you typed by hand is just another existing title, so numbering routes around it and never rewrites it.
- Re-sending inside a tab that already holds the name changes nothing — no `-2`, `-3`, `-4` creep.

Numbering applies to names created by "send to Repeater" too, so sending three matching history rows gives three distinguishable tabs. The suffix format is fixed regardless of your separator choice:

```
POST users        POST_users
POST users (2)    POST_users (2)
POST users (3)    POST_users (3)
```

On load, the extension prints a short reminder to its **Output** tab — the shortcuts as they actually registered, and where the separator setting lives:

```text
Repeater Tab Renamer v1.3.0 enabled

Ctrl+Alt+R         Rename from selection / staged parts
Ctrl+Alt+1/2/3     Stage name parts
Ctrl+Alt+Shift+R   Send to Repeater with automatic naming

Separator: Settings > Extensions > Repeater Tab Renamer
Hotkeys can be changed under Settings > Hotkeys.
```

That banner, genuine warnings and unexpected errors are all the extension writes to **Output** — staging a part or renaming a tab succeeds silently. While parts are staged, the right-click menu shows the pending name.

## Requirements

- **Burp 2023.9 or later** — auto-naming and the right-click menu item.
- **Burp shipping montoya-api 2025.12 or later** — the hotkeys. On an older release they silently don't register; everything else still works.
- **A Burp with the extension settings panel** — the separator preference. Without it, names are joined with `-`.
- **JDK 17+** — to build from source only. Not needed to load the jar.

## Naming rules

These apply to the `Path, body field, or host` format. The other two formats are described under [Settings](#settings).

1. **Path** — the last segment of the request path, e.g. `/api/users/login` → `login`. Applies to every method.
2. **Body** — if the path is too generic to distinguish requests (`/`, `/api`, `/graphql`, `/v1`, ...) and the request has a body, a likely-identifying field is used instead: `operationName`, `action`, `method`, `type`, `event`, `name`, `username`, `email`, or `id` for JSON; the first `key=value` pair for form-encoded bodies.
3. **Host** — if neither yields anything usable, the tab is named after the target host, so tabs stay distinguishable across several hosts' root paths.

Names are sanitized (invalid characters → your chosen [separator](#name-separator)) and capped at 40 characters, including any duplicate suffix — the base name is trimmed so `name (10)` still fits. A `Method and path` name that doesn't fit loses whole segments from the *front*, marked with `…`, so the endpoint at the end survives: `POST | …/attachments/postMessage`. Only the first 8 KB of a request body is scanned for a name. Duplicates are numbered — see [Duplicate names](#duplicate-names).

## Limitations

Burp's public API only lets an extension name a tab **it** creates. There's no supported way to rename an existing tab — including the common case of one created by "Send to Repeater" from Proxy or Target.

To handle that, the extension walks Burp's Swing UI tree to find and retitle the active Repeater tab when you hit Send. Two consequences:

- Renaming happens on **first send**, not when the tab is created.
- It depends on Burp's internal UI structure, not a documented API. If auto-renaming silently stops working after a Burp update, check the extension's **Output** and **Errors** tabs first.

All of that is confined to one class, `RepeaterUiLocator`, so it's the only file that changes if Montoya ever exposes native tab renaming. It runs only on the Event Dispatch Thread, never throws, holds no strong reference to any Burp component, rejects anything that doesn't look like a Repeater request tab strip, and returns "found nothing" if Burp's layout differs — in which case the tab simply isn't renamed and Burp is left untouched.

The hotkeys and menu item don't have this problem — they're built entirely on documented Montoya APIs.

## Network and data handling

The extension makes no network connections of its own and works fully offline. Request and response bytes are treated as untrusted: text taken from a message (path segments, body fields, your selection) is only ever used as a tab title after being stripped to `A-Za-z0-9._-` plus your separator and capped at 40 characters. Nothing is written to disk beyond the one separator preference, which is stored via Burp's own settings API.

Note that Burp nests every extension's context-menu items under an **Extensions** submenu rather than the top-level right-click menu. That's standard Burp behavior, not something an extension can opt out of; the hotkey skips the navigation.

## Build

```bash
./gradlew build     # jar at build/libs/repeater-tab-renamer-<version>.jar
./gradlew test      # tests only
```

Built against `montoya-api:2025.12` as `compileOnly` — Burp provides those classes at runtime, so nothing is shaded or bundled. Building against an older API jar will fail to compile the hotkey handlers.

## License

[MIT](LICENSE)
