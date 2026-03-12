package src.data.scripts.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.log4j.Logger;

/**
 * Gladiator Society initiation quest.
 * Triggered when the player visits The Arena station and talks to Commander Varro.
 *
 * Stages:
 *   TALK_TO_VARRO  -> player visits station, gets briefing
 *   KILL_PATROL    -> player must destroy the Remnant patrol in the system
 *   RETURN         -> player returns to Varro to claim reward
 *   COMPLETED      -> quest done, Salvage Pit unlocked
 *   FAILED         -> (unused, no time limit)
 */
public class GladiatorSociety_InitQuest extends HubMissionWithBarEvent {

    public static final Logger LOG = Global.getLogger(GladiatorSociety_InitQuest.class);

    public static final String QUEST_REF        = "$GS_initQuest_ref";
    public static final String FLAG_PATROL_DEAD = "$GS_initQuest_patrolDead";
    public static final String FLAG_COMPLETED   = "$GS_initQuest_completed";
    public static final String FLAG_PIT_UNLOCKED = "$GS_salvagePitUnlocked";
    public static final String PERSON_VARRO     = "gs_commander_varro";

    public enum Stage {
        KILL_PATROL,
        RETURN_TO_VARRO,
        COMPLETED,
    }

    private CampaignFleetAPI targetFleet;
    private StarSystemAPI arenaSystem;
    private PersonAPI varro;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        // Only create at The Arena market (on Gladius Prime or the station)
        if (createdAt == null) return false;
        String primaryId = createdAt.getPrimaryEntity() != null ? createdAt.getPrimaryEntity().getId() : "";
        if (!"gs_gladius_prime".equals(primaryId) && !"gs_arena_station".equals(primaryId)) return false;

        // Prevent duplicate quest
        if (!setGlobalReference(QUEST_REF)) return false;

        arenaSystem = createdAt.getStarSystem();
        if (arenaSystem == null) return false;

        // Get or create Varro
        varro = getImportantPerson(PERSON_VARRO);
        if (varro == null) {
            LOG.warn("GS Quest: Varro not found in ImportantPeople");
            return false;
        }
        personOverride = varro;
        if (!setPersonMissionRef(varro, QUEST_REF)) return false;

        // Spawn the Remnant patrol fleet
        targetFleet = spawnPatrolFleet(arenaSystem);
        if (targetFleet == null) return false;

        // Make fleet important (shows on map)
        makeImportant(targetFleet, QUEST_REF, Stage.KILL_PATROL);
        Misc.makeHostile(targetFleet);
        Misc.makeImportant(targetFleet, QUEST_REF);
        targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY, true);

        // Stage transitions
        setStartingStage(Stage.KILL_PATROL);
        addSuccessStages(Stage.COMPLETED);

        // When fleet is defeated -> advance to RETURN stage
        addFleetDefeatTrigger(targetFleet, FLAG_PATROL_DEAD, false);
        setStageOnGlobalFlag(Stage.RETURN_TO_VARRO, FLAG_PATROL_DEAD);
        setStageOnMemoryFlag(Stage.COMPLETED, varro, FLAG_COMPLETED);

        // Rewards
        setCreditReward(BaseHubMission.CreditReward.LOW);
        setRepRewardFaction(0.05f);
        setRepRewardPerson(0.1f);

        LOG.info("GS Quest: Created initiation quest, patrol fleet id=" + targetFleet.getId());
        return true;
    }

    private CampaignFleetAPI spawnPatrolFleet(StarSystemAPI system) {
        try {
            int playerFP = Global.getSector().getPlayerFleet().getFleetPoints();
            float combatPts = Math.max(60f, Math.min(playerFP * 0.6f, 200f));

            FleetParamsV3 params = new FleetParamsV3(
                    null, null,
                    Factions.REMNANTS,
                    null,
                    FleetTypes.PATROL_MEDIUM,
                    combatPts,
                    0, 0, 0f, 0f, 0f, 1f
            );
            params.quality = 0.5f;
            params.withOfficers = true;

            CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
            if (fleet == null || fleet.isEmpty()) return null;

            fleet.setName("Remnant Patrol");

            Random rand = new Random();
            float angle = rand.nextFloat() * 360f;
            float dist = 2500f + rand.nextFloat() * 2000f;
            fleet.setLocation(
                    (float) Math.cos(Math.toRadians(angle)) * dist,
                    (float) Math.sin(Math.toRadians(angle)) * dist
            );
            system.addEntity(fleet);
            return fleet;
        } catch (Throwable t) {
            LOG.error("GS Quest: Failed to spawn patrol fleet", t);
            return null;
        }
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog,
                             List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        switch (action) {
            case "beginIntro":
                dialog.getInteractionTarget().setActivePerson(varro);
                dialog.getVisualPanel().showPersonInfo(varro, true);
                updateInteractionData(dialog, memoryMap);
                return false;

            case "accept":
                accept(dialog, memoryMap);
                return true;

            case "complete":
                // Player returned after killing the fleet
                varro.getMemoryWithoutUpdate().set(FLAG_COMPLETED, true);
                Global.getSector().getMemoryWithoutUpdate().set(FLAG_PIT_UNLOCKED, true);
                // Bump rep to FAVORABLE
                Global.getSector().getFaction("gladiator")
                        .setRelationship(Factions.PLAYER, RepLevel.FAVORABLE);
                ((com.fs.starfarer.api.campaign.RuleBasedDialog) dialog.getPlugin()).updateMemory();
                return true;

            default:
                break;
        }
        return super.callEvent(ruleId, dialog, params, memoryMap);
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$GS_quest_reward", Misc.getWithDGS(getCreditsReward()));
        set("$GS_quest_systemName", arenaSystem != null ? arenaSystem.getNameWithLowercaseTypeShort() : "this system");
    }

    // ── Intel panel ──────────────────────────────────────────────────────────

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float pad = 10f;
        Color h = Misc.getHighlightColor();
        if (currentStage == Stage.KILL_PATROL) {
            info.addPara("Destroy the Remnant patrol fleet in " +
                    (arenaSystem != null ? arenaSystem.getNameWithLowercaseTypeShort() : "The Arena") + ".", pad);
        } else if (currentStage == Stage.RETURN_TO_VARRO) {
            info.addPara("Return to Commander Varro at The Arena station to claim your reward.", pad);
        }
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == Stage.KILL_PATROL) {
            info.addPara("Destroy the Remnant patrol in " +
                    (arenaSystem != null ? arenaSystem.getNameWithLowercaseTypeShort() : "The Arena"), tc, pad);
            return true;
        } else if (currentStage == Stage.RETURN_TO_VARRO) {
            info.addPara("Return to Commander Varro at The Arena", tc, pad);
            return true;
        }
        return false;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (currentStage == Stage.KILL_PATROL && targetFleet != null && targetFleet.isAlive()) {
            return getMapLocationFor(targetFleet);
        }
        if (currentStage == Stage.RETURN_TO_VARRO && arenaSystem != null) {
            SectorEntityToken station = Global.getSector().getEntityById("gs_arena_station");
            return station != null ? getMapLocationFor(station) : getMapLocationFor(arenaSystem.getCenter());
        }
        return null;
    }

    @Override
    public String getBaseName() {
        return "Gladiator Society: Initiation";
    }
}
