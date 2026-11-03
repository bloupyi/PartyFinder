package fr.bloup.partyfinder.client.hud;

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
    private static final int GEAR_SIZE = 12;

    /** Everything one frame needs; entity is set when the member is loaded close enough to be read locally. */
    public record Member(UUID uuid, String name, boolean leader, boolean self, MemberStatus status, LivingEntity entity,
                         float distance, List<Component> stats) {
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
            if (status == null && local) {
                status = new MemberStatus(nearby.getGameProfile().name(), nearby.getHealth() / Math.max(1f, nearby.getMaxHealth()),
                        0, 0, 0, 0, 0, "", "", MemberStatus.equipmentOf(nearby));
            }
            List<Component> stats = List.of();
            if (listing != null) {
                for (Listing.Member member : listing.members()) {
                    if (member.uuid().equals(uuid)) stats = StatFormat.format(activity.stats(), member.stats());
                }
            }
            members.add(new Member(uuid, nameOf(uuid, status, listing), HypixelBridge.role(uuid) == HypixelBridge.Role.LEADER
                    && HypixelBridge.inParty(), isSelf, status, local ? nearby : null, local ? distance : -1, stats));
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

    static int frameHeight(Hud cfg) {
        Font font = Minecraft.getInstance().font;
        int h = font.lineHeight;
        if (cfg.showHealthBar) h += 2 + BAR_HEIGHT;
        if (cfg.showManaBar) h += 1 + MANA_HEIGHT;
        if (cfg.healthText != PartyFinderConfig.HealthText.NONE || cfg.showDefense) h += 2 + font.lineHeight;
        if (cfg.showStats && !cfg.compact) h += 1 + font.lineHeight;
        if (cfg.showArmor || cfg.showHeldItem) h += 2 + GEAR_SIZE;
        int portrait = switch (cfg.portrait) {
            case NONE -> 0;
            case HEAD -> cfg.compact ? 16 : 24;
            case MODEL -> cfg.compact ? 30 : 40;
        };
        return Math.max(h, portrait) + 2 * PAD;
    }

    public static Bounds bounds(Hud cfg, int count, int screenWidth, int screenHeight) {
        float scale = cfg.scale / 100f;
        int fw = frameWidth(cfg);
        int fh = frameHeight(cfg);
        int w = cfg.layout == PartyFinderConfig.Layout.VERTICAL ? fw : count * fw + (count - 1) * cfg.spacing;
        int h = cfg.layout == PartyFinderConfig.Layout.VERTICAL ? count * fh + (count - 1) * cfg.spacing : fh;
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
        Bounds bounds = bounds(cfg, members.size(), g.guiWidth(), g.guiHeight());
        lastBounds = bounds;
        float scale = cfg.scale / 100f;
        int fw = frameWidth(cfg);
        int fh = frameHeight(cfg);
        List<Runnable> models = new ArrayList<>();

        g.pose().pushMatrix();
        g.pose().translate(bounds.x(), bounds.y());
        g.pose().scale(scale, scale);
        for (int i = 0; i < members.size(); i++) {
            int fx = cfg.layout == PartyFinderConfig.Layout.VERTICAL ? 0 : i * (fw + cfg.spacing);
            int fy = cfg.layout == PartyFinderConfig.Layout.VERTICAL ? i * (fh + cfg.spacing) : 0;
            Member member = members.get(i);
            drawFrame(g, member, cfg, fx, fy, fw, fh);
            if (cfg.portrait == PartyFinderConfig.Portrait.MODEL && !Portraits.isBroken(member.uuid())) {
                int x0 = Math.round(bounds.x() + (fx + PAD) * scale);
                int y0 = Math.round(bounds.y() + (fy + PAD) * scale);
                int x1 = Math.round(bounds.x() + (fx + portraitWidth(cfg)) * scale);
                int y1 = Math.round(bounds.y() + (fy + fh - PAD) * scale);
                models.add(() -> drawModel(g, member, cfg, x0, y0, x1, y1));
            }
        }
        g.pose().popMatrix();
        // Entities are rendered in screen space, outside the scaled pose.
        models.forEach(Runnable::run);
    }

    private static void drawFrame(GuiGraphicsExtractor g, Member m, Hud cfg, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int alpha = Math.round(cfg.backgroundOpacity * 2.55f);
        if (alpha > 0) {
            int tint = (alpha << 24) | 0xFFFFFF;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, m.self() ? PfSprites.HUD_FRAME_SELF : PfSprites.HUD_FRAME, x, y, w, h, tint);
        }

        int px = x + PAD;
        int py = y + PAD;
        boolean head = cfg.portrait == PartyFinderConfig.Portrait.HEAD
                || cfg.portrait == PartyFinderConfig.Portrait.MODEL && Portraits.isBroken(m.uuid());
        if (head) {
            int size = cfg.compact ? 16 : 24;
            PlayerSkin skin = Portraits.tabSkin(m.uuid());
            if (skin != null) PlayerFaceExtractor.extractRenderState(g, skin, px, py, size);
            else PlayerFaceExtractor.extractRenderState(g, Portraits.profile(m.uuid()), px, py, size);
        }

        int cx = x + PAD + portraitWidth(cfg);
        int right = x + w - PAD;
        int cy = y + PAD;
        boolean shadow = cfg.textShadow;
        MemberStatus status = m.status();

        String side = "";
        if (cfg.showDistance && m.distance() >= 0) side = Math.round(m.distance()) + "m";
        else if (cfg.showLocation && status != null) side = locationLabel(status);
        int sideWidth = side.isEmpty() ? 0 : font.width(side);
        if (!side.isEmpty()) g.text(font, side, right - sideWidth, cy, Activities.color("hud_info", 0xAAAAAA), shadow);
        if (cfg.showName) {
            String prefix = cfg.showLeader && m.leader() ? "★ " : "";
            String name = clip(font, prefix + m.name(), right - sideWidth - 4 - cx);
            int color = m.leader() && cfg.showLeader ? Activities.color("hud_leader", 0xFFAA00)
                    : m.self() ? Activities.color("hud_self", 0x55FFFF) : Activities.color("hud_name", 0xFFFFFF);
            g.text(font, name, cx, cy, color, shadow);
        }
        cy += font.lineHeight;

        if (cfg.showHealthBar) {
            cy += 2;
            float ratio = status == null ? 0 : status.healthRatio();
            int color = status == null ? Activities.color("hud_unknown", 0x555555)
                    : ratio > 0.5f ? Activities.color("hud_health_high", 0x55FF55)
                    : ratio > 0.25f ? Activities.color("hud_health_mid", 0xFFFF55) : Activities.color("hud_health_low", 0xFF5555);
            bar(g, cx, cy, right - cx, BAR_HEIGHT, status == null ? 1 : ratio, color);
            cy += BAR_HEIGHT;
        }
        if (cfg.showManaBar) {
            cy += 1;
            boolean known = status != null && status.maxMana() > 0;
            bar(g, cx, cy, right - cx, MANA_HEIGHT, known ? (float) status.mana() / status.maxMana() : 0,
                    Activities.color("hud_mana", 0x5555FF));
            cy += MANA_HEIGHT;
        }
        if (cfg.healthText != PartyFinderConfig.HealthText.NONE || cfg.showDefense) {
            cy += 2;
            String info = infoLine(status, cfg);
            g.text(font, clip(font, info, right - cx), cx, cy, status == null
                    ? Activities.color("hud_unknown", 0x808080) : Activities.color("hud_info", 0xAAAAAA), shadow);
            cy += font.lineHeight;
        }
        if (cfg.showStats && !cfg.compact) {
            cy += 1;
            String stats = String.join(" · ", m.stats().stream().map(Component::getString).toList());
            if (!stats.isEmpty()) g.text(font, clip(font, stats, right - cx), cx, cy, Activities.color("stat", 0x55FFFF), shadow);
            cy += font.lineHeight;
        }
        if ((cfg.showArmor || cfg.showHeldItem) && status != null) {
            cy += 2;
            int gx = cx;
            for (MemberStatus.Gear gear : status.equipment()) {
                boolean held = "mainhand".equals(gear.slot());
                if (held ? !cfg.showHeldItem : !cfg.showArmor) continue;
                if (gx + GEAR_SIZE > right) break;
                ItemStack stack = gear.toStack();
                g.pose().pushMatrix();
                g.pose().translate(gx, cy);
                g.pose().scale(GEAR_SIZE / 16f, GEAR_SIZE / 16f);
                g.item(stack, 0, 0);
                g.pose().popMatrix();
                gx += GEAR_SIZE + 1;
            }
        }
    }

    private static void drawModel(GuiGraphicsExtractor g, Member m, Hud cfg, int x0, int y0, int x1, int y1) {
        LivingEntity entity = m.entity();
        if (entity == null) entity = Portraits.stand(m.uuid(), m.status() == null ? null : m.status().equipment());
        if (entity == null) return;
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

    private static String infoLine(MemberStatus status, Hud cfg) {
        if (status == null) return Component.translatable("partyfinder.hud.no_data").getString();
        List<String> parts = new ArrayList<>();
        switch (cfg.healthText) {
            case NUMBERS -> parts.add(status.hasNumbers()
                    ? compact(status.health()) + "/" + compact(status.maxHealth()) + "❤"
                    : Math.round(status.healthRatio() * 100) + "%❤");
            case PERCENT -> parts.add(Math.round(status.healthRatio() * 100) + "%❤");
            case NONE -> {
            }
        }
        if (cfg.showDefense && status.defense() > 0) parts.add(compact(status.defense()) + "❈");
        return String.join("  ", parts);
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

    private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float ratio, int color) {
        g.fill(x, y, x + w, y + h, Activities.color("hud_bar_background", 0x000000) & 0x80FFFFFF);
        int filled = Math.round(w * Math.clamp(ratio, 0f, 1f));
        if (filled > 0) g.fill(x, y, x + filled, y + h, color);
    }

    private static String clip(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }
}
