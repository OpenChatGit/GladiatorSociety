package src.data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator.StarSystemType;
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

    public static final String MEMORY_IN_PIT        = "$GS_inSalvagePit";
    public static final String MEMORY_RETURN_LOC    = "$GS_salvagePitReturnLoc";
    public static final String MEMORY_RETURN_X      = "$GS_salvagePitReturnX";
    public static final String MEMORY_RETURN_Y      = "$GS_salvagePitReturnY";
    public static final String MEMORY_PIT_SYSTEM    = "$GS_salvagePitSystem";
    public static final String MEMORY_ENTRY_ENTITY  = "$GS_salvagePitEntryEntity";
    public static final String MEMORY_RETURN_ANCHOR = "$GS_salvagePitReturnAnchorId";

    public static final String ENTRY_ENTITY_TYPE = "gs_salvage_pit_entry";
    public static final String EXIT_ENTITY_TYPE  = "gs_salvage_pit_exit";

    // Star type pools
    private static final String[] SINGLE_STARS = {
        StarTypes.BROWN_DWARF, StarTypes.RED_DWARF, StarTypes.RED_GIANT,
        StarTypes.RED_SUPERGIANT, StarTypes.WHITE_DWARF, StarTypes.ORANGE, StarTypes.ORANGE_GIANT,
        StarTypes.NEUTRON_STAR, StarTypes.BLACK_HOLE,
    };
    private static final String[] COMPANION_STARS = {
        StarTypes.BROWN_DWARF, StarTypes.RED_DWARF, StarTypes.WHITE_DWARF,
        StarTypes.ORANGE, StarTypes.NEUTRON_STAR, StarTypes.BLACK_HOLE,
    };

    // ── Spawn entry gate ──────────────────────────────────────────────────────

    public static void spawnEntryGate(SectorEntityToken anchor) {
        removeEntryGate();
        LocationAPI loc = anchor.getContainingLocation();

        SectorEntityToken orbitCenter = Global.getSector().getEntityById("gs_gladius_prime");
        if (orbitCenter == null) orbitCenter = anchor;

        float orbitRadius = 550f;
        float stationAngle = 0f;
        if (orbitCenter != anchor) {
            float dx = anchor.getLocation().x - orbitCenter.getLocation().x;
            float dy = anchor.getLocation().y - orbitCenter.getLocation().y;
            stationAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
        }
        float gateAngle = stationAngle + 180f;

        SectorEntityToken gate = loc.addCustomEntity(
                "gs_salvage_pit_entry_" + System.currentTimeMillis(),
                "Salvage Pit Gate", ENTRY_ENTITY_TYPE, Factions.NEUTRAL);
        gate.setCircularOrbit(orbitCenter, gateAngle, orbitRadius, 300f);
        GateCMD.notifyScanned(gate);
        gate.getMemoryWithoutUpdate().set(GateEntityPlugin.GATE_SCANNED, true);
        gate.getMemoryWithoutUpdate().set(GateEntityPlugin.GATES_ACTIVE, true);
        gate.getMemoryWithoutUpdate().set(GateEntityPlugin.PLAYER_CAN_USE_GATES, true);

        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_ENTRY_ENTITY, gate);
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_RETURN_LOC, loc.getId());
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_RETURN_X, anchor.getLocation().x);
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_RETURN_Y, anchor.getLocation().y);
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_RETURN_ANCHOR, anchor.getId());
        LOG.info("GS Salvage Pit: Entry gate spawned at angle " + gateAngle);
    }

    public static void removeEntryGate() {
        SectorEntityToken existing = (SectorEntityToken)
                Global.getSector().getMemoryWithoutUpdate().get(MEMORY_ENTRY_ENTITY);
        if (existing != null && existing.getContainingLocation() != null) {
            existing.getContainingLocation().removeEntity(existing);
        }
        Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_ENTRY_ENTITY);
    }

    // ── Enter / Exit ──────────────────────────────────────────────────────────

    public static void enterSalvagePit(InteractionDialogAPI dialog) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        final SectorEntityToken entryGate = (SectorEntityToken)
                Global.getSector().getMemoryWithoutUpdate().get(MEMORY_ENTRY_ENTITY);

        StarSystemAPI pitSystem = generatePitSystem();
        if (pitSystem == null) { LOG.error("GS Salvage Pit: Failed to generate system!"); return; }

        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_PIT_SYSTEM, pitSystem.getId());
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_IN_PIT, true);
        if (dialog != null) dialog.dismiss();

        SectorEntityToken exitGate = pitSystem.getEntityById(pitSystem.getId() + "_exit");
        JumpPointAPI.JumpDestination dest = new JumpPointAPI.JumpDestination(
                exitGate != null ? exitGate : pitSystem.getStar(), null);
        dest.setMinDistFromToken(600f);
        dest.setMaxDistFromToken(1200f);
        Global.getSector().doHyperspaceTransition(player, entryGate != null ? entryGate : player, dest);

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

    public static void exitSalvagePit(InteractionDialogAPI dialog) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        String pitSystemId = (String) Global.getSector().getMemoryWithoutUpdate().get(MEMORY_PIT_SYSTEM);
        SectorEntityToken exitGate = null;
        final StarSystemAPI pitSystem = findSystem(pitSystemId);
        if (pitSystem != null) exitGate = pitSystem.getEntityById(pitSystemId + "_exit");

        String returnLocId   = (String) Global.getSector().getMemoryWithoutUpdate().get(MEMORY_RETURN_LOC);
        String returnAnchorId = (String) Global.getSector().getMemoryWithoutUpdate().get(MEMORY_RETURN_ANCHOR);
        float returnX = Global.getSector().getMemoryWithoutUpdate().getFloat(MEMORY_RETURN_X);
        float returnY = Global.getSector().getMemoryWithoutUpdate().getFloat(MEMORY_RETURN_Y);
        LocationAPI returnLoc = findLocation(returnLocId);
        if (returnLoc == null) { returnLoc = Global.getSector().getHyperspace(); returnX = 0f; returnY = 0f; }
        SectorEntityToken returnAnchor = returnAnchorId != null ? returnLoc.getEntityById(returnAnchorId) : null;

        if (dialog != null) dialog.dismiss();
        if (returnAnchor != null) {
            JumpPointAPI.JumpDestination dest = new JumpPointAPI.JumpDestination(returnAnchor, null);
            dest.setMinDistFromToken(500f); dest.setMaxDistFromToken(900f);
            Global.getSector().doHyperspaceTransition(player, exitGate != null ? exitGate : player, dest);
        } else {
            if (player.getContainingLocation() != null) player.getContainingLocation().removeEntity(player);
            player.setLocation(returnX + 500f, returnY + 500f);
            returnLoc.addEntity(player);
            Global.getSector().setCurrentLocation(returnLoc);
        }

        if (pitSystem != null) {
            Global.getSector().addTransientScript(new com.fs.starfarer.api.EveryFrameScript() {
                private float t = 0f;
                public boolean isDone() { return t > 3f; }
                public boolean runWhilePaused() { return false; }
                public void advance(float amount) {
                    t += amount;
                    if (t > 3f) { try { Global.getSector().removeStarSystemNextFrame(pitSystem); }
                        catch (Throwable e) { LOG.warn("GS: Could not remove pit system", e); } }
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

            // Pick star configuration: 60% single, 30% binary, 10% trinary
            float roll = rand.nextFloat();
            String primaryType = SINGLE_STARS[rand.nextInt(SINGLE_STARS.length)];
            float primaryRadius = 150f + rand.nextFloat() * 100f;
            float primaryCorona = primaryRadius + 100f + rand.nextFloat() * 100f;
            PlanetAPI primary = system.initStar(systemId + "_star", primaryType, primaryRadius, primaryCorona, 3f, 0.5f, 2f);

            if (roll < 0.10f) {
                // Trinary: two far companions
                system.setType(StarSystemType.TRINARY_2FAR);
                String sec2Type = COMPANION_STARS[rand.nextInt(COMPANION_STARS.length)];
                PlanetAPI sec2 = system.addPlanet(systemId + "_star2", system.getCenter(), "Secondary", sec2Type,
                        rand.nextFloat() * 360f, 80f, 12000f + rand.nextFloat() * 4000f, 1000f);
                system.addCorona(sec2, 150f, 3f, 0f, 1f);
                system.setSecondary(sec2);
                String ter2Type = COMPANION_STARS[rand.nextInt(COMPANION_STARS.length)];
                PlanetAPI ter2 = system.addPlanet(systemId + "_star3", system.getCenter(), "Tertiary", ter2Type,
                        rand.nextFloat() * 360f, 70f, 18000f + rand.nextFloat() * 4000f, 1500f);
                system.addCorona(ter2, 120f, 2f, 0f, 1f);
                system.setTertiary(ter2);
            } else if (roll < 0.40f) {
                // Binary: one far companion
                system.setType(StarSystemType.BINARY_FAR);
                String secType = COMPANION_STARS[rand.nextInt(COMPANION_STARS.length)];
                PlanetAPI sec = system.addPlanet(systemId + "_star2", system.getCenter(), "Secondary", secType,
                        rand.nextFloat() * 360f, 80f, 12000f + rand.nextFloat() * 4000f, 1000f);
                system.addCorona(sec, 150f, 3f, 0f, 1f);
                system.setSecondary(sec);
            } else {
                system.setType(StarSystemType.SINGLE);
            }

            // sysData - populate alreadyUsed as we add things
            BaseThemeGenerator.StarSystemData sysData = new BaseThemeGenerator.StarSystemData();
            sysData.system = system;
            sysData.stars.add(primary);
            sysData.alreadyUsed = new HashSet<SectorEntityToken>();
            sysData.alreadyUsed.add(primary);
            if (system.getSecondary() != null) { sysData.stars.add(system.getSecondary()); sysData.alreadyUsed.add(system.getSecondary()); }
            if (system.getTertiary() != null)  { sysData.stars.add(system.getTertiary());  sysData.alreadyUsed.add(system.getTertiary()); }

            // Planets FIRST - theme generators need them for location picking
            addPlanets(system, sysData, rand, primary);

            // Debris rings after planets
            addDebrisRing(system, primary, 1800f, rand);
            addDebrisRing(system, primary, 3200f, rand);
            addDebrisRing(system, primary, 5000f, rand);

            // Force orbit update so location pickers see all placed entities
            system.updateAllOrbits();

            // Theme generators - now with proper sysData
            DerelictThemeGenerator derelictGen = new DerelictThemeGenerator();
            // Inject our rand so salvage seeds are properly generated for caches/derelicts
            try {
                java.lang.reflect.Field randField = com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.class.getDeclaredField("random");
                randField.setAccessible(true);
                randField.set(derelictGen, rand);
            } catch (Throwable e) { LOG.warn("GS Salvage Pit: Could not inject random into DerelictThemeGenerator", e); }
            WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<String>(rand);
            factionPicker.add(Factions.REMNANTS, 3f);
            factionPicker.add(Factions.INDEPENDENT, 1f);

            derelictGen.addDerelictShips(sysData, 1f, 4, 7, factionPicker);
            derelictGen.addShipGraveyard(sysData, 1f, 3, 6, factionPicker);

            WeightedRandomPicker<String> cachePicker = new WeightedRandomPicker<String>(rand);
            cachePicker.add("supply_cache", 3f); cachePicker.add("supply_cache_small", 2f);
            cachePicker.add("weapons_cache", 3f); cachePicker.add("weapons_cache_low", 2f);
            cachePicker.add("weapons_cache_high", 1f); cachePicker.add("weapons_cache_remnant", 2f);
            cachePicker.add("weapons_cache_small", 2f); cachePicker.add("equipment_cache", 2f);
            cachePicker.add("equipment_cache_small", 1f); cachePicker.add("technology_cache", 1f);
            cachePicker.add("large_cache", 0.5f); cachePicker.add("hidden_cache", 0.3f);
            derelictGen.addCaches(sysData, 1f, 2, 4, cachePicker);

            WeightedRandomPicker<String> miningPicker = new WeightedRandomPicker<String>(rand);
            miningPicker.add("station_mining", 3f); miningPicker.add("station_mining_remnant", 2f);
            miningPicker.add("orbital_habitat", 1f); miningPicker.add("orbital_habitat_remnant", 1f);
            miningPicker.add("orbital_junk", 1f);
            derelictGen.addMiningStations(sysData, 1f, 1, 2, miningPicker);

            if (rand.nextFloat() < 0.35f) {
                WeightedRandomPicker<String> resPicker = new WeightedRandomPicker<String>(rand);
                resPicker.add("station_research", 3f); resPicker.add("station_research_remnant", 2f);
                derelictGen.addResearchStations(sysData, 1f, 1, 1, resPicker);
            }
            if (rand.nextFloat() < 0.40f) {
                WeightedRandomPicker<String> habPicker = new WeightedRandomPicker<String>(rand);
                habPicker.add("orbital_habitat", 3f); habPicker.add("orbital_habitat_remnant", 2f);
                habPicker.add("orbital_dockyard", 1f);
                derelictGen.addHabCenters(sysData, 1f, 1, 2, habPicker);
            }
            derelictGen.addObjectives(sysData, 1f);

            RemnantThemeGenerator remnantGen = new RemnantThemeGenerator();
            try {
                java.lang.reflect.Field randField = com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.class.getDeclaredField("random");
                randField.setAccessible(true);
                randField.set(remnantGen, rand);
            } catch (Throwable e) { LOG.warn("GS Salvage Pit: Could not inject random into RemnantThemeGenerator", e); }
            WeightedRandomPicker<String> stationPicker = new WeightedRandomPicker<String>(rand);
            stationPicker.add("remnant_station2_Standard", 2f); stationPicker.add("remnant_station2_Damaged", 1f);
            List<CampaignFleetAPI> stations = remnantGen.addBattlestations(sysData, 1f, 1, 1, stationPicker);

            spawnRemnantFleets(system, rand, stations);

            // Patch any null-named entities - vanilla salvage code calls getName().startsWith() and will NPE
            // Also pre-scan all salvageable entities so loot is immediately available
            for (SectorEntityToken entity : system.getAllEntities()) {
                if (entity.getName() == null) {
                    entity.setName("Unknown");
                }
                // Mark as surveyed/scanned so caches and derelicts have loot immediately
                entity.getMemoryWithoutUpdate().unset(com.fs.starfarer.api.util.Misc.UNSURVEYED);
            }

            // Exit gate outside all debris rings
            SectorEntityToken exitGate = system.addCustomEntity(systemId + "_exit",
                    "Salvage Pit Exit Gate", EXIT_ENTITY_TYPE, Factions.NEUTRAL);
            exitGate.setCircularOrbit(primary, rand.nextFloat() * 360f, 6500f, 700f);
            GateCMD.notifyScanned(exitGate);
            exitGate.getMemoryWithoutUpdate().set(GateEntityPlugin.GATE_SCANNED, true);
            exitGate.getMemoryWithoutUpdate().set(GateEntityPlugin.GATES_ACTIVE, true);
            exitGate.getMemoryWithoutUpdate().set(GateEntityPlugin.PLAYER_CAN_USE_GATES, true);

            LOG.info("GS Salvage Pit: System generated: " + systemId + " type=" + system.getType() + " star=" + primaryType);
            return system;
        } catch (Throwable t) {
            LOG.error("GS Salvage Pit: System generation failed", t);
            return null;
        }
    }

    private static void addPlanets(StarSystemAPI system, BaseThemeGenerator.StarSystemData sysData, Random rand, PlanetAPI star) {
        WeightedRandomPicker<String> pool = new WeightedRandomPicker<String>(rand);
        pool.add(Planets.BARREN, 4f); pool.add(Planets.BARREN2, 3f); pool.add(Planets.BARREN3, 3f);
        pool.add(Planets.BARREN_CASTIRON, 2f); pool.add(Planets.BARREN_BOMBARDED, 2f);
        pool.add(Planets.BARREN_VENUSLIKE, 2f); pool.add(Planets.BARREN_DESERT, 2f);
        pool.add(Planets.ROCKY_METALLIC, 3f); pool.add(Planets.ROCKY_UNSTABLE, 3f);
        pool.add(Planets.ROCKY_ICE, 2f); pool.add(Planets.FROZEN, 3f);
        pool.add(Planets.FROZEN1, 2f); pool.add(Planets.FROZEN2, 2f); pool.add(Planets.FROZEN3, 2f);
        pool.add(Planets.PLANET_LAVA, 2f); pool.add(Planets.PLANET_LAVA_MINOR, 2f);
        pool.add(Planets.IRRADIATED, 2f); pool.add(Planets.CRYOVOLCANIC, 1f);
        pool.add(Planets.DESERT, 1f); pool.add(Planets.DESERT1, 1f);
        pool.add(Planets.ARID, 1f); pool.add(Planets.TUNDRA, 1f);
        pool.add(Planets.PLANET_WATER, 0.3f); pool.add(Planets.PLANET_TERRAN, 0.2f);
        pool.add(Planets.PLANET_TERRAN_ECCENTRIC, 0.2f);

        WeightedRandomPicker<String> gasPool = new WeightedRandomPicker<String>(rand);
        gasPool.add(Planets.GAS_GIANT, 3f); gasPool.add(Planets.ICE_GIANT, 2f);

        int planetCount = 2 + rand.nextInt(3);
        float[] orbitRadii = { 2200f, 3500f, 5500f, 7500f };

        for (int i = 0; i < planetCount && i < orbitRadii.length; i++) {
            try {
                boolean isGas = (i == planetCount - 1) && rand.nextFloat() < 0.4f;
                String type = isGas ? gasPool.pick() : pool.pick();
                float radius = isGas ? (120f + rand.nextFloat() * 80f) : (55f + rand.nextFloat() * 50f);
                float orbitDays = 200f + orbitRadii[i] / 20f + rand.nextFloat() * 100f;
                PlanetAPI planet = system.addPlanet(system.getId() + "_planet_" + i, star,
                        "Planet " + (i + 1), type, rand.nextFloat() * 360f, radius, orbitRadii[i], orbitDays);
                // Generate proper planet conditions so survey loot spawns correctly
                com.fs.starfarer.api.impl.campaign.procgen.PlanetConditionGenerator.generateConditionsForPlanet(
                        planet, com.fs.starfarer.api.impl.campaign.procgen.StarAge.ANY);
                sysData.planets.add(planet);
                sysData.alreadyUsed.add(planet);
            } catch (Throwable t) { LOG.warn("GS Salvage Pit: Could not add planet " + i, t); }
        }
    }

    private static final String[] RING_CACHE_TYPES = {
        Entities.SUPPLY_CACHE, Entities.SUPPLY_CACHE_SMALL,
        Entities.WEAPONS_CACHE, Entities.WEAPONS_CACHE_LOW, Entities.WEAPONS_CACHE_SMALL,
        Entities.EQUIPMENT_CACHE, Entities.EQUIPMENT_CACHE_SMALL,
        Entities.TECHNOLOGY_CACHE,
    };

    private static void addDebrisRing(StarSystemAPI system, SectorEntityToken focus, float orbitRadius, Random rand) {
        try {
            // Visual terrain ring
            DebrisFieldTerrainPlugin.DebrisFieldParams params =
                    new DebrisFieldTerrainPlugin.DebrisFieldParams(orbitRadius * 0.25f, 1f, 150f, 500f);
            params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.MIXED;
            SectorEntityToken debris = system.addTerrain(Terrain.DEBRIS_FIELD, params);
            debris.setName("Debris Field");
            debris.setCircularOrbit(focus, rand.nextFloat() * 360f, orbitRadius, 350f + rand.nextFloat() * 200f);
        } catch (Throwable t) { LOG.warn("GS Salvage Pit: Could not add debris ring terrain at " + orbitRadius, t); }

        // Spawn 2-4 loot caches scattered around the ring
        int count = 2 + rand.nextInt(3);
        for (int i = 0; i < count; i++) {
            try {
                String cacheType = RING_CACHE_TYPES[rand.nextInt(RING_CACHE_TYPES.length)];
                SectorEntityToken cache = BaseThemeGenerator.addSalvageEntity(
                        rand, system, cacheType, Factions.NEUTRAL);
                if (cache != null) {
                    float angle = rand.nextFloat() * 360f;
                    float dist = orbitRadius * (0.75f + rand.nextFloat() * 0.5f);
                    cache.setCircularOrbit(focus, angle, dist, 350f + rand.nextFloat() * 200f);
                }
            } catch (Throwable t) { LOG.warn("GS Salvage Pit: Could not add cache at ring " + orbitRadius, t); }
        }
    }

    private static void spawnRemnantFleets(StarSystemAPI system, Random rand, List<CampaignFleetAPI> stations) {
        int playerFP = Global.getSector().getPlayerFleet().getFleetPoints();
        float basePts = Math.max(40f, Math.min(playerFP * 0.5f, 250f));
        int fleetCount = 3 + Math.min(2, Math.max(0, (playerFP - 100) / 100));
        float minDist = 2000f;

        for (int i = 0; i < fleetCount; i++) {
            try {
                float combatPts = basePts * (0.8f + rand.nextFloat() * 0.4f);
                FleetParamsV3 params = new FleetParamsV3(null, null, Factions.REMNANTS, null,
                        FleetTypes.PATROL_MEDIUM, combatPts, 0, 0, 0f, 0f, 0f, 1f);
                params.quality = 0.5f; params.withOfficers = true;
                CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
                if (fleet == null || fleet.isEmpty()) continue;
                RemnantSeededFleetManager.initRemnantFleetProperties(rand, fleet, false);
                RemnantSeededFleetManager.addRemnantInteractionConfig(fleet);
                float angle = rand.nextFloat() * 360f;
                float dist = minDist + rand.nextFloat() * 3500f;
                fleet.setLocation((float) Math.cos(Math.toRadians(angle)) * dist,
                                  (float) Math.sin(Math.toRadians(angle)) * dist);
                system.addEntity(fleet);
            } catch (Throwable t) { LOG.warn("GS Salvage Pit: Could not spawn fleet " + i, t); }
        }
        LOG.info("GS Salvage Pit: Spawned " + fleetCount + " fleets (~" + (int)basePts + " pts, player FP: " + playerFP + ")");
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
        if (Global.getSector().getHyperspace().getId().equals(id)) return Global.getSector().getHyperspace();
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
