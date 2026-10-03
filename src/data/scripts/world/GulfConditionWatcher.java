package src.data.scripts.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** Adds The Gulf's narrative condition if and when a colony is established there. */
public class GulfConditionWatcher implements EveryFrameScript {
    private float elapsedDays;
    private boolean done;

    @Override
    public void advance(float amount) {
        if (done) return;
        elapsedDays += amount;
        if (elapsedDays < 1f) return;
        elapsedDays = 0f;

        SectorEntityToken entity = Global.getSector().getEntityById(GladiatorSociety_WorldGen.GULF_ID);
        if (!(entity instanceof PlanetAPI)) return;

        MarketAPI market = ((PlanetAPI) entity).getMarket();
        if (market == null) return;
        if (!market.hasCondition(GulfRestrictionCondition.ID)) {
            market.addCondition(GulfRestrictionCondition.ID);
            market.reapplyConditions();
        }
        done = true;
        Global.getSector().removeScript(this);
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
