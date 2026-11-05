# PartyFinder

> Pre-alpha: expect bugs and breaking changes.

Client-side Fabric mod (Minecraft 26.2) adding a party finder for Hypixel SkyBlock activities: mining (mineshafts, Crystal Hollows), fishing, slayers, dungeons, Kuudra, Critter Safari and more.

## Usage

- `/pf` or `/partyfinder` (or the `O` key) opens the category menu, then the list of parties
- `/pf create`, `/pf delist`, `/pf accept <player>`, `/pf deny <player>`, `/pf report <player>`, `/pf reconnect`, `/pf reload`
- `/pf go` launches the activity once the party is full (floor queue, island warp...), `/pf leave` leaves a finder party
- `/pf warp` gets you warped: the Party Leader (whoever holds the Hypixel lead) runs `/p warp` for you, so anyone in the party can use it; typing `/p warp` without the lead does the same
- `/pf pt` or `/pf ptme` takes the party lead (any activity, for players who joined through the finder and the Party Owner)
- `/pf mod` opens the moderation menu (operators only)
- `/pf config` opens the settings, `/pf hud` configures the party HUD, `/pf hud edit` moves it

Party data comes from Hypixel's Mod API protocol, built in (the official Hypixel Mod API mod is used instead when installed). No extra mod required.

Settings: `/pf config` (or `config/partyfinder.json`: `autoAcceptInvites`, `notifyJoinRequests`, `notifyPartyFull`, `hud`). The mod only talks to its server while connected to Hypixel, and the server address is fixed.

## Listings

- **Verified requirements**: minimum Cata, class, HOTM, skills or slayer levels, checked by the server against the requester's real profile. Changes the mod sees in game (dungeon class picked, level ups, the Skills menu) reach the server right away instead of waiting for the Hypixel API.
- **Dungeon roles**: the leader picks the wanted classes; members' selected classes fill them and the listing shows the open ones.
- **Kuudra roles**: the leader picks the wanted roles (Stun, DPS, Tank, Healer); players choose one of the open roles when asking to join, the server keeps each role to one accepted player, and the role shows in the request, the stats card and the listing.
- **Tags**: up to 5 tags per listing, picked from the tags of its category (up to 20 per category, e.g. Stridersurfer, Lord Jawbus or Thunder for fishing, S+ or Carry for dungeons). The Tags button of a listing screen filters by tags: parties with at least one of them, the ones matching the most first.
- **Reputation**: +1 recommendations (only for players you played with in the last 24 h) next to reports.
- **History**: past finder parties are kept locally to invite the same players again, republish the same listing, +1 or report.
- **Event alerts**: toast and chat when the Fishing Festival, Spooky Festival, Mining Fiesta or Diana starts (optionally a few minutes before), with the number of open parties and a View button (`/pf event <id>`).
- **Presets**: the last listing is remembered (per activity too) and named presets can be saved; the menu offers a one-click Republish.
- **Join requests and stats**: requests show like Hypixel's Party Finder, with the player's rank, the stat that matters for the category (class and class level for dungeons) and a stats card on hover: Catacombs level, secrets, class levels and completions for dungeons, slayer levels, Kuudra runs, the category's key items (Hyperion, Terminator, Last Breath...) and reputation.
- **Clean party chat** (setting, on by default): in finder parties, Hypixel's framed party messages (joins, leaves, invites, transfers) become one line, joins with the newcomer's stats on hover, and the invite your client auto-accepts is not shown, only "You have joined". Hidden messages are only left out of the chat display: they are still received, logged and seen by other mods.
- **Party Leader and Party Owner**: the Party Leader holds the Hypixel lead, the Party Owner is who listed the party. Members can take the lead with `/pf ptme` (e.g. to warp the party to the mineshaft they found) and ask the Party Leader for a warp with `/pf warp`. The Party Owner keeps control: only they accept join requests (whoever holds the Hypixel lead sends the invite), a member holding the lead hands it back when disconnecting, and in a finder party only the Party Owner can `/p invite`, `/p transfer`, `/p kick`, `/p disband`, `/p promote` or `/p demote` (their invites are relayed to the Party Leader).

## Party HUD

Frames for every member of your Hypixel party: head or live 3D player model with equipment, health/mana bars, health text, defense, island or distance, finder stats, armor and held item. In dungeons it also shows each member's class and who is dead. Position, side, layout, scale, spacing, opacity and every element are configurable (`/pf hud`). Members close to you are read from the world; members further away are shown from the status their own client shares through the server (only between players who are in each other's party).

## Backend

The mod connects to the PartyFinder server. The server is not part of this repository.

## Resource packs

Everything visual lives in `assets/partyfinder/` and can be overridden by a resource pack. [`resourcepack-template/`](resourcepack-template/README.md) is a complete, ready-to-edit pack with every overridable file (`./gradlew resourcePackTemplate` zips it into `build/resourcepack/`):

| File | Purpose |
| --- | --- |
| `partyfinder/menu.json` | Category menu (chest GUI): texture, rows, filler item, open sound, and each slot's action/category/icon. Icons are item ids or custom heads (`texture`, `skin`, `player`), with optional glint and item model |
| `partyfinder/activities.json` | Activity list: `id`, `category`, `icon` (item id or custom head), optional `sprite`, `color`, `max_size`, `options` (floor, tier, island... each value with its launch `commands`), `launch` (`host`, `all`, `none`), `stats` shown per member (`sb`, `cata`, `class`, `hotm`, `kuudra`, `skill_<skill>`, `slayer_<boss>`), `events` it is suggested for; `category_tags` lists the tags of each category. Packs restyle activities and their own launch commands; ids, categories and option values are checked by the server |
| `partyfinder/listing_menu.json` | Chest listing style: texture, rows, listing slots, head or activity icons, buttons (slot and icon), empty and full markers |
| `partyfinder/theme.json` | Text colors (titles, owner, note, slots, status, chat messages and buttons, toasts...), chat prefix and frame lines |
| `partyfinder/notifications.json` | Per notification (join request, party full, warp / transfer request, event, report...): toast icon, duration and sound |
| `textures/gui/sprites/listing/entry*.png` | Listing row backgrounds (nine-slice, see `.mcmeta`) |
| `textures/gui/sprites/hud/frame*.png` | Party HUD frames (nine-slice) |
| `textures/gui/sprites/hud/leader.png` | Party HUD leader mark |
| `textures/gui/sprites/hud/bar_*.png` | Party HUD health, absorption and mana bars (tinted with the theme colors); optional `hud/icon_health.png` / `hud/icon_defense.png` replace the ❤ / ❈ symbols |
| `lang/*.json` | All texts, activity and category names |
