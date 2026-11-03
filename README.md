# PartyFinder

> Pre-alpha: expect bugs and breaking changes.

Client-side Fabric mod (Minecraft 26.2) adding a party finder for Hypixel SkyBlock activities: mining (mineshafts, Crystal Hollows), fishing, slayers, dungeons, Kuudra and more.

## Usage

- `/pf` or `/partyfinder` (or the `O` key) opens the category menu, then the list of parties
- `/pf create`, `/pf delist`, `/pf accept <player>`, `/pf deny <player>`, `/pf report <player>`, `/pf reconnect`, `/pf reload`
- `/pf go` launches the activity once the party is full (floor queue, island warp...), `/pf leave` leaves a finder party
- `/pf mod` opens the moderation menu (operators only)

Requires the [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api) mod: the party size and members are read from your real Hypixel party.
- A button next to every container menu opens it too

Config: `config/partyfinder.json` (`backendUrl`, `hypixelOnly`, `showMenuButton`, `autoAcceptInvites`, `notifyJoinRequests`).

## Backend

The mod connects to a PartyFinder server (`backendUrl` in the config). The server is not part of this repository.

## Resource packs

Everything visual lives in `assets/partyfinder/` and can be overridden by a resource pack:

| File | Purpose |
| --- | --- |
| `partyfinder/menu.json` | Category menu (chest GUI): texture, rows, filler item, and each slot's action/category/icon |
| `partyfinder/activities.json` | Activity list: `id`, `category`, `icon` (item id), optional `sprite`, `color`, `max_size`, `options` (floor, tier, island... each value with its launch `commands`), `launch` (`host`, `all`, `none`), `stats` shown per member (`sb`, `cata`, `class`, `hotm`, `kuudra`, `skill_<skill>`, `slayer_<boss>`) |
| `partyfinder/theme.json` | Text colors (titles, owner, note, slots, status...) |
| `textures/gui/sprites/listing/entry*.png` | Listing row backgrounds (nine-slice, see `.mcmeta`) |
| `textures/gui/sprites/icon/party.png` | Menu button icon |
| `lang/*.json` | All texts, activity and category names |
