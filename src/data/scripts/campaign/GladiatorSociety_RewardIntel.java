package src.data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.Set;

/**
 * Shows a proper intel notification (bottom-left) when the player earns a reward.
 * Uses BaseIntelPlugin so the Intel screen shows real content instead of the
 * "Override .createSmallDescription()" placeholder.
 */
public class GladiatorSociety_RewardIntel extends BaseIntelPlugin {

    public static final Color GS_COLOR = new Color(220, 160, 30, 255);

    private final String title;
    private final String body;
    private final String highlight;

    private GladiatorSociety_RewardIntel(String title, String body, String highlight) {
        this.title = title;
        this.body = body;
        this.highlight = highlight;
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "reputation");
    }

    @Override
    public String getName() {
        return title;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Gladiator Society");
        return tags;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara(body, 0f, Misc.getHighlightColor(), highlight);
    }

    @Override
    public IntelSortTier getSortTier() {
        return IntelSortTier.TIER_1;
    }

    // ── static helpers ────────────────────────────────────────────────────────

    private static void post(String title, String body, String highlight) {
        GladiatorSociety_RewardIntel intel = new GladiatorSociety_RewardIntel(title, body, highlight);
        Global.getSector().getIntelManager().addIntel(intel, false);
    }

    public static void notifyCredits(int amount, String modeName) {
        String amountStr = Misc.getWithDGS(amount);
        post(
            "Gladiator Society - " + modeName + " Victory",
            "Credits received: +" + amountStr,
            amountStr
        );
    }

    public static void notifyBlueprint(String itemName) {
        post(
            "Gladiator Society - Blueprint Reward",
            "Blueprint received: " + itemName,
            itemName
        );
    }

    public static void notifyShip(String shipName) {
        post(
            "Gladiator Society - Ship Reward",
            "Ship added to fleet: " + shipName,
            shipName
        );
    }

    public static void notifyItem(String prefix, String description) {
        post(
            "Gladiator Society - Round Reward",
            prefix + description,
            description
        );
    }
}
