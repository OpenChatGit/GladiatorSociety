package src.data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import src.data.scripts.campaign.GladiatorSociety_FactionDiscoveryConfig;
import src.data.scripts.campaign.GladiatorSociety_RewardIntel;
import src.data.utils.GladiatorSociety_Constants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import src.data.scripts.campaign.dataclass.*;

/**
 * Manages the endless battle mode content
 */
public class GladiatorSociety_EndlessContent {

    private int endlessPower;
    private int endlessRound;
    private String nextFaction;

    // Legacy reward system - kept for backward compatibility
    private final List<GladiatorSociety_EndlessReward> currentRewardList = new ArrayList<>();
    private final Set<String> rewardTaken = new HashSet<>();

    /**
     * Constructor - initializes with default values
     */
    public GladiatorSociety_EndlessContent() {
        endlessPower = 10;
        endlessRound = 0;
        setRandomFaction();
    }

    /**
     * Gets the power level for the current round
     * @return Power level multiplied by 1.3 (nerfed from 1.5)
     */
    public int getEndlessPower() {
        return (int)(endlessPower*1.3f);
    }

    /**
     * Increases the power level for the next round
     * NERFED: Reduced scaling to slow down progression
     */
    private void addEndlessPower() {
        if (endlessPower > 400) {
            endlessPower += (endlessPower * 0.035f);  // Reduced from 0.05 (5% -> 3.5%)
        } else {
            endlessPower += (endlessPower * 0.07f + 5);  // Reduced from 0.1 + 10 (10% + 10 -> 7% + 5)
        }
    }

    /**
     * Gets the current round number
     * @return Current round
     */
    public int getEndlessRound() {
        return endlessRound;
    }

    /**
     * Sets the current round number
     * @param value Round number
     */
    public void setEndlessRound(int value) {
        this.endlessRound = value;
    }

    /**
     * Gets the faction for the current round
     * @return Faction ID
     */
    public String getEndlessFaction() {
        while (Global.getSector().getFaction(nextFaction) == null) {
            setRandomFaction();
        }
        return this.nextFaction;
    }

    /**
     * Increments the round counter and processes rewards
     */
    public void incEndlessRound() {
        endlessRound++;
        
        // Process automatic rewards for this round
        CargoRewardProcessor.processRoundRewards(endlessRound, rewardTaken);
        
        // Check for blueprint rewards every 3 rounds
        if (GladiatorSociety_BlueprintRewardSystem.shouldGiveBlueprintReward(endlessRound)) {
            int creditReward = getEndlessReward();
            GladiatorSociety_BlueprintRewardSystem.giveBlueprintReward(creditReward, 
                Global.getSector().getPlayerFleet().getCargo());
        }
        
        addEndlessPower();
        setRandomFaction();
    }

    /**
     * Sets a random faction for the next round
     */
    private void setRandomFaction() {
        // Use dynamic discovery pool; split into vanilla vs modded with 50/50 selection and fallbacks
        Set<String> pool = GladiatorSociety_FactionDiscovery.getCombatFactions();
        if (pool == null || pool.isEmpty()) {
            nextFaction = Factions.PIRATES;
            return;
        }

        // Vanilla set mirrors FleetBattleContent
        Set<String> vanilla = new java.util.HashSet<>();
        vanilla.add(Factions.HEGEMONY);
        vanilla.add(Factions.TRITACHYON);
        vanilla.add(Factions.PERSEAN);
        vanilla.add(Factions.PIRATES);
        vanilla.add(Factions.INDEPENDENT);
        vanilla.add(Factions.LUDDIC_CHURCH);
        vanilla.add(Factions.LUDDIC_PATH);
        vanilla.add(Factions.DIKTAT);
        vanilla.add(Factions.LIONS_GUARD);

        java.util.List<String> vanillaBucket = new java.util.ArrayList<>();
        java.util.List<String> moddedBucket = new java.util.ArrayList<>();
        for (String id : pool) {
            if (vanilla.contains(id)) vanillaBucket.add(id); else moddedBucket.add(id);
        }

        java.util.function.Function<java.util.List<String>, String> pickFrom = list -> {
            if (list == null || list.isEmpty()) return null;
            WeightedRandomPicker<String> p = new WeightedRandomPicker<>();
            for (String id : list) p.add(id, 1f);
            return p.pick();
        };

        // Read configurable split for vanilla vs modded
        float split = 0.5f;
        try {
            GladiatorSociety_FactionDiscoveryConfig cfg = GladiatorSociety_FactionDiscoveryConfig.load();
            split = cfg.vanillaModdedSplit;
            if (Float.isNaN(split)) split = 0.5f;
            if (split < 0f) split = 0f;
            if (split > 1f) split = 1f;
        } catch (Throwable ignored) {}
        boolean useModded = Math.random() < split;
        String picked = useModded ? pickFrom.apply(moddedBucket) : pickFrom.apply(vanillaBucket);
        if (picked == null) picked = useModded ? pickFrom.apply(vanillaBucket) : pickFrom.apply(moddedBucket);
        if (picked == null) picked = Factions.PIRATES;
        nextFaction = picked;
    }

    /**
     * Calculates the credit reward for the current round.
     * Uses a linear formula tied to endlessPower to stay proportional to fleet strength.
     * Formula: power * 150 + 5000 (flat base)
     * Examples: round 5 (~p=50) -> ~12.5k, round 10 (~p=195) -> ~34k, round 20 (~p=680) -> ~107k
     */
    public int getEndlessReward() {
        return endlessPower * 150 + 5000;
    }

    /**
     * Checks if there are any rewards available through the dialog system
     * This is kept for backward compatibility but will eventually be removed
     * @return Always false in the new system
     */
    public boolean canHaveReward() {
        // This method will always return false in the new system
        // All rewards are now given automatically
        return false;
    }

    /**
     * Updates the available rewards list
     * @deprecated This method is kept for backward compatibility but is no longer used
     * All rewards are now given automatically through the BlueprintRewardSystem
     */
    @Deprecated
    public void updateAvailableReward() {
        // This method is kept for backward compatibility
        // All rewards are now given automatically
        currentRewardList.clear();
    }

    /**
     * Gets the current list of rewards
     * @return Empty list in the new system
     */
    public List<GladiatorSociety_EndlessReward> getCurrentRewardList() {
        return currentRewardList;
    }

    /**
     * Gets the set of rewards that have been taken
     * @return Set of reward IDs
     */
    public Set<String> getRewardTakenList() {
        return this.rewardTaken;
    }

    /**
     * Adds a reward ID to the taken list
     * @param reward Reward ID
     */
    public void addTakenReward(String reward) {
        rewardTaken.add(reward);
    }

    /**
     * Helper class to process cargo rewards
     */
    private static class CargoRewardProcessor {
        /**
         * Processes rewards for a specific round
         * @param round Current round
         * @param takenRewards Set of already taken rewards
         */
        public static void processRoundRewards(int round, Set<String> takenRewards) {
            // Load rewards from the BlueprintRewardSystem CSV
            List<GladiatorSociety_EndlessReward> rewards = GladiatorSociety_JSONBountyRead.getAllEndlessRewardCopy();
            
            for (GladiatorSociety_EndlessReward reward : rewards) {
                // Check if this reward has already been taken
                boolean alreadyTaken = takenRewards.contains(reward.id_Reward);
                
                // 20% chance to allow duplicate rewards
                boolean allowDuplicate = Math.random() < 0.2f;
                
                // Skip rewards that have already been taken unless we allow duplicates
                if (alreadyTaken && !allowDuplicate) {
                    continue;
                }
                
                // Check if this reward should be given in this round
                if (reward.roundReward == round) {
                    // Process the reward
                    processReward(reward);
                    
                    // Log if this is a duplicate reward
                    if (alreadyTaken) {
                        Global.getLogger(GladiatorSociety_EndlessContent.class).info(
                            "Giving duplicate reward: " + reward.id_Reward);
                    }
                    
                    // Mark as taken (even if it was already taken, to prevent further duplicates)
                    takenRewards.add(reward.id_Reward);
                }
            }
        }
        
        /**
         * Processes a single reward
         * @param reward The reward to process
         */
        private static void processReward(GladiatorSociety_EndlessReward reward) {
            switch (reward.rewardType) {
                case 2: // Weapon
                    Global.getSector().getPlayerFleet().getCargo().addWeapons(reward.id_Resource, reward.number);
                    break;
                    
                case 3: // Fighter
                    Global.getSector().getPlayerFleet().getCargo().addFighters(reward.id_Resource, reward.number);
                    break;
                    
                case 4: // Ship variant
                    if (reward.blueprint) {
                        Global.getSector().getPlayerFleet().getCargo().addSpecial(
                            new com.fs.starfarer.api.campaign.SpecialItemData("ship_bp", reward.id_Resource), 1);
                    } else {
                        // Add ship directly to fleet
                        try {
                            // Create a new ship from the variant
                            com.fs.starfarer.api.fleet.FleetMemberAPI ship = 
                                Global.getFactory().createFleetMember(
                                    com.fs.starfarer.api.fleet.FleetMemberType.SHIP, reward.id_Resource);
                            
                            // Add the ship to the player's fleet
                            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
                            
                            // Log success
                            Global.getLogger(GladiatorSociety_EndlessContent.class).info(
                                "Added ship " + reward.id_Resource + " to player fleet");
                        } catch (Exception e) {
                            // Log error
                            Global.getLogger(GladiatorSociety_EndlessContent.class).error(
                                "Failed to add ship " + reward.id_Resource + " to player fleet", e);
                        }
                    }
                    break;
                    
                case 5: // Hullmod
                    if (reward.blueprint) {
                        Global.getSector().getPlayerFleet().getCargo().addSpecial(
                            new com.fs.starfarer.api.campaign.SpecialItemData(GladiatorSociety_Constants.MODSPEC_ITEM_ID, reward.id_Resource), 1);
                    }
                    break;
                    
                case 7: // Hull
                    if (reward.blueprint) {
                        Global.getSector().getPlayerFleet().getCargo().addSpecial(
                            new com.fs.starfarer.api.campaign.SpecialItemData("ship_bp", reward.id_Resource), 1);
                    } else {
                        // Add ship hull directly to fleet
                        try {
                            // Get the default variant for this hull
                            String variantId = Global.getSettings().getHullSpec(reward.id_Resource).getHullId() + "_Hull";
                            
                            // Try to use the hull ID as variant ID if default variant doesn't exist
                            if (!Global.getSettings().doesVariantExist(variantId)) {
                                variantId = reward.id_Resource;
                            }
                            
                            // Create a new ship from the variant
                            com.fs.starfarer.api.fleet.FleetMemberAPI ship = 
                                Global.getFactory().createFleetMember(
                                    com.fs.starfarer.api.fleet.FleetMemberType.SHIP, variantId);
                            
                            // Add the ship to the player's fleet
                            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
                            
                            // Log success
                            Global.getLogger(GladiatorSociety_EndlessContent.class).info(
                                "Added hull " + reward.id_Resource + " to player fleet");
                        } catch (Exception e) {
                            // Log error
                            Global.getLogger(GladiatorSociety_EndlessContent.class).error(
                                "Failed to add hull " + reward.id_Resource + " to player fleet", e);
                            
                            // Fallback: Try to add as blueprint
                            try {
                                Global.getSector().getPlayerFleet().getCargo().addSpecial(
                                    new com.fs.starfarer.api.campaign.SpecialItemData("ship_bp", reward.id_Resource), 1);
                            } catch (Exception ex) {
                                Global.getLogger(GladiatorSociety_EndlessContent.class).error(
                                    "Fallback also failed", ex);
                            }
                        }
                    }
                    break;
            }
            
            // Show message to player with reward type
            String messagePrefix;
            switch (reward.rewardType) {
                case 2:
                    messagePrefix = "Weapon Reward: ";
                    break;
                case 3:
                    messagePrefix = "Fighter Wing Reward: ";
                    break;
                case 4:
                    messagePrefix = reward.blueprint ? "Ship Blueprint Reward: " : "Ship Reward: ";
                    break;
                case 5:
                    messagePrefix = "Hull Mod Reward: ";
                    break;
                case 7:
                    messagePrefix = reward.blueprint ? "Hull Blueprint Reward: " : "Ship Hull Reward: ";
                    break;
                default:
                    messagePrefix = "Reward: ";
            }
            
            GladiatorSociety_RewardIntel.notifyItem(messagePrefix, reward.description);
        }
    }
}