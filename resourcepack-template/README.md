# PartyFinder resource pack template

This folder is a complete resource pack: every file the mod lets you override, at its default value. Copy the
folder into `.minecraft/resourcepacks/` (or run `./gradlew resourcePackTemplate` for a zip in
`build/resourcepack/`), edit what you want and enable the pack.

A file in the pack replaces the mod's file entirely, so once you are done delete the files you did not change:
an untouched `activities.json` or lang file would otherwise hide the activities and texts added by later versions
of the mod.

Mod developers: the assets here are a copy of `src/client/resources/assets/partyfinder/`; refresh them with
`./gradlew updateResourcePackTemplate` (`./gradlew check` fails when they are out of date).

`menu.json`, `listing_menu.json`, `activities.json`, `theme.json` and `notifications.json` are re-read every time
the PartyFinder menu opens and on `F3 + T`; textures, sounds and translations need `F3 + T`.

`theme.json`, `notifications.json` and `listing_menu.json` are the exception: they are merged key by key with the
mod's file (and the packs below yours), so keep only the keys you change, e.g. `{ "chat": { "prefix": "[Party] " } }`.

## Files

```
assets/partyfinder/
├── partyfinder/
│   ├── menu.json            category menu (chest GUI)
│   ├── listing_menu.json    listings in the chest style (Hypixel Party Finder look)
│   ├── activities.json      activities, categories, options, launch commands, stats
│   ├── theme.json           text colors, chat prefix and frame
│   └── notifications.json   toast icons and durations, notification sounds
├── lang/
│   ├── en_us.json         every text of the mod
│   └── fr_fr.json
└── textures/gui/sprites/
    ├── listing/
    │   ├── entry.png              listing / history / preset row background
    │   ├── entry_highlighted.png  hovered row
    │   └── entry_own.png          your own listing
    └── hud/
        ├── frame.png              party HUD member frame
        ├── frame_self.png         your own frame
        ├── leader.png             party leader mark before the name (8×8)
        ├── bar_background.png     health / mana bar background (nine-slice, tinted)
        ├── bar_health.png         health fill (tinted with hud_health_*)
        ├── bar_absorption.png     absorption fill (tinted with hud_absorption)
        └── bar_mana.png           mana fill (tinted with hud_mana)
```

## Icons

Every `icon` (and the menu `filler`) is either an item id or an object, so menus and activities can use custom heads:

```json
"icon": "minecraft:diamond_pickaxe"
"icon": { "texture": "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUv..." }
"icon": { "skin": "a0b1c2...e9f" }
"icon": { "player": "Notch" }
"icon": { "item": "minecraft:diamond", "glint": true, "model": "mypack:shiny_diamond" }
```

| Key | Meaning |
| --- | --- |
| `texture` | Custom head from a base64 texture `Value`, as given by head sites ("Value" field) or found in an item's `profile` component. |
| `skin` | Custom head from a skin hash, or the full `http://textures.minecraft.net/texture/<hash>` url. |
| `player` | Head of a player, by name or uuid (looked up once, like any player head). |
| `item` | Item id. Defaults to `minecraft:player_head` with `texture`, `skin` or `player`. |
| `glint` | `true` adds the enchantment glint, `false` removes it. |
| `model` | Item model id (`item_model` component), to give the icon a model from your pack. |

An invalid icon falls back to paper (empty for `filler`).

## `partyfinder/menu.json`

| Key | Meaning |
| --- | --- |
| `texture` | Container texture, 256×256 laid out like `minecraft:textures/gui/container/generic_54.png` (176 px wide: title bar and slot rows from the top, bottom border at y = 215, the "My party" panel reuses the top bar and the row strip at y = 128). |
| `rows` | Number of slot rows, 1 to 6. |
| `filler` | Icon drawn in empty slots (`""` or remove the key for none). |
| `open_sound` | `{ "id", "volume", "pitch" }` played when the menu opens; remove the key for silence. `/pf sounds` prints the ids of sounds the server plays. |
| `slots[]` | `slot` (0 to rows × 9 − 1), `action`, `icon` (see [Icons](#icons)), and `category` for the `category` action. |

Slot actions: `category` (opens one category, needs `category`), `all` (every listing), `create`, `requests`,
`history`, `settings`, `moderation`, `reconnect`, `close`.

## `partyfinder/activities.json`

| Key | Meaning |
| --- | --- |
| `id` | Activity id, shared with other players through the server. |
| `category` | Category id, used by `menu.json` and translated as `partyfinder.category.<id>`. |
| `icon` | Icon shown for the activity (see [Icons](#icons)): item id or custom head. |
| `sprite` | Optional 16×16 sprite drawn instead of `icon`, e.g. `partyfinder:activity/critter_safari` → `textures/gui/sprites/activity/critter_safari.png`. |
| `color` | Activity color (`#RRGGBB`). |
| `max_size` | Party size (at least 2). |
| `options[]` | `id` + `values[]` (`id`, optional `commands`). The first option is appended to the listing title. Only travel commands are run: `warp <place>` and `joininstance <INSTANCE>` (leading `/` optional); anything else is ignored. |
| `launch` | Who runs the selected values' commands once the party is full: `host`, `all` or `none`. |
| `stats[]` | Member stats shown and usable as verified requirements: `sb`, `cata`, `class`, `hotm`, `kuudra`, `skill_<mining\|fishing\|combat\|farming\|foraging\|enchanting\|alchemy\|taming\|hunting>`, `slayer_<zombie\|spider\|wolf\|enderman\|blaze\|vampire>`. |
| `events[]` | Event alerts suggesting this activity: `fishing_festival`, `spooky_festival`, `mining_fiesta`, `diana`. |

The server only accepts the mod's own activities: their `id`, `category` and option values are checked against
its list, so a pack cannot publish new activities, move one to another category or add option values. Restyle
existing activities freely (icon, sprite, color, names) and change your own launch `commands`, which only ever run
on your side; new activities have to be added to the mod and the server.

Two optional top-level keys sit next to `activities`:

| Key | Meaning |
| --- | --- |
| `categories` | Category order in the activity picker; categories it leaves out follow in activity order. |
| `unknown` | Style of activities your mod does not know (newer version): `icon`, `sprite`, `color`, `max_size`, `stats`, `category`. |
| `category_items` | Per category, the SkyBlock item ids (`HYPERION`, `TERMINATOR`...) listed under "Items" in a player's stats card, in this order. The server only looks for the mod's own list, so a pack can reorder or drop items, not add new ones. Names come from `partyfinder.item.<id in lowercase>` or the id made readable. |

Names come from the lang files: `partyfinder.activity.<id>`, `partyfinder.category.<category>`,
`partyfinder.option.<option>` and `partyfinder.option.<option>.<value>`.

## `partyfinder/theme.json`

`colors` maps each key to a `#RRGGBB` color. Listing: `title`, `label`, `empty`, `owner`, `note`,
`requirements`, `age`, `slots_open`, `slots_full`, `stat`, `leader`. Connection status: `status_online`,
`status_connecting`, `status_offline`. Chest menu: `menu_title`, `menu_status_online`, `menu_status_connecting`,
`menu_status_offline`. Moderation: `reports_some`, `reports_none`, `report_limit`, `banned`. Party HUD:
`hud_name`, `hud_self`, `hud_leader`, `hud_info`, `hud_unknown`, `hud_health_high`, `hud_health_mid`,
`hud_health_low`, `hud_mana`, `hud_absorption` (health text and bar end while absorbing), `hud_bar_background`, `hud_dead`. Dungeon classes: `class_healer`, `class_mage`,
`class_berserk`, `class_archer`, `class_tank`. Chest listing style: `full_marker`. Toasts: `toast_title`,
`toast_body`.

Stats card (hover on a join request or a member joining): `card_line` (header line), `card_name` (name of a player
without rank), `card_label`, `card_value`, `card_highlight` (selected class), `card_muted`, `card_item`. Category
names in "queued in the ... party finder": `category_<category>` (e.g. `category_dungeons`).

Chat messages: `chat_prefix` (the `[PF]` prefix), `chat_frame` (lines around requests and prompts),
`chat_success`, `chat_error`, `chat_notice` (things in progress), `chat_detail` (missing requirements, reports),
`chat_event` (SkyBlock events), `chat_muted`, `chat_stats` (stats in a join request), and the clickable buttons
`chat_button_accept`, `chat_button_decline`, `chat_button_auto` ("I get it, auto accept") and `chat_button_link`
(launch, moderation, events).

`chat` sets the look of the mod's chat messages:

| Key | Meaning |
| --- | --- |
| `prefix` | Text before every message (`"[PF] "`; `""` for none). Up to 32 characters, no `§` codes. |
| `frame` | `false` drops the lines around join requests and warp / transfer prompts. |
| `frame_text` | What the line repeats (`"-"`, up to 4 characters). |
| `frame_length` | Line length in characters (53, Hypixel's width). |
| `frame_strikethrough` | `true` draws the line struck through, like Hypixel. |

## `partyfinder/listing_menu.json`

Layout of the chest listing style (`/pf config` > Interface > Listing style). `texture` and `filler` default to the
ones of `menu.json`.

| Key | Meaning |
| --- | --- |
| `texture`, `rows`, `filler`, `open_sound` | Like in `menu.json`; `rows` is 2 to 6. |
| `listing_slots` | Slots showing listings, in order; their count is the number of parties per page. Default: the inner 7×4 area. |
| `listing_icon` | `head` (the leader's head, default) or `activity` (the activity icon). |
| `own_glint` | `true` makes your own listing glint. |
| `full_marker` | `true` draws `partyfinder.symbol.full` (lang) on full parties, color `full_marker`. |
| `empty_slot`, `empty_icon` | Slot and icon shown when there are no listings. |
| `buttons` | `previous`, `next`, `back`, `close`, `create`, `requests`, `leave`: each `{ "slot", "icon" }`, or `null` to remove it. |

Buttons only show when they do something (no `previous` on the first page, no `requests` without requests...).

## `partyfinder/notifications.json`

One entry per notification, each key optional:

| Key | Meaning |
| --- | --- |
| `icon` | Toast icon (see [Icons](#icons)); none when left out. |
| `duration` | Seconds the toast stays (1 to 30, default 5), times the game's notification time setting. |
| `sound` | `{ "id", "volume", "pitch" }` played with it, or `null` for silence. |

Notifications with a toast: `join_request`, `party_full`, `warp`, `transfer` (requests to you as Party Leader),
`event` (SkyBlock events), `report` (operators). Sound only: `join_sent`, `join_accepted`, `join_denied`,
`invite_accepted` (auto-accepted invite). `toast_background` is the toast sprite (`minecraft:toast/system`).

## Sprites

The frames and row backgrounds are nine-slice (see each `.png.mcmeta`): the corners (`border` pixels) stay as
drawn and the rest stretches; `hud/leader.png` is drawn as is, 8×8. The bar sprites are white by default and
tinted with the theme colors: draw textured bars in your pack and set those colors to `#FFFFFF` to keep their own
colors. The filled part is cut, not squashed, so a gradient stays in place as health goes down.

The health and defense symbols of the party HUD (❤ and ❈) are text by default. Add `hud/icon_health.png` and / or
`hud/icon_defense.png` (8×8, drawn as is) to your pack to draw icons instead. Row backgrounds use a 2 px border, HUD frames a 3 px border; HUD frames are tinted with the HUD
opacity setting, so draw them at full opacity. You can change the size and border in the `.mcmeta`.

The slot hover highlight of the chest menu is the vanilla `minecraft:container/slot_highlight_back` /
`slot_highlight_front` sprite: overriding it also changes every vanilla container.

## Translations

Every text is in `lang/<language>.json`, symbols included: `partyfinder.symbol.full` (✖), `partyfinder.symbol.leader`
(★) and `partyfinder.symbol.member` (-) in member lists, `partyfinder.symbol.health` (❤) and
`partyfinder.symbol.defense` (❈) in the party HUD, `partyfinder.reputation.badge` and `partyfinder.report.badge`. Add a file for another language (e.g. `de_de.json`) with any subset
of the keys; missing ones fall back to English.

## Custom sounds

Declare the sound in `assets/partyfinder/sounds.json`:

```json
{
  "menu_open": { "sounds": ["partyfinder:menu_open"] }
}
```

add `assets/partyfinder/sounds/menu_open.ogg`, then set `"open_sound": { "id": "partyfinder:menu_open" }` in
`menu.json` (or use the id in `listing_menu.json` and `notifications.json`).
