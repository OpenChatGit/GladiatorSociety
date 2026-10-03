package src.data.scripts.world;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

/** Narrative-only marker for The Gulf. It intentionally has no gameplay modifiers. */
public class GulfRestrictionCondition extends BaseMarketConditionPlugin {
    public static final String ID = "gs_gulf_restriction";

    @Override
    public void apply(String id) {
        // The Society's restriction has no confirmed mechanical effect at this time.
    }

    @Override
    public void unapply(String id) {
        // No modifiers are applied by this condition.
    }

    @Override
    public boolean isTransient() {
        return false;
    }

    @Override
    public boolean isPlanetary() {
        return true;
    }

    @Override
    public boolean showIcon() {
        return true;
    }

    @Override
    public boolean hasCustomTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addPara("The Gladiator Society classifies The Gulf as unsuitable for settlement, despite its stable atmosphere and extensive jungle biosphere. Public survey records contain no confirmed cause. The restriction remains in force, and the Society has sealed the relevant internal reports. No measurable effect has been established.", 0f);
    }
}
