package src.data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class GladiatorSociety_FactionDiscoveryConfig {
    public final Set<String> whitelist = new HashSet<>();
    public final Set<String> blacklist = new HashSet<>();
    public boolean includeHidden = false;
    public int minFleetPointsForValidity = 20;
    public boolean debug = false;
    public boolean validateUsingFleetGen = true;
    public String validationFleetType = "gs_validation";
    public float vanillaModdedSplit = 0.5f;

    public static GladiatorSociety_FactionDiscoveryConfig load() {
        GladiatorSociety_FactionDiscoveryConfig cfg = new GladiatorSociety_FactionDiscoveryConfig();
        try {
            JSONObject json = Global.getSettings().getMergedJSONForMod("data/config/gs_factions.json", "gladiatorsociety");
            if (json.has("whitelist")) fillSet(cfg.whitelist, json.getJSONArray("whitelist"));
            if (json.has("blacklist")) fillSet(cfg.blacklist, json.getJSONArray("blacklist"));
            if (json.has("includeHidden")) cfg.includeHidden = json.getBoolean("includeHidden");
            if (json.has("minFleetPointsForValidity")) cfg.minFleetPointsForValidity = json.getInt("minFleetPointsForValidity");
            if (json.has("debug")) cfg.debug = json.getBoolean("debug");
            if (json.has("validateUsingFleetGen")) cfg.validateUsingFleetGen = json.getBoolean("validateUsingFleetGen");
            if (json.has("validationFleetType")) cfg.validationFleetType = json.getString("validationFleetType");
            if (json.has("vanillaModdedSplit")) cfg.vanillaModdedSplit = (float) json.getDouble("vanillaModdedSplit");
        } catch (Throwable t) {
            Global.getLogger(GladiatorSociety_FactionDiscoveryConfig.class).warn("Failed to load gs_factions.json, using defaults", t);
        }
        return cfg;
    }

    private static void fillSet(Set<String> set, JSONArray arr) {
        for (int i = 0; i < arr.length(); i++) {
            String v = arr.optString(i, null);
            if (v != null && !v.isEmpty()) {
                set.add(v);
            }
        }
    }
}
