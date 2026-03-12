package src.data.scripts.world;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;
import com.thoughtworks.xstream.XStream;
import src.data.utils.GladiatorSociety_Constants;
import src.data.utils.GladiatorSociety_XStreamConfig;

public class GladiatorSociety_ModPlugin extends BaseModPlugin {
    private static final String VERSION_FILE = "gladiatorsociety.version";
    private static final Logger LOG = Global.getLogger(GladiatorSociety_ModPlugin.class);

    @Override
    public void configureXStream(XStream x) {
        GladiatorSociety_XStreamConfig.configureXStream(x);
    }

    @Override
    public void onApplicationLoad() {
        try {
            Class<?> vc = Global.getSettings().getScriptClassLoader().loadClass("org.lazywizard.versionchecker.Version");
            java.lang.reflect.Method method = vc.getMethod("addVersionCheck", String.class, String.class);
            method.invoke(null, Global.getSettings().getModManager().getModSpec("gladiatorsociety").getPath(), VERSION_FILE);
        } catch (Exception ex) {
            // Version checker not installed, ignore
        }
    }

    @Override
    public void onNewGame() {
        // Always run our WorldGen to spawn The Arena system.
        // Economy/markets are loaded separately via economy.json.
        boolean haveNexerelin = Global.getSettings().getModManager().isModEnabled("nexerelin");
        if (haveNexerelin) {
            GladiatorSociety_WorldGen.generateArenaSystem(Global.getSector());
            GladiatorSociety_WorldGen.setFactionProperties(Global.getSector());
            GladiatorSociety_WorldGen.setFactionRelationships(Global.getSector());
        }
        // Non-Nexerelin: sectorGeneratorPlugin in mod_info.json handles it
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        // Economy is now loaded - market exists, we can add submarkets, Varro, and patrol fleet
        GladiatorSociety_WorldGen.postEconomySetup(Global.getSector());

        // Ensure faction is visible in Intel tab
        FactionAPI gs = Global.getSector().getFaction(GladiatorSociety_Constants.GSFACTION_ID);
        if (gs != null) {
            gs.setShowInIntelTab(true);
        }
    }

    @Override
    public PluginPick<ShipAIPlugin> pickShipAI(FleetMemberAPI member, ShipAPI ship) {
        final String PREFIX = "GladiatorSociety_";
        ShipAIConfig config = new ShipAIConfig();
        String personality = "steady";

        boolean hasHullMod = false;
        if (ship.getCaptain() != null && ship.getCaptain().isDefault()) {
            for (String hullMod : ship.getVariant().getHullMods()) {
                if (hullMod.startsWith(PREFIX)) {
                    hasHullMod = true;
                    personality = hullMod.split("_")[1];
                    config.personalityOverride = personality;
                    break;
                }
            }
        }
        if (!hasHullMod) return null;

        LOG.info("Applying personality [" + personality + "] to ship [" + ship.getName() + "]");
        return new PluginPick<>(Global.getSettings().createDefaultShipAI(ship, config), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    }
}
