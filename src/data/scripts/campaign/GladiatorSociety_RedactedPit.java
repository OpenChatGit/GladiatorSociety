package src.data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator.StarSystemType;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.GateCMD;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import src.data.utils.GladiatorSociety_Constants;

import java.util.HashSet;
import java.util.Random;

/**
 * [REDACTED] Pit - Hardcore mode with Abyssal ships (RAT) piloted by RAT AI Cores.
 * Only available when Random Assortment of Things is installed.
 */
public class GladiatorSociety_RedactedPit {

    public static final Logger LOG = Global.getLogger(GladiatorSociety_RedactedPit.class);

    public static final String MEMORY_IN_PIT        = "$GS_inRedactedPit";
    public static final String MEMORY_RETURN_LOC    = "$GS_redactedReturnLoc";
    public static final String MEMORY_RETURN_X      = "$GS_redactedReturnX";
    public static final String MEMORY_RETURN_Y      = "$GS_redactedReturnY";
    public static final String MEMORY_PIT_SYSTEM    = "$GS_redactedPitSystem";
    public static final String MEMORY_ENTRY_ENTITY  = "$GS_redactedEntryEntity";
    public static final String MEMORY_RETURN_ANCHOR = "$GS_redactedReturnAnchorId";

    public static final String ENTRY_ENTITY_TYPE = "gs_redacted_pit_entry";
    public static final String EXIT_ENTITY_TYPE  = "gs_redacted_pit_exit";

    // RAT faction ID for Abyssals
    private static final String RAT_FACTION = "rat_abyssals";

    // RAT AI Core IDs (commodity IDs used as officer AI cores)
    private static final String[] RAT_CORES = {
        "rat_chronos_core",
        "rat_cosmos_core",
        "rat_seraph_core",
    };

    // Possible star types for the [REDACTED] system - dark and oppressive
    private static final String[] REDACTED_STAR_TYPES = {
        StarTypes.NEUTRON_STAR,
        StarTypes.BLACK_HOLE,
        StarTypes.WHITE_DWARF,
    };

    /** Returns true if RAT is installed (checks mod ID). */
    public static boolean isRATInstalled() {
        try {
            return Global.getSettings().getModManager().isModEnabled("assortment_of_things");
        } catch (Throwable t) {
            return false;
        }
    }

    // ── Spawn entry gate ──────────────────────────────────────────────────────

    public static void spawnEntryGate(SectorEntityToken anchor) {
        removeEntryGate();
        LocationAPI loc = anchor.getContainingLocation();

        SectorEntityToken orbitCenter = Global.getSector().getEntityById("gs_gladius_prime");
        if (orbitCenter == null) orbitCenter = anchor;

        float orbitRadius = 700f;
        float stationAngle = 0f;
        if (orbitCenter != anchor) {
            float dx = anchor.getLocation().x - orbitCenter.getLocation().x;
            float dy = anchor.getLocation().y - orbitCenter.getLocation().y;
            stationAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
        }
        float gateAngle = stationAngle + 90f; // offset from salvage pit gate

        SectorEntityToken gate = loc.addCustomEntity(
                "gs_redacted_pit_entry_" + System.currentTimeMillis(),
                "[REDACTED] Gate",
                ENTRY_ENTITY_TYPE,
                Factions.NEUTRAL
        );
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

        LOG.info("GS Redacted Pit: Entry gate spawned");
    }

    public static void removeEntryGate() {
        SectorEntityToken existing = (SectorEntityToken)
                Global.getSector().getMemoryWithoutUpdate().get(MEMORY_ENTRY_ENTITY);
        if (existing != null && existing.getContainingLocation() != null) {
            existing.getContainingLocation().removeEntity(existing);
        }
        Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_ENTRY_ENTITY);
    }

    // ── Enter ─────────────────────────────────────────────────────────────────

    public static void enterRedactedPit(InteractionDialogAPI dialog) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        final SectorEntityToken entryGate = (SectorEntityToken)
                Global.getSector().getMemoryWithoutUpdate().get(MEMORY_ENTRY_ENTITY);

        StarSystemAPI pitSystem = generateRedactedSystem();
        if (pitSystem == null) {
            LOG.error("GS Redacted Pit: Failed to generate system!");
            return;
        }

        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_PIT_SYSTEM, pitSystem.getId());
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_IN_PIT, true);

        if (dialog != null) dialog.dismiss();

        SectorEntityToken exitGate = pitSystem.getEntityById(pitSystem.getId() + "_exit");
        JumpPointAPI.JumpDestination dest = new JumpPointAPI.JumpDestination(
                exitGate != null ? exitGate : pitSystem.getStar(), null);
        dest.setMinDistFromToken(600f);
        dest.setMaxDistFromToken(1200f);
        Global.getSector().doHyperspaceTransition(player,
                entryGate != null ? entryGate : player, dest);

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

        LOG.info("GS Redacted Pit: Player entered " + pitSystem.getId());
    }

    // ── Exit ──────────────────────────────────────────────────────────────────

    public static void exitRedactedPit(InteractionDialogAPI dialog) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        String pitSystemId = (String) Global.getSector().getMemoryWithoutUpdate().get(MEMORY_PIT_SYSTEM);
        SectorEntityToken exitGate = null;
        final StarSystemAPI pitSystem = findSystem(pitSystemId);
        if (pitSystem != null) {
            exitGate = pitSystem.getEntityById(pitSystemId + "_exit");
        }

        String returnLocId  = (String) Global.getSector().getMemoryWithoutUpdate().get(MEMORY_RETURN_LOC);
        String returnAnchorId = (String) Global.getSector().getMemoryWithoutUpdate().get(MEMORY_RETURN_ANCHOR);
        float returnX = Global.getSector().getMemoryWithoutUpdate().getFloat(MEMORY_RETURN_X);
        float returnY = Global.getSector().getMemoryWithoutUpdate().getFloat(MEMORY_RETURN_Y);

        LocationAPI returnLoc = findLocation(returnLocId);
        if (returnLoc == null) { returnLoc = Global.getSector().getHyperspace(); returnX = 0f; returnY = 0f; }

        SectorEntityToken returnAnchor = returnAnchorId != null ? returnLoc.getEntityById(returnAnchorId) : null;

        if (returnAnchor != null) {
            if (dialog != null) dialog.dismiss();
            JumpPointAPI.JumpDestination dest = new JumpPointAPI.JumpDestination(returnAnchor, null);
            dest.setMinDistFromToken(500f);
            dest.setMaxDistFromToken(900f);
            Global.getSector().doHyperspaceTransition(player,
                    exitGate != null ? exitGate : player, dest);
        } else {
            if (dialog != null) dialog.dismiss();
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
                    if (t > 3f) {
                        try { Global.getSector().removeStarSystemNextFrame(pitSystem); }
                        catch (Throwable e) { LOG.warn("GS: Could not remove redacted system", e); }
                    }
                }
            });
        }

        clearMemory();
        LOG.info("GS Redacted Pit: Player exited");
    }

    // ── System generation ─────────────────────────────────────────────────────

    // RAT Abyss entity type IDs
    private static final String[] RAT_ABYSS_ENTITIES = {
        "rat_abyss_research",
        "rat_abyss_fabrication",
        "rat_abyss_accumalator",
        "rat_abyss_drone",
        "rat_abyss_transmitter",
        "rat_abyss_sensor",
        "rat_abyss_beacon",
    };

    private static final String[] RAT_ABYSS_BACKGROUNDS = {
        "graphics/backgrounds/abyss/Abyss1.jpg",
        "graphics/backgrounds/abyss/Abyss2.jpg",
    };

    private static StarSystemAPI generateRedactedSystem() {
        try {
            Random rand = new Random();
            String systemId = "gs_redacted_pit_" + System.currentTimeMillis();

            StarSystemAPI system = Global.getSector().createStarSystem(systemId);
            system.setEnteredByPlayer(false);
            system.setName("[REDACTED]");
            // Use RAT's own abyss backgrounds
            system.setBackgroundTextureFilename(RAT_ABYSS_BACKGROUNDS[rand.nextInt(RAT_ABYSS_BACKGROUNDS.length)]);

            // Add red hyperspace nebula to mimic the abyssal atmosphere
            addAbyssNebula(system);

            // Star - always dark/exotic
            String starType = REDACTED_STAR_TYPES[rand.nextInt(REDACTED_STAR_TYPES.length)];
            PlanetAPI primary = system.initStar(systemId + "_star", starType, 80f, 300f, 5f, 1f, 3f);

            float roll = rand.nextFloat();
            if (roll < 0.15f) {
                system.setType(StarSystemType.TRINARY_2FAR);
                String s2 = REDACTED_STAR_TYPES[rand.nextInt(REDACTED_STAR_TYPES.length)];
                PlanetAPI sec = system.addPlanet(systemId + "_star2", system.getCenter(), "Secondary", s2,
                        rand.nextFloat() * 360f, 60f, 12000f + rand.nextFloat() * 3000f, 1000f);
                system.addCorona(sec, 200f, 5f, 0f, 2f);
                system.setSecondary(sec);
                String s3 = REDACTED_STAR_TYPES[rand.nextInt(REDACTED_STAR_TYPES.length)];
                PlanetAPI ter = system.addPlanet(systemId + "_star3", system.getCenter(), "Tertiary", s3,
                        rand.nextFloat() * 360f, 50f, 18000f + rand.nextFloat() * 3000f, 1500f);
                system.addCorona(ter, 150f, 4f, 0f, 2f);
                system.setTertiary(ter);
            } else if (roll < 0.45f) {
                system.setType(StarSystemType.BINARY_FAR);
                String s2 = REDACTED_STAR_TYPES[rand.nextInt(REDACTED_STAR_TYPES.length)];
                PlanetAPI sec = system.addPlanet(systemId + "_star2", system.getCenter(), "Secondary", s2,
                        rand.nextFloat() * 360f, 60f, 12000f + rand.nextFloat() * 3000f, 1000f);
                system.addCorona(sec, 200f, 5f, 0f, 2f);
                system.setSecondary(sec);
            } else {
                system.setType(StarSystemType.SINGLE);
            }

            // Barren planets first so theme generators have proper locations
            int planetCount = 2 + rand.nextInt(3);
            float[] orbitRadii = { 2000f, 3500f, 5500f, 7500f };
            for (int i = 0; i < planetCount && i < orbitRadii.length; i++) {
                try {
                    system.addPlanet(systemId + "_planet_" + i, primary,
                            "Planet " + (i + 1), Planets.BARREN,
                            rand.nextFloat() * 360f, 60f + rand.nextFloat() * 40f,
                            orbitRadii[i], 200f + orbitRadii[i] / 20f);
                } catch (Throwable t) { /* skip */ }
            }

            // Debris rings
            addDebrisRing(system, primary, 1500f, rand);
            addDebrisRing(system, primary, 2800f, rand);
            addDebrisRing(system, primary, 4500f, rand);
            addDebrisRing(system, primary, 6500f, rand);

            system.updateAllOrbits();

            // RAT Abyss entities - the core content of this system
            spawnAbyssEntities(system, primary, rand);

            // Abyssal derelict ships
            spawnAbyssalDerelicts(system, rand);

            // Abyssal fleets
            spawnAbyssalFleets(system, rand);

            // Patch any null-named entities - vanilla salvage code calls getName().startsWith() and will NPE
            // Also pre-scan all salvageable entities so loot is immediately available
            for (SectorEntityToken entity : system.getAllEntities()) {
                if (entity.getName() == null) {
                    entity.setName("Unknown");
                }
                entity.getMemoryWithoutUpdate().unset(com.fs.starfarer.api.util.Misc.UNSURVEYED);
            }

            // Exit gate outside all debris rings
            SectorEntityToken exitGate = system.addCustomEntity(systemId + "_exit",
                    "[REDACTED] Exit Gate", EXIT_ENTITY_TYPE, Factions.NEUTRAL);
            exitGate.setCircularOrbit(primary, rand.nextFloat() * 360f, 8000f, 600f);
            GateCMD.notifyScanned(exitGate);
            exitGate.getMemoryWithoutUpdate().set(GateEntityPlugin.GATE_SCANNED, true);
            exitGate.getMemoryWithoutUpdate().set(GateEntityPlugin.GATES_ACTIVE, true);
            exitGate.getMemoryWithoutUpdate().set(GateEntityPlugin.PLAYER_CAN_USE_GATES, true);

            LOG.info("GS Redacted Pit: System generated: " + systemId + " type=" + system.getType() + " star=" + starType);
            return system;
        } catch (Throwable t) {
            LOG.error("GS Redacted Pit: System generation failed", t);
            return null;
        }
    }

    /**
     * Spawns RAT Abyss-specific entities at random orbital positions around the primary star.
     * Mirrors what AbyssalWastes biome places: research, fabricators, drones, transmitters, sensors, accumulators.
     */
    private static void spawnAbyssEntities(StarSystemAPI system, PlanetAPI primary, Random rand) {
        // Always: 1 research station, 1-2 fabricators
        spawnAbyssEntity(system, primary, "rat_abyss_research", rand);
        spawnAbyssEntity(system, primary, "rat_abyss_fabrication", rand);
        if (rand.nextFloat() < 0.6f) spawnAbyssEntity(system, primary, "rat_abyss_fabrication", rand);

        // Usually: accumulator (high-value loot)
        if (rand.nextFloat() < 0.7f) spawnAbyssEntity(system, primary, "rat_abyss_accumalator", rand);

        // Sometimes: sensor array, beacon
        if (rand.nextFloat() < 0.6f) spawnAbyssEntity(system, primary, "rat_abyss_sensor", rand);
        if (rand.nextFloat() < 0.5f) spawnAbyssEntity(system, primary, "rat_abyss_beacon", rand);

        // Several drones and transmitters (scattered around)
        int droneCount = 2 + rand.nextInt(3);
        for (int i = 0; i < droneCount; i++) spawnAbyssEntity(system, primary, "rat_abyss_drone", rand);

        int transmitterCount = 1 + rand.nextInt(2);
        for (int i = 0; i < transmitterCount; i++) spawnAbyssEntity(system, primary, "rat_abyss_transmitter", rand);
    }

    private static void spawnAbyssEntity(StarSystemAPI system, PlanetAPI primary, String entityType, Random rand) {
        try {
            String uid = Long.toHexString(rand.nextLong() & 0xFFFFFFFFL);
            SectorEntityToken entity = system.addCustomEntity(
                    entityType + "_" + uid, null, entityType, Factions.NEUTRAL);
            // Spread entities across the inner-to-mid system (1200-7000u)
            float orbitRadius = 1200f + rand.nextFloat() * 5800f;
            float orbitDays = 200f + orbitRadius / 15f + rand.nextFloat() * 100f;
            entity.setCircularOrbit(primary, rand.nextFloat() * 360f, orbitRadius, orbitDays);
            // Ensure salvage seed exists for loot generation
            entity.getMemoryWithoutUpdate().set(com.fs.starfarer.api.impl.campaign.ids.MemFlags.SALVAGE_SEED, rand.nextLong());
        } catch (Throwable t) {
            LOG.warn("GS Redacted Pit: Could not spawn entity " + entityType, t);
        }
    }

    private static void spawnAbyssalDerelicts(StarSystemAPI system, Random rand) {
        // 3-6 abyssal derelict ships scattered around the system
        int count = 3 + rand.nextInt(4);
        for (int i = 0; i < count; i++) {
            try {
                // Pick a random abyssal variant for the wreck
                String[] abyssalVariants = {
                    "rat_merrow_Attack", "rat_merrow_Support",
                    "rat_chuul_Attack", "rat_chuul_Strike",
                    "rat_makara_Attack", "rat_makara_Strike",
                    "rat_aboleth_Attack", "rat_morkoth_Attack",
                };
                String variant = abyssalVariants[rand.nextInt(abyssalVariants.length)];
                if (!Global.getSettings().doesVariantExist(variant)) continue;

                com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData params =
                        com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.createVariant(
                                variant, rand, com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.getDefaultSModProb());
                SectorEntityToken wreck = com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.addSalvageEntity(
                        rand, system, com.fs.starfarer.api.impl.campaign.ids.Entities.WRECK, Factions.NEUTRAL, params);
                float orbitRadius = 1500f + rand.nextFloat() * 5000f;
                wreck.setCircularOrbit(system.getStar(), rand.nextFloat() * 360f, orbitRadius, 300f + rand.nextFloat() * 200f);
            } catch (Throwable t) {
                LOG.warn("GS Redacted Pit: Could not spawn abyssal derelict " + i, t);
            }
        }
    }

    // ── Abyssal fleet spawning ────────────────────────────────────────────────

    private static void spawnAbyssalFleets(StarSystemAPI system, Random rand) {
        int playerFP = Global.getSector().getPlayerFleet().getFleetPoints();
        float basePts = Math.max(60f, Math.min(playerFP * 0.7f, 300f));
        int fleetCount = 3 + Math.min(2, Math.max(0, (playerFP - 100) / 100));
        float minDist = 1800f;

        for (int i = 0; i < fleetCount; i++) {
            try {
                float combatPts = basePts * (0.8f + rand.nextFloat() * 0.4f);
                CampaignFleetAPI fleet = buildAbyssalFleet(combatPts, rand);
                if (fleet == null || fleet.isEmpty()) continue;

                float angle = rand.nextFloat() * 360f;
                float dist = minDist + rand.nextFloat() * 4000f;
                fleet.setLocation(
                        (float) Math.cos(Math.toRadians(angle)) * dist,
                        (float) Math.sin(Math.toRadians(angle)) * dist);
                system.addEntity(fleet);
            } catch (Throwable t) {
                LOG.warn("GS Redacted Pit: Could not spawn abyssal fleet " + i, t);
            }
        }
        LOG.info("GS Redacted Pit: Spawned " + fleetCount + " Abyssal fleets (~" + (int)basePts + " pts, player FP: " + playerFP + ")");
    }

    private static CampaignFleetAPI buildAbyssalFleet(float combatPts, Random rand) {
        FleetParamsV3 params = new FleetParamsV3(
                null, null,
                RAT_FACTION,
                null,
                FleetTypes.PATROL_MEDIUM,
                combatPts,
                0, 0, 0f, 0f, 0f, 1f
        );
        params.quality = 1.0f;
        params.withOfficers = false; // we assign RAT cores manually

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.inflateIfNeeded();

        // Assign RAT AI cores - mirrors AbyssFleetEquipUtils.addAICores
        assignRATCores(fleet, rand);

        // Abyssal behaviour flags - mirrors AbyssUtils.initAbyssalFleetBehaviour
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.DO_NOT_TRY_TO_AVOID_NEARBY_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.SALVAGE_SEED, rand.nextLong());

        com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager.addRemnantInteractionConfig(fleet);

        fleet.setName("[REDACTED] Fleet");
        fleet.getMemoryWithoutUpdate().set("$GS_isAbyssalFleet", true);

        return fleet;
    }

    /**
     * Assigns RAT AI cores to fleet members, mirroring AbyssFleetEquipUtils.addAICores.
     * - Seraph-tagged ships always get a Seraph core
     * - Larger ships are more likely to get a core
     * - Variant ID determines Chronos vs Cosmos
     */
    private static void assignRATCores(CampaignFleetAPI fleet, Random rand) {
        // ~60% of ships get a core, weighted by hull size
        float coreChance = 0.6f;

        WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<>(rand);
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            // Seraph ships always get Seraph core
            if (member.getVariant().hasTag("rat_seraph")) {
                assignCore(member, "rat_seraph_core", fleet.getFaction().getId(), rand);
                continue;
            }
            float weight = 0f;
            switch (member.getHullSpec().getHullSize()) {
                case FRIGATE:       weight = 1f;  break;
                case DESTROYER:     weight = 3f;  break;
                case CRUISER:       weight = 6f;  break;
                case CAPITAL_SHIP:  weight = 15f; break;
                default: break;
            }
            if (weight > 0f) picker.add(member, weight);
        }

        int count = (int)(fleet.getFleetData().getMembersListCopy().size() * coreChance);
        for (int i = 0; i < count; i++) {
            FleetMemberAPI member = picker.pickAndRemove();
            if (member == null) break;
            String variantId = member.getVariant().getHullVariantId().toLowerCase();
            String coreId;
            if (variantId.contains("temporal") || variantId.contains("chronos")) {
                coreId = "rat_chronos_core";
            } else if (variantId.contains("cosmal") || variantId.contains("cosmos")) {
                coreId = "rat_cosmos_core";
            } else {
                coreId = rand.nextBoolean() ? "rat_chronos_core" : "rat_cosmos_core";
            }
            assignCore(member, coreId, fleet.getFaction().getId(), rand);
        }
    }

    private static void assignCore(FleetMemberAPI member, String coreId, String factionId, Random rand) {
        try {
            com.fs.starfarer.api.campaign.AICoreOfficerPlugin plugin =
                    com.fs.starfarer.api.util.Misc.getAICoreOfficerPlugin(coreId);
            PersonAPI officer = plugin.createPerson(coreId, factionId, rand);
            member.setCaptain(officer);
        } catch (Throwable t) {
            try {
                PersonAPI officer = Global.getFactory().createPerson();
                officer.setAICoreId(coreId);
                officer.setFaction(factionId);
                member.setCaptain(officer);
            } catch (Throwable t2) {
                LOG.warn("GS Redacted Pit: Could not assign core " + coreId, t2);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void addAbyssNebula(StarSystemAPI system) {
        try {
            // Set red light color to give the system an abyssal feel
            system.setLightColor(new java.awt.Color(180, 20, 20));
        } catch (Throwable t) {
            LOG.warn("GS Redacted Pit: Could not set abyss light color", t);
        }
    }

    private static void addDebrisRing(StarSystemAPI system, SectorEntityToken focus, float orbitRadius, Random rand) {
        try {
            DebrisFieldTerrainPlugin.DebrisFieldParams params =
                    new DebrisFieldTerrainPlugin.DebrisFieldParams(
                            orbitRadius * 0.3f, 1.5f, 200f, 700f);
            params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.MIXED;
            SectorEntityToken debris = system.addTerrain(Terrain.DEBRIS_FIELD, params);
            debris.setName("Debris Field");
            debris.setCircularOrbit(focus,
                    rand.nextFloat() * 360f, orbitRadius, 300f + rand.nextFloat() * 200f);
        } catch (Throwable t) {
            LOG.warn("GS Redacted Pit: Could not add debris ring at " + orbitRadius, t);
        }

        // Salvageable debris field entities scattered around the ring (1-3 per ring)
        int count = 1 + rand.nextInt(3);
        for (int i = 0; i < count; i++) {
            try {
                SectorEntityToken salvageDebris = com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.addSalvageEntity(
                        rand, system,
                        com.fs.starfarer.api.impl.campaign.ids.Entities.DEBRIS_FIELD_SHARED,
                        Factions.NEUTRAL);
                if (salvageDebris != null) {
                    float angle = rand.nextFloat() * 360f;
                    float dist = orbitRadius * (0.8f + rand.nextFloat() * 0.4f);
                    salvageDebris.setCircularOrbit(focus, angle, dist, 300f + rand.nextFloat() * 200f);
                }
            } catch (Throwable t) { LOG.warn("GS Redacted Pit: Could not add salvage debris at " + orbitRadius, t); }
        }
    }

    public static boolean isInRedactedPit() {
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
