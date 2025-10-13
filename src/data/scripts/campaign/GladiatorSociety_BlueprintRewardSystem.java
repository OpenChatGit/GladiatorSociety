package src.data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import src.data.scripts.campaign.dataclass.GladiatorSociety_EndlessReward;
import src.data.utils.GladiatorSociety_Constants;

/**
 * Enhanced reward system for Gladiator Society
 * Combines the old dialog-based rewards with the new automatic blueprint rewards
 */
public class GladiatorSociety_BlueprintRewardSystem {
    
    public static final Logger LOG = Global.getLogger(GladiatorSociety_BlueprintRewardSystem.class);
    
    private static final String BLUEPRINT_REWARDS_PATH = "data/config/gsounty/BlueprintRewards.csv";
    private static final String ENDLESS_REWARD_PATH = "data/config/gsounty/EndlessReward.csv";
    
    private static final List<BlueprintReward> BLUEPRINT_REWARDS = new ArrayList<>();
    private static final Map<Integer, List<RoundSpecificReward>> ROUND_SPECIFIC_REWARDS = new HashMap<>();
    
    // Load reward configuration on first access
    static {
        loadBlueprintRewards();
        loadLegacyRewards();
    }
    
    /**
     * Checks if the player should receive a blueprint reward
     * @param currentRound Current round
     * @return true if a blueprint should be awarded
     */
    public static boolean shouldGiveBlueprintReward(int currentRound) {
        return currentRound > 0 && currentRound % 3 == 0;
    }
    
    /**
     * Checks if there are specific rewards for this round
     * @param currentRound Current round
     * @return true if there are specific rewards for this round
     */
    public static boolean hasRoundSpecificRewards(int currentRound) {
        return ROUND_SPECIFIC_REWARDS.containsKey(currentRound) && 
               !ROUND_SPECIFIC_REWARDS.get(currentRound).isEmpty();
    }
    
    /**
     * Gets the list of specific rewards for a round
     * @param currentRound Current round
     * @return List of rewards or empty list if none
     */
    public static List<RoundSpecificReward> getRoundSpecificRewards(int currentRound) {
        if (ROUND_SPECIFIC_REWARDS.containsKey(currentRound)) {
            return ROUND_SPECIFIC_REWARDS.get(currentRound);
        }
        return new ArrayList<>();
    }
    
    /**
     * Loads blueprint rewards from the CSV file
     */
    private static void loadBlueprintRewards() {
        try {
            JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod("item_id", BLUEPRINT_REWARDS_PATH, GladiatorSociety_Constants.MOD_ID);
            LOG.info("GS Blueprint Rewards: Loading blueprint rewards from CSV");
            
            for (int i = 0; i < csv.length(); i++) {
                JSONObject row = csv.getJSONObject(i);
                
                String itemId = row.optString("item_id", "");
                if (itemId.isEmpty() || itemId.startsWith("#")) continue; // Skip comments
                
                String blueprintType = getBlueprintTypeFromCategory(row.optString("blueprint_type", ""));
                String name = row.optString("name", itemId);
                int minValue = row.optInt("min_credit_value", 0);
                int maxValue = row.optInt("max_credit_value", 999999);
                float weight = (float) row.optDouble("weight", 1.0);
                
                // Check if item exists
                if (isValidItem(itemId, blueprintType)) {
                    BLUEPRINT_REWARDS.add(new BlueprintReward(itemId, blueprintType, name, minValue, maxValue, weight));
                    LOG.info("GS Blueprint Rewards: Added " + name + " (" + minValue + "-" + maxValue + " credits)");
                }
            }
            
            LOG.info("GS Blueprint Rewards: Loaded " + BLUEPRINT_REWARDS.size() + " blueprint rewards");
            
        } catch (IOException | JSONException ex) {
            LOG.error("GS Blueprint Rewards: Failed to load blueprint rewards", ex);
        }
    }
    
    /**
     * Loads legacy rewards from EndlessReward.csv and converts them to the new format
     */
    private static void loadLegacyRewards() {
        try {
            JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod("id_reward", ENDLESS_REWARD_PATH, GladiatorSociety_Constants.MOD_ID);
            LOG.info("GS Blueprint Rewards: Loading legacy rewards from EndlessReward.csv");
            
            for (int i = 0; i < csv.length(); i++) {
                JSONObject row = csv.getJSONObject(i);
                
                String rewardId = row.optString("id_reward", "");
                if (rewardId.isEmpty()) continue;
                
                String resourceId = row.optString("id_resource", "");
                if (resourceId.isEmpty()) continue;
                
                int roundReward = row.optInt("roundReward", 0);
                int number = row.optInt("number", 1);
                boolean isBlueprint = "true".equalsIgnoreCase(row.optString("blueprint", ""));
                String description = row.optString("description", "No description");
                
                // Create reward based on type
                RoundSpecificReward reward = null;
                
                if (isBlueprint) {
                    // Blueprint reward
                    String blueprintType = determineItemType(resourceId);
                    if (blueprintType != null) {
                        reward = new RoundSpecificReward(
                            rewardId, 
                            resourceId, 
                            RewardType.BLUEPRINT, 
                            description, 
                            blueprintType, 
                            number
                        );
                    }
                } else {
                    // Direct item reward
                    RewardType type = determineRewardType(resourceId);
                    if (type != RewardType.UNKNOWN) {
                        reward = new RoundSpecificReward(
                            rewardId,
                            resourceId,
                            type,
                            description,
                            null,
                            number
                        );
                    }
                }
                
                if (reward != null) {
                    // Add to round-specific rewards
                    if (!ROUND_SPECIFIC_REWARDS.containsKey(roundReward)) {
                        ROUND_SPECIFIC_REWARDS.put(roundReward, new ArrayList<>());
                    }
                    ROUND_SPECIFIC_REWARDS.get(roundReward).add(reward);
                    LOG.info("GS Blueprint Rewards: Added legacy reward " + rewardId + " for round " + roundReward);
                }
            }
            
            int totalRewards = 0;
            for (List<RoundSpecificReward> rewards : ROUND_SPECIFIC_REWARDS.values()) {
                totalRewards += rewards.size();
            }
            LOG.info("GS Blueprint Rewards: Loaded " + totalRewards + " legacy rewards for " + ROUND_SPECIFIC_REWARDS.size() + " rounds");
            
        } catch (IOException | JSONException ex) {
            LOG.error("GS Blueprint Rewards: Failed to load legacy rewards", ex);
        }
    }
    
    /**
     * Determines the type of reward based on the resource ID
     */
    private static RewardType determineRewardType(String resourceId) {
        try {
            if (Global.getSettings().getWeaponSpec(resourceId) != null) {
                return RewardType.WEAPON;
            }
        } catch (Exception ignored) {}
        
        try {
            if (Global.getSettings().getFighterWingSpec(resourceId) != null) {
                return RewardType.FIGHTER;
            }
        } catch (Exception ignored) {}
        
        try {
            if (Global.getSettings().doesVariantExist(resourceId)) {
                return RewardType.SHIP;
            }
        } catch (Exception ignored) {}
        
        try {
            if (Global.getSettings().getHullModSpec(resourceId) != null) {
                return RewardType.HULLMOD;
            }
        } catch (Exception ignored) {}
        
        return RewardType.UNKNOWN;
    }
    
    /**
     * Determines the blueprint type for an item
     */
    private static String determineItemType(String itemId) {
        try {
            if (Global.getSettings().getWeaponSpec(itemId) != null) {
                return Items.WEAPON_BP;
            }
        } catch (Exception ignored) {}
        
        try {
            if (Global.getSettings().getFighterWingSpec(itemId) != null) {
                return Items.FIGHTER_BP;
            }
        } catch (Exception ignored) {}
        
        try {
            if (Global.getSettings().doesVariantExist(itemId)) {
                return Items.SHIP_BP;
            }
        } catch (Exception ignored) {}
        
        try {
            if (Global.getSettings().getHullModSpec(itemId) != null) {
                return "modspec"; // Use string directly
            }
        } catch (Exception ignored) {}
        
        return null;
    }
    
    /**
     * Gives a blueprint based on the credit value of the round
     * @param creditValue The credit value of the current round
     * @param playerCargo The player's cargo
     */
    /**
     * Gives a blueprint based on the credit value of the round
     * @param creditValue The credit value of the current round
     * @param playerCargo The player's cargo
     */
    public static void giveBlueprintReward(int creditValue, CargoAPI playerCargo) {
        // Get the current round from the EndlessContent
        int currentRound = 0;
        if (Global.getSector().getPersistentData().containsKey("$GladiatorSociety_EndlessContentKey")) {
            Object content = Global.getSector().getPersistentData().get("$GladiatorSociety_EndlessContentKey");
            if (content instanceof src.data.scripts.campaign.GladiatorSociety_EndlessContent) {
                currentRound = ((src.data.scripts.campaign.GladiatorSociety_EndlessContent) content).getEndlessRound();
            }
        }
        
        LOG.info("GS Blueprint Reward: Giving blueprint for credit value: " + creditValue + " in round " + currentRound);
        
        WeightedRandomPicker<BlueprintReward> picker = new WeightedRandomPicker<>();
        
        // Collect available blueprints based on credit value
        for (BlueprintReward reward : BLUEPRINT_REWARDS) {
            if (creditValue >= reward.minValue && creditValue <= reward.maxValue) {
                // Calculate bonus weight based on round number and reward value
                float bonusWeight = calculateBonusWeight(reward, currentRound);
                
                // Add reward with adjusted weight
                picker.add(reward, reward.weight + bonusWeight);
                
                LOG.info("GS Blueprint Reward: Added " + reward.name + " with weight " + 
                         (reward.weight + bonusWeight) + " (base: " + reward.weight + 
                         ", bonus: " + bonusWeight + ")");
            }
        }
        
        if (picker.isEmpty()) {
            LOG.warn("GS Blueprint Reward: No blueprints available for credit value: " + creditValue);
            return;
        }
        
        BlueprintReward reward = picker.pick();
        if (reward != null) {
            addBlueprintToPlayer(reward, playerCargo);
            LOG.info("GS Blueprint Reward: Gave " + reward.name + " blueprint");
        }
    }
    
    /**
     * Calculates bonus weight for rewards based on round number and reward value
     * Higher rounds give more bonus to expensive rewards
     * @param reward The reward to calculate bonus for
     * @param round The current round
     * @return Bonus weight to add
     */
    private static float calculateBonusWeight(BlueprintReward reward, int round) {
        // No bonus for early rounds
        if (round < 5) {
            return 0f;
        }
        
        // Calculate value ratio (0.0 to 1.0) based on min and max values
        float valueRatio = 0f;
        if (reward.maxValue > reward.minValue) {
            valueRatio = (float)(reward.maxValue - reward.minValue) / 999999f;
        }
        
        // Calculate round factor (increases with round number)
        float roundFactor = Math.min(1.0f, round / 50f);
        
        // Calculate bonus weight (higher for more valuable items and later rounds)
        float bonusWeight = valueRatio * roundFactor * 10f;
        
        return bonusWeight;
    }
    
    /**
     * Gives a specific reward to the player
     * @param reward The reward to give
     * @param playerCargo The player's cargo
     */
    public static void giveSpecificReward(RoundSpecificReward reward, CargoAPI playerCargo) {
        LOG.info("GS Specific Reward: Giving " + reward.rewardId);
        
        switch (reward.type) {
            case BLUEPRINT:
                SpecialItemData blueprintData = new SpecialItemData(reward.blueprintType, reward.resourceId);
                playerCargo.addSpecial(blueprintData, 1);
                
                Global.getSector().getCampaignUI().addMessage(
                    "Blueprint Reward: " + reward.description,
                    Global.getSettings().getColor("textFriendColor")
                );
                break;
                
            case SHIP:
                playerCargo.addFighters(reward.resourceId, reward.quantity);
                
                Global.getSector().getCampaignUI().addMessage(
                    "Ship Reward: " + reward.description,
                    Global.getSettings().getColor("textFriendColor")
                );
                break;
                
            case FIGHTER:
                playerCargo.addFighters(reward.resourceId, reward.quantity);
                
                Global.getSector().getCampaignUI().addMessage(
                    "Fighter Wing Reward: " + reward.description,
                    Global.getSettings().getColor("textFriendColor")
                );
                break;
                
            case WEAPON:
                playerCargo.addWeapons(reward.resourceId, reward.quantity);
                
                Global.getSector().getCampaignUI().addMessage(
                    "Weapon Reward: " + reward.description,
                    Global.getSettings().getColor("textFriendColor")
                );
                break;
                
            case HULLMOD:
                SpecialItemData hullmodData = new SpecialItemData("modspec", reward.resourceId); // Use string directly
                playerCargo.addSpecial(hullmodData, 1);
                
                Global.getSector().getCampaignUI().addMessage(
                    "Hull Mod Reward: " + reward.description,
                    Global.getSettings().getColor("textFriendColor")
                );
                break;
        }
        
        LOG.info("GS Specific Reward: Gave " + reward.rewardId);
    }
    
    /**
     * Converts blueprint category to Starsector item ID
     */
    private static String getBlueprintTypeFromCategory(String category) {
        switch (category.toLowerCase()) {
            case "ship": return Items.SHIP_BP;
            case "weapon": return Items.WEAPON_BP;
            case "fighter": return Items.FIGHTER_BP;
            case "hullmod": return "modspec"; // Use string directly instead of Items.MODSPEC
            default: return Items.SHIP_BP;
        }
    }
    
    /**
     * Checks if an item exists
     */
    private static boolean isValidItem(String itemId, String blueprintType) {
        try {
            switch (blueprintType) {
                case Items.SHIP_BP:
                    return Global.getSettings().doesVariantExist(itemId);
                case Items.WEAPON_BP:
                    return Global.getSettings().getWeaponSpec(itemId) != null;
                case Items.FIGHTER_BP:
                    return Global.getSettings().getFighterWingSpec(itemId) != null;
                case "modspec": // Use string directly instead of Items.MODSPEC
                    return Global.getSettings().getHullModSpec(itemId) != null;
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Adds the blueprint to the player's cargo
     */
    private static void addBlueprintToPlayer(BlueprintReward reward, CargoAPI playerCargo) {
        SpecialItemData blueprintData = new SpecialItemData(reward.blueprintType, reward.itemId);
        playerCargo.addSpecial(blueprintData, 1);
        
        // Message to the player
        Global.getSector().getCampaignUI().addMessage(
            "Blueprint Reward: Received " + reward.name + "!",
            Global.getSettings().getColor("textFriendColor")
        );
    }
    
    /**
     * Converts a legacy EndlessReward to the new RoundSpecificReward format
     */
    public static RoundSpecificReward convertLegacyReward(GladiatorSociety_EndlessReward legacyReward) {
        RewardType type;
        String blueprintType = null;
        
        if (legacyReward.blueprint) {
            type = RewardType.BLUEPRINT;
            blueprintType = determineItemType(legacyReward.id_Resource);
        } else {
            type = determineRewardType(legacyReward.id_Resource);
        }
        
        return new RoundSpecificReward(
            legacyReward.id_Reward,
            legacyReward.id_Resource,
            type,
            legacyReward.description,
            blueprintType,
            legacyReward.number
        );
    }
    
    /**
     * Enum for reward types
     */
    public enum RewardType {
        BLUEPRINT,
        SHIP,
        FIGHTER,
        WEAPON,
        HULLMOD,
        UNKNOWN
    }
    
    /**
     * Data class for blueprint rewards
     */
    private static class BlueprintReward {
        public final String itemId;
        public final String blueprintType;
        public final String name;
        public final int minValue;
        public final int maxValue;
        public final float weight;
        
        public BlueprintReward(String itemId, String blueprintType, String name, int minValue, int maxValue, float weight) {
            this.itemId = itemId;
            this.blueprintType = blueprintType;
            this.name = name;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.weight = weight;
        }
    }
    
    /**
     * Data class for round-specific rewards
     */
    public static class RoundSpecificReward {
        public final String rewardId;
        public final String resourceId;
        public final RewardType type;
        public final String description;
        public final String blueprintType;
        public final int quantity;
        
        public RoundSpecificReward(String rewardId, String resourceId, RewardType type, 
                                  String description, String blueprintType, int quantity) {
            this.rewardId = rewardId;
            this.resourceId = resourceId;
            this.type = type;
            this.description = description;
            this.blueprintType = blueprintType;
            this.quantity = quantity > 0 ? quantity : 1;
        }
    }
}