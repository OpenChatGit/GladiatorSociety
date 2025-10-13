package src.data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the Fleet Battles mode state (ally/enemy growth per round)
 */
public class GladiatorSociety_FleetBattleContent {
    private int round;
    private int allyPower;
    private int enemyPower;
    private String nextEnemyFaction;
    private String nextAllyFaction;
    private final Set<String> shipRewardTaken = new HashSet<>();

    public GladiatorSociety_FleetBattleContent() {
        this.round = 0;
        this.allyPower = 10;
        this.enemyPower = 12;
        pickNextFactions();
    }

    public int getRound() { return round; }

    public int getAllyPower() { return Math.max(5, (int)(allyPower * 1.25f)); }  // Nerfed from 1.4x

    public int getEnemyPower() { return Math.max(6, (int)(enemyPower * 1.35f)); }  // Nerfed from 1.5x

    public String getEnemyFaction() {
        ensureFactionExists();
        return nextEnemyFaction;
    }

    public String getAllyFaction() {
        ensureFactionExists();
        return nextAllyFaction;
    }

    public void nextRound() {
        round++;
        // NERFED: Reduced scaling to slow down progression
        if (allyPower > 400) allyPower += (int)(allyPower * 0.04f); else allyPower += (int)(allyPower * 0.08f + 5);  // Was 6%/12%+8
        if (enemyPower > 400) enemyPower += (int)(enemyPower * 0.05f); else enemyPower += (int)(enemyPower * 0.10f + 6);  // Was 8%/14%+10
        pickNextFactions();
        // Process round rewards similar to Endless, but lighter (blueprints only here)
        processRoundRewards();
    }

    /**
     * Calculates the credit reward based on enemy power this round.
     * NERFED: Reduced to match Endless nerf (3.0 coefficient instead of 4.5)
     */
    public int getCreditReward() {
        int p = getEnemyPower();
        // Slightly lower than Endless to reflect that you have an ally
        return (int) (((3.0 * Math.pow(p, 2)) + p * 800) * 0.75f);
    }

    private void ensureFactionExists() {
        if (Global.getSector().getFaction(nextEnemyFaction) == null) {
            nextEnemyFaction = Factions.PIRATES;
        }
        if (Global.getSector().getFaction(nextAllyFaction) == null) {
            nextAllyFaction = Factions.INDEPENDENT;
        }
    }

    private void pickNextFactions() {
        // 1) Try discovery-based pool (includes mod factions)
        try {
            java.util.Set<String> pool = GladiatorSociety_FactionDiscovery.getCombatFactions();
            if (pool != null && !pool.isEmpty()) {
                // Build vanilla bucket (known core factions) and modded bucket (everything else in pool)
                java.util.Set<String> vanilla = new java.util.HashSet<>();
                java.util.Collections.addAll(vanilla,
                        Factions.HEGEMONY,
                        Factions.TRITACHYON,
                        Factions.PERSEAN,
                        Factions.PIRATES,
                        Factions.INDEPENDENT,
                        Factions.LUDDIC_CHURCH,
                        Factions.LUDDIC_PATH,
                        Factions.DIKTAT,
                        Factions.LIONS_GUARD
                );

                java.util.List<String> vanillaBucket = new java.util.ArrayList<>();
                java.util.List<String> moddedBucket = new java.util.ArrayList<>();
                for (String id : pool) {
                    if (vanilla.contains(id)) vanillaBucket.add(id); else moddedBucket.add(id);
                }

                // Helper to pick from a list with equal weights
                java.util.function.Function<java.util.List<String>, String> pickFrom = list -> {
                    if (list == null || list.isEmpty()) return null;
                    WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
                    for (String id : list) picker.add(id, 1f);
                    return picker.pick();
                };

                boolean enemyPickModded = Math.random() < 0.5;
                String enemy = enemyPickModded ? pickFrom.apply(moddedBucket) : pickFrom.apply(vanillaBucket);
                if (enemy == null) enemy = enemyPickModded ? pickFrom.apply(vanillaBucket) : pickFrom.apply(moddedBucket);
                if (enemy == null) enemy = Factions.PIRATES;
                nextEnemyFaction = enemy;

                boolean allyPickModded = Math.random() < 0.5;
                String ally = allyPickModded ? pickFrom.apply(moddedBucket) : pickFrom.apply(vanillaBucket);
                if (ally == null) ally = allyPickModded ? pickFrom.apply(vanillaBucket) : pickFrom.apply(moddedBucket);
                if (ally == null) ally = Factions.INDEPENDENT;
                nextAllyFaction = ally;
                return; // success with discovery
            }
        } catch (Throwable ignored) { }

        // 2) Fallback to existing logic
        WeightedRandomPicker<String> enemyPicker = new WeightedRandomPicker<>();
        for (String fac : src.data.scripts.campaign.GladiatorSociety_JSONBountyRead.getAllEndlessFactionCopy()) {
            enemyPicker.add(fac, 1f);
        }
        nextEnemyFaction = enemyPicker.isEmpty() ? Factions.PIRATES : (enemyPicker.pick() == null ? Factions.PIRATES : enemyPicker.pick());

        WeightedRandomPicker<String> allyPicker = new WeightedRandomPicker<>();
        allyPicker.add(Factions.INDEPENDENT, 1f);
        allyPicker.add(Factions.HEGEMONY, 1f);
        allyPicker.add(Factions.TRITACHYON, 1f);
        allyPicker.add(Factions.LIONS_GUARD, 1f);
        allyPicker.add(Factions.PERSEAN, 1f);
        nextAllyFaction = allyPicker.isEmpty() ? Factions.INDEPENDENT : (allyPicker.pick() == null ? Factions.INDEPENDENT : allyPicker.pick());
    }

    /**
     * Process blueprint and ship rewards for the current round.
     * Mirrors Endless logic conceptually but simplified and nerfed for Fleet Battles.
     */
    private void processRoundRewards() {
        int r = getRound();
        // Blueprint reward every 3 rounds (reuse Endless system)
        try {
            if (GladiatorSociety_BlueprintRewardSystem.shouldGiveBlueprintReward(r)) {
                int creditReward = getCreditReward();
                GladiatorSociety_BlueprintRewardSystem.giveBlueprintReward(creditReward,
                        Global.getSector().getPlayerFleet().getCargo());
            }
        } catch (Throwable t) {
            Global.getLogger(GladiatorSociety_FleetBattleContent.class).warn("Blueprint reward failed", t);
        }
    }

    /**
     * Try to award a unique ship from the provided enemy seed list.
     * BudgetFP limits max FP of the ship. Returns true if a ship was awarded.
     */
    public boolean tryAwardUniqueShipFromEnemy(List<FleetMemberAPI> enemySeed, int budgetFP) {
        if (enemySeed == null || enemySeed.isEmpty()) return false;
        try {
            WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<>();
            for (FleetMemberAPI m : enemySeed) {
                if (m.isFighterWing()) continue;
                if (m.getHullSpec() == null) continue;
                int fp = (int) m.getHullSpec().getFleetPoints();
                if (fp <= 0 || fp > budgetFP) continue;
                String hullId = m.getHullSpec().getHullId();
                if (shipRewardTaken.contains(hullId)) continue; // give only once
                picker.add(m, Math.max(1f, fp));
            }
            FleetMemberAPI chosen = picker.pick();
            if (chosen == null) return false;

            String variantId = chosen.getVariant().getHullVariantId();
            // In some cases, we may need to fallback to base hull ID
            if (!Global.getSettings().doesVariantExist(variantId)) {
                variantId = chosen.getHullSpec().getHullId();
            }
            FleetMemberAPI ship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
            shipRewardTaken.add(chosen.getHullSpec().getHullId());

            Global.getSector().getCampaignUI().addMessage(
                    "Ship Reward: " + chosen.getHullSpec().getHullNameWithDashClass(),
                    Global.getSettings().getColor("textFriendColor"));
            return true;
        } catch (Throwable t) {
            Global.getLogger(GladiatorSociety_FleetBattleContent.class).error("Failed to award ship from enemy seed", t);
            return false;
        }
    }
}
