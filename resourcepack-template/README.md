# PartyFinder resource pack template

This folder is a complete resource pack: every file the mod lets you override, at its default value. Copy the
folder into `.minecraft/resourcepacks/` (or run `./gradlew resourcePackTemplate` for a zip in
`build/resourcepack/`), edit what you want and enable the pack.

A file in the pack replaces the mod's file entirely, so once you are done delete the files you did not change:
an untouched `activities.json` or lang file would otherwise hide the activities and texts added by later versions
of the mod.

Mod developers: the assets here are a copy of `src/client/resources/assets/partyfinder/`; refresh them with
`./gradlew updateResourcePackTemplate` (`./gradlew check` fails when they are out of date).

`menu.json`, `activities.json` and `theme.json` are re-read every time the PartyFinder menu opens; textures,
sounds and translations need `F3 + T`.

## Files

```
assets/partyfinder/
├── partyfinder/
│   ├── menu.json          category menu (chest GUI)
│   ├── activities.json    activities, categories, options, launch commands, stats
│   └── theme.json         text colors
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
        └── leader.png             party leader mark before the name (8×8)
```

## `partyfinder/menu.json`

| Key | Meaning |
| --- | --- |
| `texture` | Container texture, 256×256 laid out like `minecraft:textures/gui/container/generic_54.png` (176 px wide: title bar and slot rows from the top, bottom border at y = 215, the "My party" panel reuses the top bar and the row strip at y = 128). |
| `rows` | Number of slot rows, 1 to 6. |
| `filler` | Item drawn in empty slots (`""` or remove the key for none). |
| `open_sound` | `{ "id", "volume", "pitch" }` played when the menu opens; remove the key for silence. `/pf sounds` prints the ids of sounds the server plays. |
| `slots[]` | `slot` (0 to rows × 9 − 1), `action`, `icon` (item id), and `category` for the `category` action. |

Slot actions: `category` (opens one category, needs `category`), `all` (every listing), `create`, `requests`,
`history`, `settings`, `moderation`, `reconnect`, `close`.

## `partyfinder/activities.json`

| Key | Meaning |
| --- | --- |
| `id` | Activity id, shared with other players through the server. |
| `category` | Category id, used by `menu.json` and translated as `partyfinder.category.<id>`. |
| `icon` | Item id shown for the activity. |
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

Names come from the lang files: `partyfinder.activity.<id>`, `partyfinder.category.<category>`,
`partyfinder.option.<option>` and `partyfinder.option.<option>.<value>`.

## `partyfinder/theme.json`

`colors` maps each key to a `#RRGGBB` color. Listing: `title`, `label`, `empty`, `owner`, `note`,
`requirements`, `age`, `slots_open`, `slots_full`, `stat`, `leader`. Connection status: `status_online`,
`status_connecting`, `status_offline`. Chest menu: `menu_title`, `menu_status_online`, `menu_status_connecting`,
`menu_status_offline`. Moderation: `reports_some`, `reports_none`, `report_limit`, `banned`. Party HUD:
`hud_name`, `hud_self`, `hud_leader`, `hud_info`, `hud_unknown`, `hud_health_high`, `hud_health_mid`,
`hud_health_low`, `hud_mana`, `hud_absorption` (health text and bar end while absorbing), `hud_bar_background`, `hud_dead`. Dungeon classes: `class_healer`, `class_mage`,
`class_berserk`, `class_archer`, `class_tank`.

## Sprites

The frames and row backgrounds are nine-slice (see each `.png.mcmeta`): the corners (`border` pixels) stay as
drawn and the rest stretches; `hud/leader.png` is drawn as is, 8×8. Row backgrounds use a 2 px border, HUD frames a 3 px border; HUD frames are tinted with the HUD
opacity setting, so draw them at full opacity. You can change the size and border in the `.mcmeta`.

The slot hover highlight of the chest menu is the vanilla `minecraft:container/slot_highlight_back` /
`slot_highlight_front` sprite: overriding it also changes every vanilla container.

## Translations

Every text is in `lang/<language>.json`. Add a file for another language (e.g. `de_de.json`) with any subset
of the keys; missing ones fall back to English.

## Custom sounds

Declare the sound in `assets/partyfinder/sounds.json`:

```json
{
  "menu_open": { "sounds": ["partyfinder:menu_open"] }
}
```

add `assets/partyfinder/sounds/menu_open.ogg`, then set `"open_sound": { "id": "partyfinder:menu_open" }` in
`menu.json`.
