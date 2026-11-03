package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.Activity;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.ReportSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ListingList extends ObjectSelectionList<ListingList.Entry> {
    public static final int ROW_HEIGHT = 40;
    private static final int LINE_GAP = 2;

    private final Consumer<Listing> onJoin;
    private final Runnable onSelect;

    public ListingList(Minecraft mc, int width, int height, int y, Consumer<Listing> onJoin, Runnable onSelect) {
        super(mc, width, height, y, ROW_HEIGHT);
        this.onJoin = onJoin;
        this.onSelect = onSelect;
    }

    public void setListings(List<Listing> listings) {
        String selectedId = getSelected() != null ? getSelected().listing.id() : null;
        List<Entry> entries = new ArrayList<>();
        for (Listing listing : listings) entries.add(new Entry(listing));
        replaceEntries(entries);
        entries.stream().filter(e -> e.listing.id().equals(selectedId)).findFirst().ifPresent(this::setSelected);
    }

    public Listing selectedListing() {
        return getSelected() != null ? getSelected().listing : null;
    }

    @Override
    public void setSelected(Entry entry) {
        super.setSelected(entry);
        onSelect.run();
    }

    @Override
    public int getRowWidth() {
        return Math.min(320, width - 20);
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> {
        private final Listing listing;

        Entry(Listing listing) {
            this.listing = listing;
        }

        @Override
        public Component getNarration() {
            return Component.translatable("partyfinder.listing.narration",
                    Activities.get(listing.activity()).name(), listing.owner().name(), listing.size(), listing.maxSize());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            setSelected(this);
            if (doubleClick) onJoin.accept(listing);
            return true;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = minecraft.font;
            Activity activity = Activities.get(listing.activity());
            int x = getContentX();
            int y = getContentY();
            int w = getContentWidth();
            int h = getContentHeight();
            boolean own = PartyFinderState.self().map(s -> s.uuid().equals(listing.owner().uuid())).orElse(false);

            g.blitSprite(RenderPipelines.GUI_TEXTURED, own ? PfSprites.ENTRY_OWN : hovered ? PfSprites.ENTRY_HIGHLIGHTED : PfSprites.ENTRY, x, y, w, h);

            int iconY = y + (h - 16) / 2;
            if (activity.sprite() != null) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, activity.sprite(), x + 4, iconY, 16, 16);
            } else {
                g.item(activity.icon(), x + 4, iconY);
            }

            int textX = x + 26;
            int right = x + w - 4;
            int lineStep = font.lineHeight + LINE_GAP;
            int line1 = y + (h - 3 * font.lineHeight - 2 * LINE_GAP) / 2;
            int line2 = line1 + lineStep;
            int line3 = line2 + lineStep;

            String slots = listing.size() + "/" + listing.maxSize();
            int slotsColor = Activities.color(listing.isFull() ? "slots_full" : "slots_open", listing.isFull() ? 0xFF5555 : 0x55FF55);
            int slotsWidth = font.width(slots);
            g.text(font, slots, right - slotsWidth, line1, slotsColor);

            int titleRoom = right - slotsWidth - 6 - textX;
            String title = clip(font, activity.name().getString(), titleRoom);
            g.text(font, title, textX, line1, 0xFF000000 | activity.color());
            ReportSummary reports = listing.ownerReports();
            String badge = Component.translatable("partyfinder.report.badge", reports.count()).getString();
            int badgeWidth = font.width(badge);
            int ownerX = textX + font.width(title) + 6;
            int ownerRoom = right - slotsWidth - 6 - ownerX - badgeWidth - 4;
            if (ownerRoom > font.width("...")) {
                String owner = clip(font, listing.owner().name(), ownerRoom);
                g.text(font, owner, ownerX, line1, Activities.color("owner", 0xAAAAAA));
                int badgeColor = reports.count() > 0 ? Activities.color("reports_some", 0xFF5555) : Activities.color("reports_none", 0x55FF55);
                g.text(font, badge, ownerX + font.width(owner) + 4, line1, badgeColor);
            }

            String note = listing.note().isEmpty() ? Component.translatable("partyfinder.listing.no_note").getString() : listing.note();
            g.text(font, clip(font, note, right - textX), textX, line2, Activities.color("note", 0xE0E0E0));

            String age = age(listing.createdAt()).getString();
            int ageWidth = font.width(age);
            g.text(font, age, right - ageWidth, line3, Activities.color("age", 0x808080));
            int line3X = textX;
            int line3Right = right - ageWidth - 6;
            if (listing.hasReportLimit()) {
                String limit = Component.translatable("partyfinder.listing.max_reports", listing.maxReports()).getString();
                boolean blocked = !listing.accepts(PartyFinderState.selfReports());
                String clipped = clip(font, limit, line3Right - line3X);
                g.text(font, clipped, line3X, line3, blocked ? Activities.color("reports_some", 0xFF5555) : Activities.color("report_limit", 0xAAAAAA));
                line3X += font.width(clipped) + 6;
            }
            if (!listing.requirements().isEmpty()) {
                String req = clip(font, listing.requirements(), line3Right - line3X);
                g.text(font, req, line3X, line3, Activities.color("requirements", 0xFFAA00));
            }

            if (hovered) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(activity.name().copy().withColor(activity.color()));
                tooltip.add(Component.literal(listing.owner().name()).withColor(0xAAAAAA));
                tooltip.add(Component.literal(note));
                if (!listing.requirements().isEmpty()) tooltip.add(Component.literal(listing.requirements()).withColor(0xFFAA00));
                if (listing.hasReportLimit()) {
                    tooltip.add(Component.translatable("partyfinder.listing.max_reports.tooltip", listing.maxReports()).withColor(0xAAAAAA));
                }
                tooltip.addAll(reports.describe());
                g.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            }
        }
    }

    private static String clip(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        int ellipsis = font.width("...");
        if (maxWidth <= ellipsis) return "";
        return font.plainSubstrByWidth(text, maxWidth - ellipsis) + "...";
    }

    private static Component age(long createdAt) {
        long minutes = Math.max(0, (System.currentTimeMillis() - createdAt) / 60_000);
        if (minutes < 1) return Component.translatable("partyfinder.listing.age.now");
        if (minutes < 60) return Component.translatable("partyfinder.listing.age.minutes", minutes);
        return Component.translatable("partyfinder.listing.age.hours", minutes / 60);
    }
}
