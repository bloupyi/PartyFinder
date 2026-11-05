package fr.bloup.partyfinder.client.hud;

import net.minecraft.resources.Identifier;
import fr.bloup.partyfinder.Partyfinder;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.Activity;
import fr.bloup.partyfinder.client.activity.StatFormat;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.config.PartyFinderConfig.Hud;
import fr.bloup.partyfinder.client.gui.PfSprites;
import fr.bloup.partyfinder.client.hypixel.HypixelBridge;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.MemberStatus;
import fr.bloup.partyfinder.client.party.PartyStatus;
import fr.bloup.partyfinder.client.party.PartySync;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Party frames drawn on the HUD, one per Hypixel party member, with live data from nearby or from the backend. */
public final class PartyHud implements HudElement {
    private static final int PAD = 3;
    private static final int BAR_HEIGHT = 5;
    private static final int MANA_HEIGHT = 3;
    private static final int ICON_SIZE = 8;
    private static final int GEAR_SIZE = 12;
    /** Gear drawn on the health text line: small enough to fit that line without making the frame taller. */
    private static final int INLINE_GEAR_SIZE = 10;
    private static final int LEADER_SIZE = 8;

    /** Everything one frame needs; entity is set when the member is loaded close enough to be read locally. */
    /** shared: status comes from the member's own client (mod installed), so its dead flag is authoritative. */
    public record Member(UUID uuid, String name, boolean leader, boolean self, MemberStatus status, LivingEntity entity,
                         float distance, List<Component> stats, boolean shared) {
    }

    /**
     * Optional rows of one member's frame: a row only exists when that member has something to show in it (no empty
     * stats line when the API has nothing, no mana bar when they do not share mana...), so frames differ in height.
     */
    record Rows(boolean mana, boolean info, boolean stats, boolean gear, boolean gearInline) {
        static Rows of(Hud cfg, Member m) {
            MemberStatus status = m.status();
            boolean mana = cfg.showManaBar && status != null && status.maxMana() > 0;
            boolean info = (cfg.healthText != PartyFinderConfig.HealthText.NONE || cfg.showDefense)
                    && !infoParts(status, cfg, false).isEmpty();
            boolean stats = cfg.showStats && !cfg.compact && !m.stats().isEmpty();
            boolean gear = !shownGear(m, cfg).isEmpty();
            // Gear goes at the end of the health text line when there is room for it there.
            boolean inline = gear && info && status != null && Minecraft.getInstance().font.width(infoText(status, cfg))
                    + 4 + shownGearWidth(m, cfg, INLINE_GEAR_SIZE) <= frameWidth(cfg) - portraitWidth(cfg) - 2 * PAD;
            return new Rows(mana, info, stats, gear, inline);
        }
    }

    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private static Bounds lastBounds = new Bounds(0, 0, 0, 0);

    public static void reset() {
        Portraits.clear();
    }

    public static Bounds lastBounds() {
        return lastBounds;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Hud cfg = PartyFinderConfig.get().hud;
        if (!cfg.enabled || mc.player == null || mc.gui.hud.isHidden()) return;
        if (mc.gui.screen() instanceof HudEditScreen || mc.gui.screen() instanceof HudConfigScreen) return;
        if (cfg.onlyOnSkyBlock && !HypixelBridge.isOnSkyBlock()) return;
        List<Member> members = collect(cfg);
        if (members.isEmpty()) return;
        render(g, members, cfg);
    }

    static List<Member> collect(Hud cfg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return List.of();
        if (cfg.hideWhenSolo && (!HypixelBridge.inParty() || HypixelBridge.members().size() <= 1)) return List.of();
        UUID self = mc.player.getUUID();
        Listing listing = PartySync.currentListing().orElse(null);
        Activity activity = listing == null ? null : Activities.get(listing.activity());
        List<Member> members = new ArrayList<>();
        for (UUID uuid : HypixelBridge.members()) {
            boolean isSelf = uuid.equals(self);
            if (isSelf && !cfg.showSelf) continue;
            if (members.size() >= cfg.maxMembers) break;
            Player nearby = isSelf ? mc.player : mc.level.getPlayerByUUID(uuid);
            float distance = nearby == null || isSelf ? -1 : nearby.distanceTo(mc.player);
            boolean local = nearby != null && (isSelf || distance <= cfg.localRange);
            MemberStatus status = isSelf ? PartyStatus.self() : PartyStatus.remote(uuid).orElse(null);
            boolean shared = status != null;
            if (status == null && nearby != null) {
                // No shared status (member without the mod): the loaded entity still gives health % and gear.
                float max = Math.max(1f, nearby.getMaxHealth());
                status = new MemberStatus(nearby.getGameProfile().name(), nearby.getHealth() / max,
                        0, 0, 0, 0, 0, nearby.getAbsorptionAmount() / max, "", "", MemberStatus.equipmentOf(nearby), false);
            }
            List<Component> stats = List.of();
            if (listing != null) {
                for (Listing.Member member : listing.members()) {
                    if (member.uuid().equals(uuid)) stats = StatFormat.format(activity.stats(), member.stats());
                }
            }
            members.add(new Member(uuid, nameOf(uuid, status, listing), HypixelBridge.role(uuid) == HypixelBridge.Role.LEADER
                    && HypixelBridge.inParty(), isSelf, status, local ? nearby : null, distance, stats, shared));
        }
        return members;
    }

    private static String nameOf(UUID uuid, MemberStatus status, Listing listing) {
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(uuid);
        if (info != null) return info.getProfile().name();
        if (status != null && status.name() != null) return status.name();
        if (listing != null) {
            for (Listing.Member member : listing.members()) {
                if (member.uuid().equals(uuid) && member.name() != null) return member.name();
            }
        }
        return Portraits.profile(uuid).name().orElse("...");
    }

    static int frameWidth(Hud cfg) {
        return (cfg.compact ? 96 : 140) + portraitWidth(cfg);
    }

    static int portraitWidth(Hud cfg) {
        return switch (cfg.portrait) {
            case NONE -> 0;
            case HEAD -> (cfg.compact ? 16 : 24) + PAD;
            case MODEL -> (cfg.compact ? 18 : 26) + PAD;
        };
    }

    /** Portrait box: a square head, or a fixed-height model so it never grows with the frame. */
    static int portraitHeight(Hud cfg) {
        return switch (cfg.portrait) {
            case NONE -> 0;
            case HEAD -> cfg.compact ? 16 : 24;
            case MODEL -> cfg.compact ? 30 : 40;
        };
    }

    /** Height of a frame; a gear row only when the gear does not fit on the health text line. */
    static int frameHeight(Hud cfg, Rows rows) {
        Font font = Minecraft.getInstance().font;
        int h = font.lineHeight;
        if (cfg.showHealthBar) h += 2 + BAR_HEIGHT;
        if (rows.mana()) h += 1 + MANA_HEIGHT;
        if (rows.info()) h += 2 + font.lineHeight;
        if (rows.stats()) h += 1 + font.lineHeight;
        if (rows.gear() && !rows.gearInline()) h += 2 + GEAR_SIZE;
        return Math.max(h, portraitHeight(cfg)) + 2 * PAD;
    }

    public static Bounds bounds(Hud cfg, List<Integer> heights, int screenWidth, int screenHeight) {
        float scale = cfg.scale / 100f;
        int fw = frameWidth(cfg);
        int count = heights.size();
        boolean vertical = cfg.layout == PartyFinderConfig.Layout.VERTICAL;
        int w = vertical ? fw : count * fw + (count - 1) * cfg.spacing;
        int h = vertical ? heights.stream().mapToInt(Integer::intValue).sum() + (count - 1) * cfg.spacing
                : heights.stream().mapToInt(Integer::intValue).max().orElse(0);
        int sw = Math.round(w * scale);
        int sh = Math.round(h * scale);
        int x = switch (cfg.anchor) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> cfg.offsetX;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> screenWidth - sw - cfg.offsetX;
        };
        int y = switch (cfg.anchor) {
            case TOP_LEFT, TOP_RIGHT -> cfg.offsetY;
            case CENTER_LEFT, CENTER_RIGHT -> (screenHeight - sh) / 2 + cfg.offsetY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - sh - cfg.offsetY;
        };
        return new Bounds(x, y, sw, sh);
    }

    static void render(GuiGraphicsExtractor g, List<Member> members, Hud cfg) {
        List<Rows> rows = members.stream().map(m -> Rows.of(cfg, m)).toList();
        List<Integer> heights = rows.stream().map(r -> frameHeight(cfg, r)).toList();
        Bounds bounds = bounds(cfg, heights, g.guiWidth(), g.guiHeight());
        lastBounds = bounds;
        float scale = cfg.scale / 100f;
        int fw = frameWidth(cfg);
        boolean vertical = cfg.layout == PartyFinderConfig.Layout.VERTICAL;
        List<Runnable> models = new ArrayList<>();

        g.pose().pushMatrix();
        g.pose().translate(bounds.x(), bounds.y());
        g.pose().scale(scale, scale);
        int fy = 0;
        for (int i = 0; i < members.size(); i++) {
            int fx = vertical ? 0 : i * (fw + cfg.spacing);
            int fh = heights.get(i);
            Member member = members.get(i);
            drawFrame(g, member, cfg, fx, fy, fw, fh, rows.get(i));
            if (cfg.portrait == PartyFinderConfig.Portrait.MODEL && !Portraits.isBroken(member.uuid())) {
                int boxWidth = portraitWidth(cfg) - PAD;
                int boxHeight = portraitHeight(cfg);
                int boxX = cfg.side == PartyFinderConfig.Side.RIGHT ? fx + fw - PAD - boxWidth : fx + PAD;
                int boxY = fy + (fh - boxHeight) / 2;
                int x0 = Math.round(bounds.x() + boxX * scale);
                int y0 = Math.round(bounds.y() + boxY * scale);
                int x1 = Math.round(bounds.x() + (boxX + boxWidth) * scale);
                int y1 = Math.round(bounds.y() + (boxY + boxHeight) * scale);
                models.add(() -> drawModel(g, member, cfg, x0, y0, x1, y1));
            }
            if (vertical) fy += fh + cfg.spacing;
        }
        g.pose().popMatrix();
        // Entities are rendered in screen space, outside the scaled pose.
        models.forEach(Runnable::run);
    }

    private static void drawFrame(GuiGraphicsExtractor g, Member m, Hud cfg, int x, int y, int w, int h, Rows rows) {
        Font font = Minecraft.getInstance().font;
        int alpha = Math.round(cfg.backgroundOpacity * 2.55f);
        if (alpha > 0) {
            int tint = (alpha << 24) | 0xFFFFFF;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, m.self() ? PfSprites.HUD_FRAME_SELF : PfSprites.HUD_FRAME, x, y, w, h, tint);
        }

        boolean flip = cfg.side == PartyFinderConfig.Side.RIGHT;
        boolean head = cfg.portrait == PartyFinderConfig.Portrait.HEAD
                || cfg.portrait == PartyFinderConfig.Portrait.MODEL && Portraits.isBroken(m.uuid());
        if (head) {
            int size = cfg.compact ? 16 : 24;
            int px = flip ? x + w - PAD - size : x + PAD;
            int py = y + (h - size) / 2;
            PlayerSkin skin = Portraits.tabSkin(m.uuid());
            if (skin != null) PlayerFaceExtractor.extractRenderState(g, skin, px, py, size);
            else PlayerFaceExtractor.extractRenderState(g, Portraits.profile(m.uuid()), px, py, size);
        }

        int cx = flip ? x + PAD : x + PAD + portraitWidth(cfg);
        int right = flip ? x + w - PAD - portraitWidth(cfg) : x + w - PAD;
        int cy = y + PAD;
        boolean shadow = cfg.textShadow;
        MemberStatus status = m.status();
        DungeonTab.Info dungeon = cfg.showDungeonClass ? DungeonTab.of(m.name()) : null;
        // The member's own client is authoritative; without the mod, fall back to the tab list, then the chat.
        boolean dead = DungeonTab.inDungeon() && (m.shared() ? status.dead()
                : dungeon != null && dungeon.dead() || PartyStatus.diedInChat(m.name()));

        String side = "";
        if (cfg.showDistance && m.distance() >= 0) side = Math.round(m.distance()) + "m";
        else if (cfg.showLocation && status != null) side = locationLabel(status);
        int sideWidth = side.isEmpty() ? 0 : font.width(side);
        if (!side.isEmpty()) g.text(font, side, flip ? cx : right - sideWidth, cy, Activities.color("hud_info", 0xAAAAAA), shadow);
        if (cfg.showName) {
            // Leader mark as a sprite rather than a glyph: fonts (and server resource packs) do not all have one.
            boolean leader = cfg.showLeader && m.leader();
            int icon = leader ? LEADER_SIZE + 2 : 0;
            String tag = dungeon != null && dungeon.dungeonClass() != null
                    ? "[" + Character.toUpperCase(dungeon.dungeonClass().charAt(0)) + "] " : "";
            String suffix = dead ? " ☠" : "";
            int room = right - sideWidth - 4 - cx - icon - font.width(tag);
            String name = clip(font, m.name() + suffix, room);
            int color = dead ? Activities.color("hud_dead", 0x777777)
                    : leader ? Activities.color("hud_leader", 0xFFAA00)
                    : m.self() ? Activities.color("hud_self", 0x55FFFF) : Activities.color("hud_name", 0xFFFFFF);
            int width = icon + font.width(tag) + font.width(name);
            int nx = flip ? right - width : cx;
            if (leader) g.blitSprite(RenderPipelines.GUI_TEXTURED, PfSprites.HUD_LEADER, nx, cy, LEADER_SIZE, LEADER_SIZE);
            if (!tag.isEmpty()) {
                g.text(font, tag, nx + icon, cy, Activities.color("class_" + dungeon.dungeonClass(), 0xFFFFFF), shadow);
            }
            g.text(font, name, nx + icon + font.width(tag), cy, color, shadow);
        }
        cy += font.lineHeight;

        float absorption = dead || status == null ? 0 : status.absorptionRatio();
        if (cfg.showHealthBar) {
            cy += 2;
            float ratio = dead || status == null ? 0 : status.healthRatio();
            int color = dead || status == null ? Activities.color("hud_unknown", 0x555555)
                    : ratio > 0.5f ? Activities.color("hud_health_high", 0x55FF55)
                    : ratio > 0.25f ? Activities.color("hud_health_mid", 0xFFFF55) : Activities.color("hud_health_low", 0xFF5555);
            healthBar(g, cx, cy, right - cx, status == null && !dead ? 1 : ratio, absorption, color, flip);
            cy += BAR_HEIGHT;
        }
        if (rows.mana()) {
            cy += 1;
            bar(g, PfSprites.HUD_BAR_MANA, cx, cy, right - cx, MANA_HEIGHT, (float) status.mana() / status.maxMana(),
                    Activities.color("hud_mana", 0x5555FF), flip);
            cy += MANA_HEIGHT;
        }
        if (rows.info()) {
            cy += 2;
            int room = right - cx - (rows.gearInline() ? shownGearWidth(m, cfg, INLINE_GEAR_SIZE) + 4 : 0);
            drawInfo(g, font, infoParts(status, cfg, absorption > 0), cx, right, room, cy, flip, shadow);
            if (rows.gearInline()) {
                // Opposite end of the text: the right edge, or the left one when the frame is flipped.
                int start = flip ? cx : right - shownGearWidth(m, cfg, INLINE_GEAR_SIZE);
                drawGear(g, m, cfg, start, cy - 1, INLINE_GEAR_SIZE);
            }
            cy += font.lineHeight;
        }
        if (rows.stats()) {
            cy += 1;
            String stats = String.join(" · ", m.stats().stream().map(Component::getString).toList());
            stats = clip(font, stats, right - cx);
            if (!stats.isEmpty()) g.text(font, stats, flip ? right - font.width(stats) : cx, cy, Activities.color("stat", 0x55FFFF), shadow);
            cy += font.lineHeight;
        }
        if (rows.gear() && !rows.gearInline()) {
            cy += 2;
            drawGear(g, m, cfg, flip ? right - shownGearWidth(m, cfg, GEAR_SIZE) : cx, cy, GEAR_SIZE);
        }
    }

    private static int shownGearWidth(Member m, Hud cfg, int size) {
        int count = shownGear(m, cfg).size();
        return count == 0 ? 0 : count * (size + 1) - 1;
    }

    private static List<MemberStatus.Gear> shownGear(Member m, Hud cfg) {
        if (m.status() == null) return List.of();
        return m.status().equipment().stream()
                .filter(gear -> "mainhand".equals(gear.slot()) ? cfg.showHeldItem : cfg.showArmor)
                .toList();
    }

    /** Draws the shown gear left to right from x, in slot order (helmet to boots, then held item). */
    private static void drawGear(GuiGraphicsExtractor g, Member m, Hud cfg, int x, int y, int size) {
        int gx = x;
        for (MemberStatus.Gear gear : shownGear(m, cfg)) {
            g.pose().pushMatrix();
            g.pose().translate(gx, y);
            g.pose().scale(size / 16f, size / 16f);
            g.item(stackOf(m, gear), 0, 0);
            g.pose().popMatrix();
            gx += size + 1;
        }
    }

    /**
     * The real item when the member is loaded nearby (or is us), so resource packs retexture it exactly like in the
     * world; otherwise the item rebuilt from what their client shared.
     */
    private static ItemStack stackOf(Member m, MemberStatus.Gear gear) {
        EquipmentSlot slot = gear.equipmentSlot();
        if (m.entity() != null && slot != null) {
            ItemStack real = m.entity().getItemBySlot(slot);
            if (!real.isEmpty()) return real;
        }
        return gear.toStack();
    }

    private static void drawModel(GuiGraphicsExtractor g, Member m, Hud cfg, int x0, int y0, int x1, int y1) {
        LivingEntity entity = m.entity();
        if (entity == null) entity = Portraits.stand(m.uuid(), m.status() == null ? null : m.status().equipment());
        if (entity == null) return;
        // Fixed box height, so the model keeps the same size whatever rows the frame shows.
        int size = Math.max(4, Math.round((y1 - y0) / 2.1f));
        float centerX = (x0 + x1) / 2f;
        float centerY = (y0 + y1) / 2f;
        // The inventory renderer turns the model toward the "mouse"; a fixed point gives a fixed angle.
        float mouseX = centerX - (float) Math.tan(Math.toRadians(cfg.modelRotation)) * 40f;
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, size, 0.0625f, mouseX, centerY - 10, entity);
        } catch (RuntimeException e) {
            // A model that cannot render must never take the game down: fall back to the head for this member.
            Partyfinder.LOGGER.warn("Party HUD model failed for {}, using head instead", m.name(), e);
            Portraits.markBroken(m.uuid());
        }
    }

    /** Text then its symbol: the pack's icon sprite when there is one, else the symbol text (❤, ❈). */
    private record InfoPart(String text, String symbol, Identifier icon, int color) {
        InfoPart(String text, int color) {
            this(text, "", null, color);
        }

        String full() {
            return text + symbol;
        }

        int width(Font font) {
            return icon != null ? font.width(text) + ICON_SIZE + 1 : font.width(full());
        }
    }

    /** Health text (gold while absorbing) and defense; "no data" when the member shares nothing. */
    private static List<InfoPart> infoParts(MemberStatus status, Hud cfg, boolean absorbing) {
        int info = Activities.color("hud_info", 0xAAAAAA);
        if (status == null) {
            return List.of(new InfoPart(Component.translatable("partyfinder.hud.no_data").getString(),
                    Activities.color("hud_unknown", 0x808080)));
        }
        List<InfoPart> parts = new ArrayList<>();
        int percent = Math.round((status.healthRatio() + status.absorptionRatio()) * 100);
        String heart = Component.translatable("partyfinder.symbol.health").getString();
        String health = switch (cfg.healthText) {
            case NUMBERS -> status.hasNumbers()
                    ? compact(status.displayedHealth()) + "/" + compact(status.maxHealth()) : percent + "%";
            case PERCENT -> percent + "%";
            case NONE -> null;
        };
        if (health != null) {
            parts.add(new InfoPart(health, heart, PfSprites.healthIcon(), absorbing ? Activities.color("hud_absorption", 0xFFAA00) : info));
        }
        if (cfg.showDefense && status.defense() > 0) {
            parts.add(new InfoPart(compact(status.defense()), Component.translatable("partyfinder.symbol.defense").getString(),
                    PfSprites.defenseIcon(), info));
        }
        return parts;
    }

    private static String infoText(MemberStatus status, Hud cfg) {
        return String.join("  ", infoParts(status, cfg, false).stream().map(InfoPart::full).toList());
    }

    private static void drawInfo(GuiGraphicsExtractor g, Font font, List<InfoPart> parts, int cx, int right, int room, int y,
                                 boolean flip, boolean shadow) {
        if (parts.isEmpty()) return;
        int gap = font.width("  ");
        int total = parts.stream().mapToInt(p -> p.width(font)).sum() + gap * (parts.size() - 1);
        if (total > room) {
            String joined = String.join("  ", parts.stream().map(InfoPart::full).toList());
            String clipped = clip(font, joined, room);
            g.text(font, clipped, flip ? right - font.width(clipped) : cx, y, parts.getFirst().color(), shadow);
            return;
        }
        int x = flip ? right - total : cx;
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) x += gap;
            InfoPart part = parts.get(i);
            if (part.icon() != null) {
                g.text(font, part.text(), x, y, part.color(), shadow);
                x += font.width(part.text()) + 1;
                g.blitSprite(RenderPipelines.GUI_TEXTURED, part.icon(), x, y, ICON_SIZE, ICON_SIZE);
                x += ICON_SIZE;
            } else {
                g.text(font, part.full(), x, y, part.color(), shadow);
                x += font.width(part.full());
            }
        }
    }

    private static String locationLabel(MemberStatus status) {
        if (!status.map().isEmpty()) return status.map();
        if (status.location().isEmpty()) return "";
        return Component.translatableWithFallback("partyfinder.island." + status.location(),
                status.location().replace('_', ' ')).getString();
    }

    private static String compact(int value) {
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000f);
        if (value >= 10_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000f);
        return String.valueOf(value);
    }

    /**
     * Health then absorption (gold) in one bar; when both exceed the bar they share it in proportion, so SkyBlock's
     * absorption above max health still shows as a gold end.
     */
    private static void healthBar(GuiGraphicsExtractor g, int x, int y, int w, float health, float absorption, int color,
                                  boolean reverse) {
        if (absorption <= 0) {
            bar(g, PfSprites.HUD_BAR_HEALTH, x, y, w, BAR_HEIGHT, health, color, reverse);
            return;
        }
        float total = Math.max(1f, health + absorption);
        int healthWidth = Math.round(w * Math.clamp(health / total, 0f, 1f));
        int absorptionWidth = Math.min(w - healthWidth, Math.round(w * absorption / total));
        bar(g, PfSprites.HUD_BAR_HEALTH, x, y, w, BAR_HEIGHT, health / total, color, reverse);
        int start = reverse ? x + w - healthWidth - absorptionWidth : x + healthWidth;
        fillPart(g, PfSprites.HUD_BAR_ABSORPTION, x, y, w, BAR_HEIGHT, start, start + absorptionWidth,
                Activities.color("hud_absorption", 0xFFAA00));
    }

    /**
     * Bar background then the filled part. Both are sprites (hud/bar_*.png, white by default) tinted with the theme
     * color; the fill sprite is drawn at full width and cut, so a textured pack bar is not squashed.
     */
    private static void bar(GuiGraphicsExtractor g, Identifier fill, int x, int y, int w, int h, float ratio, int color,
                            boolean reverse) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, PfSprites.HUD_BAR_BACKGROUND, x, y, w, h,
                Activities.color("hud_bar_background", 0x000000) & 0x80FFFFFF);
        int filled = Math.round(w * Math.clamp(ratio, 0f, 1f));
        if (filled <= 0) return;
        if (reverse) fillPart(g, fill, x, y, w, h, x + w - filled, x + w, color);
        else fillPart(g, fill, x, y, w, h, x, x + filled, color);
    }

    private static void fillPart(GuiGraphicsExtractor g, Identifier fill, int x, int y, int w, int h, int from, int to, int color) {
        if (to <= from) return;
        g.enableScissor(from, y, to, y + h);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, fill, x, y, w, h, color);
        g.disableScissor();
    }

    private static String clip(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }
}
