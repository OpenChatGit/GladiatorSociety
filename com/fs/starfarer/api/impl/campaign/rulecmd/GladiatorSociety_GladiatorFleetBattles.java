package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;
import src.data.scripts.campaign.GladiatorSociety_FleetBattleContent;
import src.data.scripts.campaign.GladiatorSociety_TinyFleetFactoryV2;
import src.data.utils.GladiatorSociety_Constants;

public class GladiatorSociety_GladiatorFleetBattles extends BaseCommandPlugin {

    public static final String GS_FLEETBATTLE_CONTENT_KEY = "$GladiatorSociety_FleetBattleContentKey";

    private GladiatorSociety_FleetBattleContent content;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String arg = params.get(0).getString(memoryMap);

        if (content == null) {
            if (Global.getSector().getPersistentData().containsKey(GS_FLEETBATTLE_CONTENT_KEY)) {
                content = (GladiatorSociety_FleetBattleContent) Global.getSector().getPersistentData().get(GS_FLEETBATTLE_CONTENT_KEY);
            } else {
                content = new GladiatorSociety_FleetBattleContent();
                Global.getSector().getPersistentData().put(GS_FLEETBATTLE_CONTENT_KEY, content);
            }
        }
        if (content == null) return false;

        switch (arg) {
            case "Display":
                display(dialog, memoryMap.get(MemKeys.LOCAL));
                return true;
            case "Accept":
                accept(dialog, memoryMap.get(MemKeys.LOCAL));
                return true;
            case "Increment":
                content.nextRound();
                return true;
        }
        return false;
    }

    private void display(InteractionDialogAPI dialog, MemoryAPI memory) {
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        dialog.getTextPanel().addParagraph("Round: " + content.getRound());
        dialog.getTextPanel().addParagraph("Allied power: " + content.getAllyPower());
        dialog.getTextPanel().addParagraph("Enemy power: " + content.getEnemyPower(), Color.RED);
        dialog.getTextPanel().addParagraph("Estimated reward: " + content.getCreditReward() + " credits");

        FactionAPI enemy = Global.getSector().getFaction(content.getEnemyFaction());
        FactionAPI ally = Global.getSector().getFaction(content.getAllyFaction());
        if (ally != null) dialog.getTextPanel().addParagraph("Ally: " + ally.getDisplayNameLongWithArticle());
        if (enemy != null) dialog.getTextPanel().addParagraph("Enemy: " + enemy.getDisplayNameLongWithArticle());

        dialog.getTextPanel().addParagraph("Two fleets will appear near you: an allied escort and the enemy.", Color.ORANGE);
        opts.addOption("Accept", "AcceptFleetBattle", "Accept");
        opts.setShortcut("AcceptFleetBattle", Keyboard.KEY_H, false, false, false, false);

        if (Global.getSettings().isDevMode()) {
            opts.addOption(">> (dev) Increment", "DevIncFleetBattle", "Simulate a win (next round)");
            opts.setShortcut("DevIncFleetBattle", Keyboard.KEY_J, false, false, false, false);
        }

        String exitOpt = "gladiatorComRelay";
        opts.addOption(Misc.ucFirst("back"), exitOpt);
        opts.setShortcut(exitOpt, Keyboard.KEY_ESCAPE, false, false, false, false);
    }

    private void accept(InteractionDialogAPI dialog, MemoryAPI memory) {
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();
        dialog.getTextPanel().addParagraph("The Fleet Battle has been accepted.");

        final SectorEntityToken entity = dialog.getInteractionTarget();
        final CampaignFleetAPI enemy = spawnEnemyFleet(content);
        final CampaignFleetAPI ally = spawnAllyFleet(content, enemy);
        // Keep a seed list of enemy ships for post-battle rewards before fleets get deflated
        final java.util.List<com.fs.starfarer.api.fleet.FleetMemberAPI> enemySeed =
                (enemy != null && enemy.getFleetData() != null)
                        ? new java.util.ArrayList<>(enemy.getFleetData().getMembersListCopy())
                        : new java.util.ArrayList<>();

        // Ensure both fleets are in the current location
        if (enemy != null && enemy.getContainingLocation() == null) {
            Global.getSector().getCurrentLocation().addEntity(enemy);
        }
        if (ally != null && ally.getContainingLocation() == null) {
            Global.getSector().getCurrentLocation().addEntity(ally);
        }

        // Brief diagnostics for the player
        if (enemy != null) {
            dialog.getTextPanel().addPara("Enemy spawned: %s (ships: %s, DP: %s)",
                    Misc.getNegativeHighlightColor(),
                    enemy.getName(),
                    String.valueOf(enemy.getFleetData().getNumMembers()),
                    String.valueOf((int) enemy.getFleetPoints()));
        } else {
            dialog.getTextPanel().addPara("Enemy spawn failed!", Misc.getNegativeHighlightColor());
        }
        if (ally != null) {
            dialog.getTextPanel().addPara("Ally spawned: %s (ships: %s, DP: %s)",
                    Misc.getPositiveHighlightColor(),
                    ally.getName(),
                    String.valueOf(ally.getFleetData().getNumMembers()),
                    String.valueOf((int) ally.getFleetPoints()));
            // discourage despawn
            ally.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        } else {
            dialog.getTextPanel().addPara("Ally spawn failed!", Misc.getNegativeHighlightColor());
        }

        // If enemy failed to spawn, abort gracefully to avoid NPE in FleetInteraction
        if (enemy == null) {
            dialog.getTextPanel().addPara("Battle canceled: enemy failed to spawn.", Misc.getNegativeHighlightColor());
            if (ally != null) {
                try {
                    ally.getMemoryWithoutUpdate().clear();
                    ally.clearAssignments();
                    if (ally.getContainingLocation() != null) {
                        ally.getContainingLocation().removeEntity(ally);
                    }
                    ally.despawn();
                } catch (Throwable ignored) {}
            }
            // Return to previous dialog without starting an interaction
            OptionPanelAPI opts2 = dialog.getOptionPanel();
            opts2.addOption(Misc.ucFirst("back"), "gladiatorComRelay");
            opts2.setShortcut("gladiatorComRelay", Keyboard.KEY_ESCAPE, false, false, false, false);
            return;
        }

        dialog.setInteractionTarget(enemy);

        final FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();
        config.leaveAlwaysAvailable = false;
        config.showCommLinkOption = false;
        config.showEngageText = true;
        config.showFleetAttitude = false;
        config.showTransponderStatus = false;
        config.showWarningDialogWhenNotHostile = false;
        config.alwaysAttackVsAttack = true;
        config.impactsAllyReputation = false;
        config.impactsEnemyReputation = false;
        config.pullInAllies = true;
        config.pullInEnemies = true;
        config.pullInStations = false;
        config.lootCredits = false;
        config.firstTimeEngageOptionText = "Engage the enemy fleet";
        config.afterFirstTimeEngageOptionText = "Re-engage the enemy fleet";
        config.noSalvageLeaveOptionText = "Continue";
        config.dismissOnLeave = true;
        config.printXPToDialog = true;

        long seed = memory.getLong(MemFlags.SALVAGE_SEED);
        config.salvageRandom = Misc.getRandom(seed, 75);

        final FleetInteractionDialogPluginImpl plugin = new FleetInteractionDialogPluginImpl(config);
        final InteractionDialogPlugin originalPlugin = dialog.getPlugin();
        config.delegate = new FleetInteractionDialogPluginImpl.BaseFIDDelegate() {
            @Override
            public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
                bcc.aiRetreatAllowed = false;
                bcc.enemyDeployAll = true;
            }

            @Override
            public void notifyLeave(InteractionDialogAPI dialog) {
                // Cleanup enemy fleet
                if (enemy != null) {
                    enemy.getMemoryWithoutUpdate().clear();
                    enemy.clearAssignments();
                    if (enemy.getContainingLocation() != null) {
                        enemy.getContainingLocation().removeEntity(enemy);
                    }
                    enemy.despawn();
                }
                // Cleanup ally fleet - BUGFIX: properly despawn ally fleet
                if (ally != null) {
                    ally.getMemoryWithoutUpdate().clear();
                    ally.clearAssignments();
                    if (ally.getContainingLocation() != null) {
                        ally.getContainingLocation().removeEntity(ally);
                    }
                    ally.despawn();
                }

                dialog.setPlugin(originalPlugin);
                dialog.setInteractionTarget(entity);

                if (plugin.getContext() instanceof FleetEncounterContext) {
                    FleetEncounterContext context = (FleetEncounterContext) plugin.getContext();
                    if (context.didPlayerWinEncounterOutright()) {
                        float involvement = 1f;
                        try {
                            if (context.getBattle() != null) {
                                involvement = context.getBattle().getPlayerInvolvementFraction();
                            }
                        } catch (Throwable ignored) {}
                        int payment = (int) (content.getCreditReward() * involvement);
                        Global.getSector().getPlayerFleet().getCargo().getCredits().add(payment);
                        // Try to award a unique ship based on the defeated enemy composition (budget scales with round)
                        int budgetFP = Math.min(10 + content.getRound() * 3, 60);
                        try {
                            content.tryAwardUniqueShipFromEnemy(enemySeed, budgetFP);
                        } catch (Throwable t) {
                            Global.getLogger(GladiatorSociety_GladiatorFleetBattles.class).warn("Ship reward failed", t);
                        }
                        content.nextRound();
                    } else {
                        dialog.dismiss();
                    }
                } else {
                    dialog.dismiss();
                }
            }
        };
        dialog.setPlugin(plugin);
        plugin.init(dialog);
    }

    private PersonAPI makeCommander(FactionAPI faction) {
        float level = ((OfficerLevelupPlugin) Global.getSettings().getPlugin("officerLevelUp")).getMaxLevel(null);
        int personLevel = (int) (5 + level * 1.5f);
        return OfficerManagerEvent.createOfficer(Global.getSector().getFaction(faction.getId()), personLevel, true);
    }

    private CampaignFleetAPI spawnEnemyFleet(GladiatorSociety_FleetBattleContent content) {
        String[] fallbacks = new String[] { content.getEnemyFaction(), "pirates", "hegemony", "tritachyon" };
        for (String facId : fallbacks) {
            try {
                FactionAPI faction = Global.getSector().getFaction(facId);
                if (faction == null) continue;
                PersonAPI person = makeCommander(faction);
                float random = (int) (Math.random() * 10) / 10f;
                FleetParamsV3 params = new FleetParamsV3(
                        null, null, faction.getId(), null, FleetTypes.PERSON_BOUNTY_FLEET,
                        content.getEnemyPower() + random, 0, 0, 0f, 0f, 0f, 1f);
                params.ignoreMarketFleetSizeMult = true;
                CampaignFleetAPI fleet = GladiatorSociety_TinyFleetFactoryV2.createFleet(params);
                if (fleet == null || fleet.isEmpty()) continue;
                fleet.setCommander(person);
                if (fleet.getFlagship() != null) fleet.getFlagship().setCaptain(person);
                FleetFactoryV3.addCommanderSkills(person, fleet, null);
                Misc.makeImportant(fleet, GladiatorSociety_Constants.GSFACTION_ID, 120);
                fleet.setNoFactionInName(true);
                fleet.setFaction(GladiatorSociety_Constants.GSFACTION_ID, true);
                fleet.setName("Gladiator enemy fleet");
                fleet.getAI().addAssignment(FleetAssignment.INTERCEPT, Global.getSector().getPlayerFleet(), 1000000f, null);
                fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
                fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
                CampaignFleetAPI player = Global.getSector().getPlayerFleet();
                if (fleet.getContainingLocation() == null) {
                    Global.getSector().getCurrentLocation().addEntity(fleet);
                }
                fleet.setLocation(player.getLocation().x + 500f, player.getLocation().y + 500f);
                return fleet;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private CampaignFleetAPI spawnAllyFleet(GladiatorSociety_FleetBattleContent content, CampaignFleetAPI enemyToHunt) {
        String allyFactionId = content.getAllyFaction();
        String[] fallbacks = new String[] { allyFactionId, "independent", "hegemony" };
        for (String facId : fallbacks) {
            try {
                FactionAPI faction = Global.getSector().getFaction(facId);
                if (faction == null) continue;
                PersonAPI person = makeCommander(faction);
                float random = (int) (Math.random() * 10) / 10f;
                FleetParamsV3 params = new FleetParamsV3(
                        null, null, faction.getId(), null, FleetTypes.PATROL_MEDIUM,
                        content.getEnemyPower() + random, 0, 0, 0f, 0f, 0f, 1f);
                params.ignoreMarketFleetSizeMult = true;
                CampaignFleetAPI fleet = GladiatorSociety_TinyFleetFactoryV2.createFleet(params);
                if (fleet == null || fleet.isEmpty()) continue;
                fleet.setCommander(person);
                if (fleet.getFlagship() != null) fleet.getFlagship().setCaptain(person);
                FleetFactoryV3.addCommanderSkills(person, fleet, null);
                String playerFactionId = Global.getSector().getPlayerFaction().getId();
                fleet.setNoFactionInName(true);
                fleet.setFaction(playerFactionId, true);
                FactionAPI allyFac = Global.getSector().getFaction(allyFactionId);
                fleet.setName("Allied " + (allyFac != null ? allyFac.getDisplayName() : "fleet"));
                CampaignFleetAPI player = Global.getSector().getPlayerFleet();
                fleet.getAI().addAssignment(FleetAssignment.DEFEND_LOCATION, player, 1000000f, null);
                fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
                if (fleet.getContainingLocation() == null) {
                    Global.getSector().getCurrentLocation().addEntity(fleet);
                }
                fleet.setLocation(player.getLocation().x + 20f, player.getLocation().y + 20f);
                Global.getLogger(GladiatorSociety_GladiatorFleetBattles.class).info("Ally spawned at " + fleet.getLocation() + ", enemy target set: " + (enemyToHunt != null));
                return fleet;
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
