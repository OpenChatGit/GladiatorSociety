package src.data.scripts.campaign;

import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionDoctrineAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfigGen;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDDelegate;
import static com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3.*;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import src.data.scripts.campaign.dataclass.GladiatorSociety_BountyData;
import src.data.scripts.campaign.dataclass.GladiatorSociety_DataShip;
import src.data.scripts.campaign.GladiatorSociety_FactionDiscoveryConfig;

public class GladiatorSociety_TinyFleetFactoryV2 {

    public static final Logger LOG = Global.getLogger(GladiatorSociety_TinyFleetFactoryV2.class);

    public static void addGSInteractionConfig(CampaignFleetAPI fleet) {
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN,
                new GSFleetInteractionConfigGen());
    }

    private static boolean isValidationFleet(FleetParamsV3 params) {
        return params != null && params.fleetType != null
                && params.fleetType.equals(GladiatorSociety_FactionDiscoveryConfig.load().validationFleetType);
    }

    private static void markValidationFleet(CampaignFleetAPI fleet) {
        fleet.getMemoryWithoutUpdate().set("$gs_validation", true);
    }

    public static class GSFleetInteractionConfigGen implements FIDConfigGen {

        @Override
        public FIDConfig createConfig() {
            FIDConfig config = new FIDConfig();
            config.showTransponderStatus = false;
            config.delegate = new FIDDelegate() {

                @Override
                public void postPlayerSalvageGeneration(InteractionDialogAPI dialog, FleetEncounterContext context, CargoAPI salvage) {
                }

                @Override
                public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
                    bcc.aiRetreatAllowed = false;

                }

                @Override
                public void notifyLeave(InteractionDialogAPI dialog) {
                }
            };
            return config;
        }
    }

    public static CampaignFleetAPI createFleet(FleetParamsV3 params, GladiatorSociety_BountyData mission,PersonAPI person) {
        Global.getSettings().profilerBegin("GladiatorSociety_TinyFleetFactoryV2.createFleet()");
        MarketAPI originalSource = params == null ? null : params.source;
        try {
            if (params == null || mission == null || mission.flagship == null || person == null) {
                LOG.warn("Cannot generate bounty fleet: required fleet parameters are missing");
                return null;
            }
            GladiatorSociety_DataShip mothership = mission.flagship;
            List<GladiatorSociety_DataShip> ships = mission.advships == null
                    ? java.util.Collections.<GladiatorSociety_DataShip>emptyList() : mission.advships;
            boolean randomFleet = mission.randomFleet;

            MarketAPI market = pickMarket(params);
            if (market == null) {
                market = Global.getFactory().createMarket("fake", "fake", 5);
                market.getStability().modifyFlat("fake", 10000);
                market.setFactionId(params.factionId);
                SectorEntityToken token = Global.getSector().getHyperspace().createToken(0, 0);
                market.setPrimaryEntity(token);
                
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat("fake", BASE_QUALITY_WHEN_NO_MARKET);

                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlat("fake", 1f);

            }
            boolean sourceWasNull = params.source == null;
            params.source = market;
            if (sourceWasNull && params.qualityOverride == null) { // we picked a nearby market based on location
                params.updateQualityAndProducerFromSourceMarket();
            }

            String factionId = params.factionId;
            if (factionId == null) {
                factionId = params.source.getFactionId();
            }

            ShipPickMode mode = Misc.getShipPickMode(market, factionId);
            if (params.modeOverride != null) {
                mode = params.modeOverride;
            }

            CampaignFleetAPI fleet = createEmptyFleet(factionId, params.fleetType, market);
            fleet.getFleetData().setOnlySyncMemberLists(true);
            FleetMemberAPI memb = null;
            ShipVariantAPI variant2;
              int countFleet = 0;
            if (mothership.hullid != null) {
                variant2 = GladiatorSociety_CustomVariantFactory.create(mothership.ship);
                if (variant2 != null) {
                    memb = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant2);
                   
                }
            } else {
                memb = Global.getFactory().createFleetMember(FleetMemberType.SHIP, mothership.ship);
            }
            if (memb != null) {
                fleet.getFleetData().addFleetMember(memb);
                 countFleet++;
                memb.setShipName(person.getName().getFullName() + "'s Ship");
            } else {
                LOG.info("|||   Creating MotherShip failed   |||");
            }
            LOG.info("|||   Creating Custom Fleet Begin   |||");
            LOG.info("Custom Fleet Array Size: " + ships.size());

          
            for (GladiatorSociety_DataShip variant : ships) {
                if (variant == null || variant.ship == null || variant.ship.isEmpty()) {
                    continue;
                }

                LOG.info("Create " + variant.ship + " x " + variant.number);
                if (variant.hullid != null) {
                    variant2 = GladiatorSociety_CustomVariantFactory.create(variant.ship);
                    if (variant2 != null) {
                        for (int i = 0; i < variant.number; i++) {
                            memb = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant2);
                            if (memb != null) {
                                if (!variant.personality.equals("steady")) {
                                    memb.getVariant().addMod("GladiatorSociety_" + variant.personality);
                                }
                                fleet.getFleetData().addFleetMember(memb);
                                countFleet++;
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < variant.number; i++) {
                        memb = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant.ship);
                        if (memb != null) {
                            if (!variant.personality.equals("steady")) {
                                memb.getVariant().addMod("GladiatorSociety_" + variant.personality);
                            }
                            fleet.getFleetData().addFleetMember(memb);
                            countFleet++;
                        }
                    }
                }

            }

            FactionDoctrineAPI doctrine = fleet.getFaction().getDoctrine();
            if (params.doctrineOverride != null) {
                doctrine = params.doctrineOverride;
            }
            if (doctrine == null) {
                FactionAPI fallbackFaction = Global.getSector().getFaction("independent");
                doctrine = fallbackFaction == null ? null : fallbackFaction.getDoctrine();
            }
            if (doctrine == null) {
                LOG.warn("Cannot generate fleet for " + factionId + ": faction doctrine is unavailable");
                return null;
            }

            float numShipsMult = 1f;
            if (params.ignoreMarketFleetSizeMult == null || !params.ignoreMarketFleetSizeMult) {
                numShipsMult = market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f);
            }

            Random random = new Random();
            if (params.random != null) {
                random = params.random;
            }

          //  float combatPts = params.combatPts * (3);//(numShipsMult + 3);
          
            float combatPts = params.combatPts;
              if(countFleet==0){
                  combatPts+=10;
              }
            if (params.onlyApplyFleetSizeToCombatShips != null && params.onlyApplyFleetSizeToCombatShips) {
                numShipsMult = 1f;
            }

            float freighterPts = params.freighterPts * numShipsMult;

            if (combatPts < 10 && combatPts > 0) {
                combatPts = Math.max(combatPts, 5 + random.nextInt(6));
            }

            float dW = (float) doctrine.getWarships() + random.nextInt(3) - 2;
            float dC = (float) doctrine.getCarriers() + random.nextInt(3) - 2;
            float dP = (float) doctrine.getPhaseShips() + random.nextInt(3) - 2;

            float r1 = random.nextFloat();
            float r2 = random.nextFloat();
            float min = Math.min(r1, r2);
            float max = Math.max(r1, r2);

            float mag = 1f;
            float v1 = min;
            float v2 = max - min;
            float v3 = 1f - max;

            v1 *= mag;
            v2 *= mag;
            v3 *= mag;

            v1 -= mag / 3f;
            v2 -= mag / 3f;
            v3 -= mag / 3f;

            dW += v1;
            dC += v2;
            dP += v3;

            boolean banPhaseShipsEtc = !fleet.getFaction().isPlayerFaction()
                    && combatPts < FLEET_POINTS_THRESHOLD_FOR_ANNOYING_SHIPS;
            if (params.forceAllowPhaseShipsEtc != null && params.forceAllowPhaseShipsEtc) {
                banPhaseShipsEtc = !params.forceAllowPhaseShipsEtc;
            }

            params.mode = mode;
            params.banPhaseShipsEtc = banPhaseShipsEtc;

            if (banPhaseShipsEtc) {
                dP = 0;
            };

            if (dW < 0) {
                dW = 0;
            }
            if (dC < 0) {
                dC = 0;
            }
            if (dP < 0) {
                dP = 0;
            }

            float extra = 7 - (dC + dP + dW);
            if (extra < 0) {
                extra = 0f;
            }
            if (doctrine.getWarships() > doctrine.getCarriers() && doctrine.getWarships() > doctrine.getPhaseShips()) {
                dW += extra;
            } else if (doctrine.getCarriers() > doctrine.getWarships() && doctrine.getCarriers() > doctrine.getPhaseShips()) {
                dC += extra;
            } else if (doctrine.getPhaseShips() > doctrine.getWarships() && doctrine.getPhaseShips() > doctrine.getCarriers()) {
                dP += extra;
            }

            float doctrineTotal = dW + dC + dP;

            // Some mod factions have incomplete doctrine data. Avoid dividing by zero
            // and still generate a usable warship fleet for them.
            if (!(doctrineTotal > 0f) || Float.isInfinite(doctrineTotal)) {
                dW = 1f;
                dC = 0f;
                dP = 0f;
                doctrineTotal = 1f;
            }

            //System.out.println("DW: " + dW + ", DC: " + dC + " DP: " + dP);
            combatPts = (int) combatPts;
            int warships = (int) (combatPts * dW / doctrineTotal);
            int carriers = (int) (combatPts * dC / doctrineTotal);
            int phase = (int) (combatPts * dP / doctrineTotal);

            warships += (combatPts - warships - carriers - phase);
            CampaignFleetAPI subfleet = createEmptyFleet(factionId, params.fleetType, market);

            if (randomFleet) {
                if (params.treatCombatFreighterSettingAsFraction != null && params.treatCombatFreighterSettingAsFraction) {
                    float combatFreighters = (int) Math.min(freighterPts * 1.5f, warships * 1.5f) * doctrine.getCombatFreighterProbability();
                    float added = addCombatFreighterFleetPoints(subfleet, random, combatFreighters, params);
                    freighterPts -= added * 0.5f;
                    warships -= added * 0.5f;
                } else if (freighterPts > 0 && random.nextFloat() < doctrine.getCombatFreighterProbability()) {
                    float combatFreighters = (int) Math.min(freighterPts * 1.5f, warships * 1.5f);
                    float added = addCombatFreighterFleetPoints(subfleet, random, combatFreighters, params);
                    freighterPts -= added * 0.5f;
                    warships -= added * 0.5f;
                }
                params.maxShipSize = 4;

                addCombatFleetPoints(subfleet, random, warships, carriers, phase, params);
                addFreighterFleetPoints(subfleet, random, freighterPts, params);

                subfleet.getFleetData().sort();
            }
            List<FleetMemberAPI> members = subfleet.getFleetData().getMembersListCopy();
            for (FleetMemberAPI mem : members) {
                fleet.getFleetData().addFleetMember(mem);
            }

            /* members = fleet.getFleetData().getMembersListCopy();
            boolean hasShip = false;
            for (FleetMemberAPI member : members) {

                if (!member.isFighterWing()) {
                    hasShip = true;
                }
            }
            
            if (!hasShip && randomFleet) {
                addRandomShips(1f, 0, fleet, random, market, ShipRoles.COMBAT_SMALL);
            }*/
            if (params.withOfficers) {
                com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3.addCommanderAndOfficersV2(fleet, params, random);
            }

            fleet.forceSync();

            if (fleet.getFleetData().getNumMembers() <= 0
                    || fleet.getFleetData().getNumMembers() == fleet.getNumFighters()) {
            }

            /*  DefaultFleetInflaterParams p = new DefaultFleetInflaterParams();
            p.quality = quality;
            p.persistent = true;
            p.seed = random.nextLong();
            p.mode = mode;
            p.timestamp = params.timestamp;

            FleetInflater inflater = Misc.getInflater(fleet, p);
            fleet.setInflater(inflater);*/
            fleet.getFleetData().setOnlySyncMemberLists(false);
            fleet.getFleetData().sort();

            members = fleet.getFleetData().getMembersListCopy();
            for (FleetMemberAPI member : members) {
                member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
            }
            // If this is a validator/test fleet, do NOT attach GS interaction or extra behavior
            if (!isValidationFleet(params)) {
                GladiatorSociety_TinyFleetFactoryV2.addGSInteractionConfig(fleet);
            } else {
                // Mark to help other systems ignore this ephemeral fleet
                markValidationFleet(fleet);
            }

            return fleet;

        } finally {
            if (params != null) params.source = originalSource;
            Global.getSettings().profilerEnd();
        }
    }

    /**
     * Simple fleet creation without custom bounty ships - delegates directly to FleetFactoryV3.
     * Used by Endless Battle, Fleet Battles, patrol spawning, and faction validation.
     */
    public static CampaignFleetAPI createFleet(FleetParamsV3 params) {
        CampaignFleetAPI fleet = com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return fleet;

        // Restore CR (FleetFactoryV3 may leave ships at partial CR)
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
        }

        // Skip GS interaction config for validation fleets
        if (!isValidationFleet(params)) {
            addGSInteractionConfig(fleet);
        } else {
            markValidationFleet(fleet);
        }

        return fleet;
    }

}
