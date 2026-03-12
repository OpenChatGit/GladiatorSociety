package src.data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Planets;
import com.fs.starfarer.api.impl.campaign.ids.StarTypes;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.DerelictThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.GateCMD;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.apache.log4j.Logger;

public class GladiatorSociety_SalvagePit {

    public static final Logger LOG = Global.getLogger(GladiatorSociety_SalvagePit.class);

    public static final String MEMORY_IN_PIT         = "$GS_inSalvagePit";
    public static final String MEMORY_RETURN_LOC     = "$GS_salvagePitReturnLoc";
    public static final String MEMORY_RETURN_X       = "$GS_salvagePitReturnX";
    public static final String MEMORY_RETURN_Y       = "$GS_salvagePitReturnY";
    public static final String MEMORY_PIT_SYSTEM     = "$GS_salvagePitSystem";
    public static final String MEMORY_ENTRY_ENTITY   = "$GS_salvagePitEntryEntity";
    public static final String MEMORY_RETURN_ANCHOR  = "$GS_salvagePitReturnAnchorId";

    public static final String ENTRY_ENTITY_TYPE = "gs_salvage_pit_entry";
    public static final String EXIT_ENTITY_TYPE  = "gs_salvage_pit_exit";

    // ── Spawn entry gate next to the Comm Relay ───────────────────────────────

    public static void spawnEntryGate(SectorEntityToken anchor) {
        removeEntryGate();

        LocationAPI loc = anchor.getContainingLocation();

        // Find Gladius Prime to orbit around it
        SectorEntityToken orbitCenter = Global.getSector().getEntityById("gs_gladius_prime");
        if (orbitCenter == null) orbitCenter = anchor; // fallback

        // Place gate in orbit around Gladius Prime, 180° from the Arena Station
        float orbitRadius = 550f;
        float stationAngle = 0f;
        if (orbitCenter != anchor) {
            float dx = anchor.getLocation().x - orbitCenter.getLocation().x;
            float dy = anchor.getLocation().y - orbitCenter.getLocation().y;
            stationAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
        }
        float gateAngle = stationAngle + 180f;
        float gateX = orbitCenter.getLocation().x + (float) Math.cos(Math.toRadians(gateAngle)) * orbitRadius;
        float gateY = orbitCenter.getLocation().y + (float) Math.sin(Math.toRadians(gateAngle)) * orbitRadius;

        SectorEntityToken gate = loc.addCustomEntity(
                "gs_salvage_pit_entry_" + System.currentTimeMillis(),
                "Salvage Pit Gate",
                ENTRY_ENTITY_TYPE,
                Factions.NEUTRAL
        );
        gate.setCircularOrbit(orbitCenter, gateAngle, orbitRadius, 300f);

        // Make the gate visually active
        GateCMD.notifyScanned(gate);
        gate.getMemoryWithoutUpdate().set(GateEntityPlugin.GATE_SCANNED, true);
        gate.getMemoryWithoutUpdate().set(GateEntityPlugin.GATES_ACTIVE, true);
        gate.getMemoryWithoutUpdate().set(GateEntityPlugin.PLAYER_CAN_USE_GATES, true);

        // Save gate + return anchor info
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_ENTRY_ENTITY, gate);
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_RETURN_LOC, loc.getId());
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_RETURN_X, anchor.getLocation().x);
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_RETURN_Y, anchor.getLocation().y);
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_RETURN_ANCHOR, anchor.getId());

        LOG.info("GS Salvage Pit: Entry gate spawned in orbit around Gladius Prime at angle " + gateAngle);
    }

    public static void removeEntryGate() {
        SectorEntityToken existing = (SectorEntityToken)
                Global.getSector().getMemoryWithoutUpdate().get(MEMORY_ENTRY_ENTITY);
        if (existing != null && existing.getContainingLocation() != null) {
            existing.getContainingLocation().removeEntity(existing);
        }
        Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_ENTRY_ENTITY);
    }

    // ── Enter the Salvage Pit ─────────────────────────────────────────────────

    public static void enterSalvagePit(com.fs.starfarer.api.campaign.InteractionDialogAPI dialog) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        final SectorEntityToken entryGate = (SectorEntityToken)
                Global.getSector().getMemoryWithoutUpdate().get(MEMORY_ENTRY_ENTITY);

        // Generate system BEFORE removing gate or dismissing dialog
        StarSystemAPI pitSystem = generatePitSystem();
        if (pitSystem == null) {
            LOG.error("GS Salvage Pit: Failed to generate system!");
            return;
        }

        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_PIT_SYSTEM, pitSystem.getId());
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_IN_PIT, true);

        // Dismiss dialog first so the transition animation plays cleanly
        if (dialog != null) dialog.dismiss();

        // Do the hyperspace transition - this plays the jump animation
        SectorEntityToken exitGate = pitSystem.getEntityById(pitSystem.getId() + "_exit");
        JumpPointAPI.JumpDestination dest = new JumpPointAPI.JumpDestination(
                exitGate != null ? exitGate : pitSystem.getStar(), null);
        dest.setMinDistFromToken(600f);
        dest.setMaxDistFromToken(1200f);
        Global.getSector().doHyperspaceTransition(player,
                entryGate != null ? entryGate : player, dest);

        // Remove entry gate AFTER transition (short delay so it doesn't pop away visually)
        Global.getSector().addTransientScript(new com.fs.starfarer.api.EveryFrameScript() {
            private float t = 0f;
            public boolean isDone() { return t > 1f; }
            public boolean runWhilePaused() { return false; }
            public void advance(float amount) {
                t += amount;
                if (t > 1f && entryGate != null && entryGate.getContainingLocation() != null) {
                    entryGate.getContainingLocation().removeEntity(entryGate);
                    Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_ENTRY_ENTITY);
                }
            }
        });

        LOG.info("GS Salvage Pit: Player entered " + pitSystem.getId());
    }

    // ── Exit the Salvage Pit ──────────────────────────────────────────────────

    public static void exitSalvagePit(com.fs.starfarer.api.campaign.InteractionDialogAPI dialog) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        // Find exit gate as "from" anchor for the transition
        String pitSystemId = (String) Global.getSector().getMemoryWithoutUpdate().get(MEMORY_PIT_SYSTEM);
        SectorEntityToken exitGate = null;
        final StarSystemAPI pitSystem = findSystem(pitSystemId);
        if (pitSystem != null) {
            exitGate = pitSystem.getEntityById(pitSystemId + "_exit");
        }

        // Find return anchor
        String returnLocId = (String) Global.getSector().getMemoryWithoutUpdate().get(MEMORY_RETURN_LOC);
        String returnAnchorId = (String) Global.getSector().getMemoryWithoutUpdate().get(MEMORY_RETURN_ANCHOR);
        float returnX = Global.getSector().getMemoryWithoutUpdate().getFloat(MEMORY_RETURN_X);
        float returnY = Global.getSector().getMemoryWithoutUpdate().getFloat(MEMORY_RETURN_Y);

        LocationAPI returnLoc = findLocation(returnLocId);
        if (returnLoc == null) {
            returnLoc = Global.getSector().getHyperspace();
            returnX = 0f; returnY = 0f;
        }

        SectorEntityToken returnAnchor = returnAnchorId != null
                ? returnLoc.getEntityById(returnAnchorId) : null;

        if (returnAnchor != null) {
            // Dismiss dialog before transition so animation plays cleanly
            if (dialog != null) dialog.dismiss();
            JumpPointAPI.JumpDestination dest = new JumpPointAPI.JumpDestination(returnAnchor, null);
            dest.setMinDistFromToken(500f);
            dest.setMaxDistFromToken(900f);
            Global.getSector().doHyperspaceTransition(player,
                    exitGate != null ? exitGate : player, dest);
        } else {
            // Fallback manual move
            if (dialog != null) dialog.dismiss();
            if (player.getContainingLocation() != null) {
                player.getContainingLocation().removeEntity(player);
            }
            player.setLocation(returnX + 500f, returnY + 500f);
            returnLoc.addEntity(player);
            Global.getSector().setCurrentLocation(returnLoc);
        }

        // Remove pit system after transition completes
        if (pitSystem != null) {
            Global.getSector().addTransientScript(new com.fs.starfarer.api.EveryFrameScript() {
                private float t = 0f;
                public boolean isDone() { return t > 3f; }
                public boolean runWhilePaused() { return false; }
                public void advance(float amount) {
                    t += amount;
                    if (t > 3f) {
                        try { Global.getSector().removeStarSystem(pitSystem); }
                        catch (Throwable e) { LOG.warn("GS: Could not remove pit system", e); }
                    }
                }
            });
        }

        clearMemory();
        LOG.info("GS Salvage Pit: Player exited");
    }

    // ── System generation ─────────────────────────────────────────────────────

    private static StarSystemAPI generatePitSystem() {
        try {
            Random rand = new Random();
            String systemId = "gs_salvage_pit_" + System.currentTimeMillis();

            StarSystemAPI system = Global.getSector().createStarSystem(systemId);
            system.setEnteredByPlayer(false);
            system.setName("The Salvage Pit");
            system.setBackgroundTextureFilename("graphics/backgrounds/background3.jpg");

            system.initStar(systemId + "_star", StarTypes.BROWN_DWARF,
                    150f, 200f, 3f, 0.5f, 2f);

            // Build StarSystemData for theme generator methods
            BaseThemeGenerator.StarSystemData sysData = new BaseThemeGenerator.StarSystemData();
            sysData.system = system;
            sysData.stars.add(system.getStar());
            sysData.alreadyUsed = new HashSet<SectorEntityToken>();

            // --- Debris rings ---
            addDebrisRing(system, 1800f, rand);
            addDebrisRing(system, 3200f, rand);
            addDebrisRing(system, 5000f, rand);

            // --- Planets (2-4 unsurveyed, scannable) ---
            addPlanets(system, sysData, rand);

            // --- Derelict ships + caches (loot) ---
            DerelictThemeGenerator derelictGen = new DerelictThemeGenerator();
            WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<String>(rand);
            factionPicker.add(Factions.REMNANTS, 3f);
            factionPicker.add(Factions.INDEPENDENT, 1f);

            derelictGen.addDerelictShips(sysData, 1f, 4, 7, factionPicker);
            derelictGen.addShipGraveyard(sysData, 1f, 3, 6, factionPicker);

            // --- Supply/weapons/tech caches (2-4) - full pool ---
            WeightedRandomPicker<String> cachePicker = new WeightedRandomPicker<String>(rand);
            // Supply
            cachePicker.add("supply_cache",              3f);
            cachePicker.add("supply_cache_small",        2f);
            // Weapons
            cachePicker.add("weapons_cache",             3f);
            cachePicker.add("weapons_cache_low",         2f);
            cachePicker.add("weapons_cache_high",        1f);
            cachePicker.add("weapons_cache_remnant",     2f);
            cachePicker.add("weapons_cache_small",       2f);
            cachePicker.add("weapons_cache_small_low",   1f);
            cachePicker.add("weapons_cache_small_high",  0.5f);
            cachePicker.add("weapons_cache_small_remnant", 1f);
            // Tech/Equipment
            cachePicker.add("equipment_cache",           2f);
            cachePicker.add("equipment_cache_small",     1f);
            cachePicker.add("technology_cache",          1f);
            // Rare special
            cachePicker.add("large_cache",               0.5f);
            cachePicker.add("hidden_cache",              0.3f);
            cachePicker.add("alpha_site_weapons_cache",  0.2f);
            derelictGen.addCaches(sysData, 1f, 2, 4, cachePicker);

            // --- Mining stations (1-2 immer) - full pool ---
            WeightedRandomPicker<String> miningPicker = new WeightedRandomPicker<String>(rand);
            miningPicker.add("station_mining",          3f);
            miningPicker.add("station_mining_remnant",  2f);
            miningPicker.add("station_mining00",        1f);
            miningPicker.add("orbital_habitat",         1f);
            miningPicker.add("orbital_habitat_remnant", 1f);
            miningPicker.add("orbital_dockyard",        0.5f);
            miningPicker.add("orbital_junk",            1f);
            derelictGen.addMiningStations(sysData, 1f, 1, 2, miningPicker);

            // --- Research station (30% Chance) - full pool ---
            if (rand.nextFloat() < 0.30f) {
                WeightedRandomPicker<String> researchPicker = new WeightedRandomPicker<String>(rand);
                researchPicker.add("station_research",         3f);
                researchPicker.add("station_research_remnant", 2f);
                derelictGen.addResearchStations(sysData, 1f, 1, 1, researchPicker);
            }

            // --- Hab centers (40% Chance, 1-2) ---
            if (rand.nextFloat() < 0.40f) {
                WeightedRandomPicker<String> habPicker = new WeightedRandomPicker<String>(rand);
                habPicker.add("orbital_habitat",         3f);
                habPicker.add("orbital_habitat_remnant", 2f);
                habPicker.add("orbital_dockyard",        1f);
                derelictGen.addHabCenters(sysData, 1f, 1, 2, habPicker);
            }

            // --- Objectives: nav buoys + comm relays ---
            derelictGen.addObjectives(sysData, 1f);

            // --- Rare derelict specials (probe, survey ship, mothership) ---
            if (rand.nextFloat() < 0.4f) {
                WeightedRandomPicker<String> specialPicker = new WeightedRandomPicker<String>(rand);
                specialPicker.add("derelict_probe",       3f);
                specialPicker.add("generic_probe",        2f);
                specialPicker.add("derelict_survey_ship", 1f);
                specialPicker.add("derelict_mothership",  0.3f);
                specialPicker.add("derelict_vambrace",    0.5f);
                String specialType = specialPicker.pick();
                BaseThemeGenerator.EntityLocation loc = BaseThemeGenerator.pickAnyLocation(
                        rand, system, 200f, sysData.alreadyUsed);
                if (loc != null) {
                    BaseThemeGenerator.addSalvageEntity(rand, system, specialType, Factions.NEUTRAL);
                }
            }

            // --- Remnant Nexus Station (always 1) ---
            // addBattlestations expects variant IDs, not entity type IDs
            RemnantThemeGenerator remnantGen = new RemnantThemeGenerator();
            WeightedRandomPicker<String> stationPicker = new WeightedRandomPicker<String>(rand);
            stationPicker.add("remnant_station2_Standard", 2f);
            stationPicker.add("remnant_station2_Damaged", 1f);
            List<CampaignFleetAPI> stations = remnantGen.addBattlestations(sysData, 1f, 1, 1, stationPicker);

            // --- Remnant patrol fleets scaled to player strength ---
            spawnRemnantFleets(system, rand, stations);

            // --- Exit gate in stable orbit ---
            SectorEntityToken exitGate = system.addCustomEntity(
                    systemId + "_exit",
                    "Salvage Pit Exit Gate",
                    EXIT_ENTITY_TYPE,
                    Factions.NEUTRAL
            );
            exitGate.setCircularOrbit(system.getStar(), rand.nextFloat() * 360f, 2500f, 600f);
            GateCMD.notifyScanned(exitGate);
            exitGate.getMemoryWithoutUpdate().set(GateEntityPlugin.GATE_SCANNED, true);
            exitGate.getMemoryWithoutUpdate().set(GateEntityPlugin.GATES_ACTIVE, true);
            exitGate.getMemoryWithoutUpdate().set(GateEntityPlugin.PLAYER_CAN_USE_GATES, true);

            LOG.info("GS Salvage Pit: System generated: " + systemId);
            return system;

        } catch (Throwable t) {
            LOG.error("GS Salvage Pit: System generation failed", t);
            return null;
        }
    }

    private static void addPlanets(StarSystemAPI system, BaseThemeGenerator.StarSystemData sysData, Random rand) {
        // All available planet types, weighted by how fitting they are for a salvage pit
        WeightedRandomPicker<String> barrenPool = new WeightedRandomPicker<String>(rand);
        barrenPool.add(Planets.BARREN,              4f);
        barrenPool.add(Planets.BARREN2,             3f);
        barrenPool.add(Planets.BARREN3,             3f);
        barrenPool.add(Planets.BARREN_CASTIRON,     2f);
        barrenPool.add(Planets.BARREN_BOMBARDED,    2f);
        barrenPool.add(Planets.BARREN_VENUSLIKE,    2f);
        barrenPool.add(Planets.BARREN_DESERT,       2f);
        barrenPool.add(Planets.ROCKY_METALLIC,      3f);
        barrenPool.add(Planets.ROCKY_UNSTABLE,      3f);
        barrenPool.add(Planets.ROCKY_ICE,           2f);
        barrenPool.add(Planets.FROZEN,              3f);
        barrenPool.add(Planets.FROZEN1,             2f);
        barrenPool.add(Planets.FROZEN2,             2f);
        barrenPool.add(Planets.FROZEN3,             2f);
        barrenPool.add(Planets.PLANET_LAVA,         2f);
        barrenPool.add(Planets.PLANET_LAVA_MINOR,   2f);
        barrenPool.add(Planets.IRRADIATED,          2f);
        barrenPool.add(Planets.CRYOVOLCANIC,        1f);
        barrenPool.add(Planets.DESERT,              1f);
        barrenPool.add(Planets.DESERT1,             1f);
        barrenPool.add(Planets.ARID,                1f);
        barrenPool.add(Planets.TUNDRA,              1f);
        // Rare: actually habitable worlds hidden in the pit
        barrenPool.add(Planets.PLANET_WATER,        0.3f);
        barrenPool.add(Planets.PLANET_TERRAN,       0.2f);
        barrenPool.add(Planets.PLANET_TERRAN_ECCENTRIC, 0.2f);

        WeightedRandomPicker<String> gasPool = new WeightedRandomPicker<String>(rand);
        gasPool.add(Planets.GAS_GIANT,  3f);
        gasPool.add(Planets.ICE_GIANT,  2f);

        int planetCount = 2 + rand.nextInt(3); // 2-4 planets
        float[] orbitRadii = { 2200f, 3500f, 5500f, 7500f };

        for (int i = 0; i < planetCount && i < orbitRadii.length; i++) {
            try {
                boolean isGas = (i == planetCount - 1) && rand.nextFloat() < 0.4f;
                String planetType = isGas ? gasPool.pick() : barrenPool.pick();
                float radius = isGas ? (120f + rand.nextFloat() * 80f) : (55f + rand.nextFloat() * 50f);
                float orbitDays = 200f + orbitRadii[i] / 20f + rand.nextFloat() * 100f;

                PlanetAPI planet = system.addPlanet(
                        system.getId() + "_planet_" + i,
                        system.getStar(),
                        "Planet " + (i + 1),
                        planetType,
                        rand.nextFloat() * 360f,
                        radius,
                        orbitRadii[i],
                        orbitDays
                );
                planet.getMemoryWithoutUpdate().set(com.fs.starfarer.api.util.Misc.UNSURVEYED, true);
                sysData.planets.add(planet);

            } catch (Throwable t) {
                LOG.warn("GS Salvage Pit: Could not add planet " + i, t);
            }
        }
    }

    private static void addDebrisRing(StarSystemAPI system, float orbitRadius, Random rand) {
        try {
            DebrisFieldTerrainPlugin.DebrisFieldParams params =
                    new DebrisFieldTerrainPlugin.DebrisFieldParams(
                            orbitRadius * 0.25f, // band width
                            1f,                  // density
                            150f,                // min size
                            500f                 // max size
                    );
            params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.MIXED;
            SectorEntityToken debris = system.addTerrain(Terrain.DEBRIS_FIELD, params);
            debris.setCircularOrbit(system.getStar(),
                    rand.nextFloat() * 360f, orbitRadius, 350f + rand.nextFloat() * 200f);
        } catch (Throwable t) {
            LOG.warn("GS Salvage Pit: Could not add debris ring at " + orbitRadius, t);
        }
    }

    private static void spawnRemnantFleets(StarSystemAPI system, Random rand, List<CampaignFleetAPI> stations) {
        // Scale to player fleet points
        int playerFP = Global.getSector().getPlayerFleet().getFleetPoints();

        // Combat points per fleet: 40-60% of player FP, clamped 40-250
        float basePts = Math.max(40f, Math.min(playerFP * 0.5f, 250f));

        // 3-5 fleets, +1 for every 100 player FP above 100
        int fleetCount = 3 + Math.min(2, Math.max(0, (playerFP - 100) / 100));

        for (int i = 0; i < fleetCount; i++) {
            try {
                float combatPts = basePts * (0.8f + rand.nextFloat() * 0.4f); // ±20% variance

                com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3 params =
                        new com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3(
                                null, null,
                                Factions.REMNANTS,
                                null,
                                com.fs.starfarer.api.impl.campaign.ids.FleetTypes.PATROL_MEDIUM,
                                combatPts,
                                0, 0, 0f, 0f, 0f, 1f
                        );
                // Remnant quality: 0.5 = normal, not Ordo-tier
                params.quality = 0.5f;
                params.withOfficers = true;

                CampaignFleetAPI fleet =
                        com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3.createFleet(params);
                if (fleet == null || fleet.isEmpty()) continue;

                // Apply Remnant AI behavior (aggressive, no retreat)
                RemnantSeededFleetManager.initRemnantFleetProperties(rand, fleet, false);
                RemnantSeededFleetManager.addRemnantInteractionConfig(fleet);

                float angle = rand.nextFloat() * 360f;
                float dist = 1200f + rand.nextFloat() * 3500f;
                fleet.setLocation(
                        (float) Math.cos(Math.toRadians(angle)) * dist,
                        (float) Math.sin(Math.toRadians(angle)) * dist
                );
                system.addEntity(fleet);

            } catch (Throwable t) {
                LOG.warn("GS Salvage Pit: Could not spawn remnant fleet " + i, t);
            }
        }

        LOG.info("GS Salvage Pit: Spawned " + fleetCount + " Remnant fleets (~" + (int)basePts + " pts each, player FP: " + playerFP + ")");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static boolean isInSalvagePit() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(MEMORY_IN_PIT);
    }

    private static StarSystemAPI findSystem(String id) {
        if (id == null) return null;
        for (StarSystemAPI sys : Global.getSector().getStarSystems()) {
            if (sys.getId().equals(id)) return sys;
        }
        return null;
    }

    private static LocationAPI findLocation(String id) {
        if (id == null) return null;
        for (StarSystemAPI sys : Global.getSector().getStarSystems()) {
            if (sys.getId().equals(id)) return sys;
        }
        if (Global.getSector().getHyperspace().getId().equals(id)) {
            return Global.getSector().getHyperspace();
        }
        return null;
    }

    private static void clearMemory() {
        Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_IN_PIT);
        Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_RETURN_LOC);
        Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_RETURN_X);
        Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_RETURN_Y);
        Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_PIT_SYSTEM);
        Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_RETURN_ANCHOR);
    }
}
