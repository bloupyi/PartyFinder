# PartyFinder

> Pre-alpha: expect bugs and breaking changes.

Client-side Fabric mod (Minecraft 26.2) adding a party finder for Hypixel SkyBlock activities: mining (mineshafts, Crystal Hollows), fishing, slayers, dungeons, Kuudra and more.

## Usage

- `/pf` or `/partyfinder` (or the `O` key) opens the category menu, then the list of parties
- `/pf create`, `/pf delist`, `/pf accept <player>`, `/pf deny <player>`, `/pf report <player>`, `/pf reconnect`, `/pf reload`
- `/pf go` launches the activity once the party is full (floor queue, island warp...), `/pf leave` leaves a finder party
- `/pf mod` opens the moderation menu (operators only)
- `/pf config` opens the settings, `/pf hud` configures the party HUD, `/pf hud edit` moves it

Party data comes from Hypixel's Mod API protocol, built in (the official Hypixel Mod API mod is used instead when installed). No extra mod required.

Settings: `/pf config` (or `config/partyfinder.json`: `autoAcceptInvites`, `notifyJoinRequests`, `notifyPartyFull`, `hud`). The mod only talks to its server while connected to Hypixel, and the server address is fixed.

## Listings

- **Verified requirements**: minimum Cata, class, HOTM, skills or slayer levels, checked by the server against the requester's real profile.
- **Dungeon roles**: the leader picks the wanted classes; members' selected classes fill them and the listing shows the open ones.
- **Reputation**: +1 recommendations (only for players you played with in the last 24 h) next to reports.
- **History**: past finder parties are kept locally to invite the same players again, republish the same listing, +1 or report.
- **Event alerts**: toast and chat when the Fishing Festival, Spooky Festival, Mining Fiesta or Diana starts (optionally a few minutes before), with the number of open parties and a View button (`/pf event <id>`).
- **Presets**: the last listing is remembered (per activity too) and named presets can be saved; the menu offers a one-click Republish.

## Party HUD

Frames for every member of your Hypixel party: head or live 3D player model with equipment, health/mana bars, health text, defense, island or distance, finder stats, armor and held item. In dungeons it also shows each member's class and who is dead. Position, side, layout, scale, spacing, opacity and every element are configurable (`/pf hud`). Members close to you are read from the world; members further away are shown from the status their own client shares through the server (only between players who are in each other's party).

## Backend

The mod connects to the PartyFinder server. The server is not part of this repository.

## Resource packs

Everything visual lives in `assets/partyfinder/` and can be overridden by a resource pack:

| File | Purpose |
| --- | --- |
| `partyfinder/menu.json` | Category menu (chest GUI): texture, rows, filler item, and each slot's action/category/icon |
| `partyfinder/activities.json` | Activity list: `id`, `category`, `icon` (item id), optional `sprite`, `color`, `max_size`, `options` (floor, tier, island... each value with its launch `commands`), `launch` (`host`, `all`, `none`), `stats` shown per member (`sb`, `cata`, `class`, `hotm`, `kuudra`, `skill_<skill>`, `slayer_<boss>`) |
| `partyfinder/theme.json` | Text colors (titles, owner, note, slots, status...) |
| `textures/gui/sprites/listing/entry*.png` | Listing row backgrounds (nine-slice, see `.mcmeta`) |
| `textures/gui/sprites/icon/party.png` | Menu button icon |
| `textures/gui/sprites/hud/frame*.png` | Party HUD frames (nine-slice) |
| `lang/*.json` | All texts, activity and category names |
