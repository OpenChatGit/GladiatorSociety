package src.data.scripts.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Planets;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.StarTypes;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import org.apache.log4j.Logger;
import src.data.scripts.campaign.intel.GladiatorSociety_InitQuest;

public class GladiatorSociety_WorldGen implements SectorGeneratorPlugin {

    public static final String SYSTEM_ID   = "gs_arena_system";
    public static final String PLANET_ID   = "gs_gladius_prime";
    public static final String STATION_ID  = "gs_arena_station";
    public static final String MARKET_ID   = "gs_gladius_prime"; // market ID = primary entity ID (set by economy.json)
    public static final String GS_FACTION  = "gladiator";

    private static final Logger LOG = Global.getLogger(GladiatorSociety_WorldGen.class);

    @Override
    public void generate(SectorAPI sector) {
        generateArenaSystem(sector);
        setFactionProperties(sector);
        setFactionRelationships(sector);
    }

    public static void generateArenaSystem(SectorAPI sector) {
        // Prevent double-generation
        if (sector.getStarSystem(SYSTEM_ID) != null) return;

        StarSystemAPI system = sector.createStarSystem(SYSTEM_ID);
        system.setName("The Arena");
        system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
        system.getLocation().set(-14000f, 5000f);

        PlanetAPI star = system.initStar(
                SYSTEM_ID + "_star", StarTypes.YELLOW,
                450f, 400f, 2f, 0.5f, 2f);
        system.setLightColor(new Color(255, 240, 200));

        // ── Gladius Prime - capital planet (terran, size 7) ──────────────────
        PlanetAPI gladiusPrime = system.addPlanet(
                PLANET_ID, star, "Gladius Prime",
                Planets.PLANET_TERRAN, 45f, 180f, 2800f, 200f);
        gladiusPrime.setCustomDescriptionId("gs_gladius_prime");

        // The Arena station orbits Gladius Prime
        SectorEntityToken station = system.addCustomEntity(
                STATION_ID, "The Arena", "station_side06", GS_FACTION);
        station.setCustomDescriptionId("gs_arena_station");
        station.setCircularOrbitPointingDown(gladiusPrime, 0f, 350f, 25f);

        // ── Comm Relay ───────────────────────────────────────────────────────
        SectorEntityToken commRelay = system.addCustomEntity(
                SYSTEM_ID + "_comm_relay", "GS Comm Relay", "comm_relay", GS_FACTION);
        commRelay.setCircularOrbit(star, 135f, 3200f, 220f);

        // ── Nav Buoy ─────────────────────────────────────────────────────────
        SectorEntityToken navBuoy = system.addCustomEntity(
                SYSTEM_ID + "_nav_buoy", "GS Nav Buoy", "nav_buoy", GS_FACTION);
        navBuoy.setCircularOrbit(star, 315f, 3200f, 220f);

        // ── Empty planet 1 with 2 moons ──────────────────────────────────────
        PlanetAPI barren1 = system.addPlanet(
                SYSTEM_ID + "_barren1", star, "Ferrum",
                Planets.BARREN, 120f, 100f, 5500f, 350f);

        system.addPlanet(SYSTEM_ID + "_barren1_moon1", barren1, "Ferrum I",
                Planets.BARREN_CASTIRON, 30f, 50f, 350f, 40f);
        system.addPlanet(SYSTEM_ID + "_barren1_moon2", barren1, "Ferrum II",
                Planets.ROCKY_ICE, 60f, 45f, 500f, 55f);

        // ── Empty planet 2 with 2 moons ──────────────────────────────────────
        PlanetAPI barren2 = system.addPlanet(
                SYSTEM_ID + "_barren2", star, "Scoria",
                Planets.PLANET_LAVA, 200f, 90f, 8500f, 500f);

        system.addPlanet(SYSTEM_ID + "_barren2_moon1", barren2, "Scoria I",
                Planets.BARREN, 45f, 40f, 350f, 40f);
        system.addPlanet(SYSTEM_ID + "_barren2_moon2", barren2, "Scoria II",
                Planets.ROCKY_ICE, 90f, 38f, 520f, 60f);

        // ── Jump point ───────────────────────────────────────────────────────
        JumpPointAPI jp = Global.getFactory().createJumpPoint(
                SYSTEM_ID + "_jump", "The Arena Jump Point");
        OrbitAPI orbit = Global.getFactory().createCircularOrbit(star, 270f, 4000f, 280f);
        jp.setOrbit(orbit);
        jp.setStandardWormholeToHyperspaceVisual();
        system.addEntity(jp);

        system.autogenerateHyperspaceJumpPoints(true, true, false);
        clearNebula(system);

        // Market is defined in data/campaign/econ/gs_arena_system.json
        // System location is registered in data/campaign/starmap.json

        LOG.info("GS WorldGen: Arena system generated at (-14000, 5000)");
    }

    /**
     * Called from ModPlugin.onNewGameAfterEconomyLoad() - adds Varro and patrol fleet.
     * Market/conditions/industries are loaded from data/campaign/econ/gs_arena_system.json
     */
    public static void postEconomySetup(SectorAPI sector) {
        MarketAPI market = sector.getEconomy().getMarket(MARKET_ID);
        if (market == null) {
            SectorEntityToken planet = sector.getEntityById(PLANET_ID);
            if (planet != null) market = planet.getMarket();
        }
        if (market == null) {
            LOG.warn("GS WorldGen: Could not find Gladius Prime market in postEconomySetup");
            return;
        }

        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);

        // Add Commander Varro
        addVarro(sector, market);

        // Spawn system patrol fleet
        StarSystemAPI system = sector.getStarSystem(SYSTEM_ID);
        if (system != null) spawnSystemFleet(system);

        LOG.info("GS WorldGen: Post-economy setup complete");
    }

    private static void addVarro(SectorAPI sector, MarketAPI market) {
        // Check if already added
        if (sector.getImportantPeople().getPerson(GladiatorSociety_InitQuest.PERSON_VARRO) != null) return;

        PersonAPI varro = Global.getFactory().createPerson();
        varro.setId(GladiatorSociety_InitQuest.PERSON_VARRO);
        varro.setImportance(PersonImportance.HIGH);
        varro.setFaction(GS_FACTION);
        varro.setGender(FullName.Gender.MALE);
        varro.setRankId(Ranks.SPACE_COMMANDER);
        varro.setPostId(Ranks.POST_BASE_COMMANDER);
        varro.setVoice(Voices.SOLDIER);
        varro.getName().setFirst("Marcus");
        varro.getName().setLast("Varro");
        varro.setPortraitSprite("graphics/portraits/portrait_hegemony06.png");
        varro.addTag(GladiatorSociety_InitQuest.PERSON_VARRO);

        sector.getImportantPeople().addPerson(varro);
        market.addPerson(varro);
        market.getCommDirectory().addPerson(varro);
        LOG.info("GS WorldGen: Commander Varro added to Gladius Prime");
    }

    private static void spawnSystemFleet(StarSystemAPI system) {
        try {
            FleetParamsV3 params = new FleetParamsV3(
                    null, null,
                    GS_FACTION,
                    null,
                    FleetTypes.PATROL_LARGE,
                    300f,
                    20f,
                    0f, 0f, 0f, 0f, 1f
            );
            params.quality = 0.75f;
            params.withOfficers = true;

            CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
            if (fleet == null || fleet.isEmpty()) return;

            fleet.setName("GS Defense Force");
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, false);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE, true);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, false);
            // Prevent this patrol from joining any battles
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_PLAYER_BATTLE_JOIN_TOFF, true);
            fleet.getMemoryWithoutUpdate().set("$gs_patrol_fleet", true);

            SectorEntityToken planet = Global.getSector().getEntityById(PLANET_ID);
            if (planet != null) {
                fleet.setLocation(planet.getLocation().x + 500f, planet.getLocation().y);
            }
            system.addEntity(fleet);
            fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, system.getCenter(), Float.MAX_VALUE);
            LOG.info("GS WorldGen: System patrol fleet spawned");
        } catch (Throwable t) {
            LOG.error("GS WorldGen: Failed to spawn system fleet", t);
        }
    }

    public static void setFactionProperties(SectorAPI sector) {
        FactionAPI gs = sector.getFaction(GS_FACTION);
        if (gs == null) return;

        gs.setShowInIntelTab(true);

        try {
            SharedData.getData().getPersonBountyEventData().addParticipatingFaction(GS_FACTION);
        } catch (Throwable t) {
            LOG.warn("GS WorldGen: Could not register for person bounties", t);
        }
    }

    private static void clearNebula(StarSystemAPI system) {
        try {
            HyperspaceTerrainPlugin plugin =
                    (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
            NebulaEditor editor = new NebulaEditor(plugin);
            float minRadius = plugin.getTileSize() * 2f;
            float radius = system.getMaxRadiusInHyperspace();
            editor.clearArc(system.getLocation().x, system.getLocation().y,
                    0, radius + minRadius * 0.5f, 0, 360f);
            editor.clearArc(system.getLocation().x, system.getLocation().y,
                    0, radius + minRadius, 0, 360f, 0.25f);
        } catch (Throwable t) {
            LOG.warn("GS WorldGen: Could not clear nebula", t);
        }
    }

    public static void setFactionRelationships(SectorAPI sector) {
        FactionAPI gs = sector.getFaction(GS_FACTION);
        if (gs == null) return;

        gs.setRelationship(Factions.PLAYER,        RepLevel.NEUTRAL);
        gs.setRelationship(Factions.HEGEMONY,      RepLevel.FAVORABLE);
        gs.setRelationship(Factions.LIONS_GUARD,   RepLevel.FRIENDLY);
        gs.setRelationship(Factions.DIKTAT,        RepLevel.NEUTRAL);
        gs.setRelationship(Factions.PERSEAN,       RepLevel.SUSPICIOUS);
        gs.setRelationship(Factions.TRITACHYON,    RepLevel.SUSPICIOUS);
        gs.setRelationship(Factions.INDEPENDENT,   RepLevel.NEUTRAL);
        gs.setRelationship(Factions.LUDDIC_CHURCH, RepLevel.SUSPICIOUS);
        gs.setRelationship(Factions.PIRATES,       RepLevel.INHOSPITABLE);
        gs.setRelationship(Factions.LUDDIC_PATH,   RepLevel.HOSTILE);
        gs.setRelationship(Factions.REMNANTS,      RepLevel.HOSTILE);
    }
}
