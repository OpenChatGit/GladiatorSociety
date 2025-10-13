package src.data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers combat-capable factions at runtime, including mod factions.
 * Results are cached in persistent data and refreshed only when requested.
 */
public class GladiatorSociety_FactionDiscovery {
    private static final String PERSIST_KEY = "gs_faction_discovery_cache";

    public static class Result {
        public final Set<String> combatFactions = new HashSet<>();
        public final Set<String> seenFactionIds = new HashSet<>();
        public long builtAt = System.currentTimeMillis();
    }

    @SuppressWarnings("unchecked")
    private static Result getCached() {
        Object obj = Global.getSector().getPersistentData().get(PERSIST_KEY);
        if (obj instanceof Result) return (Result) obj;
        Result res = new Result();
        Global.getSector().getPersistentData().put(PERSIST_KEY, res);
        return res;
    }

    public static void refresh() {
        Result res = new Result();
        // Load config
        GladiatorSociety_FactionDiscoveryConfig cfg = GladiatorSociety_FactionDiscoveryConfig.load();

        // Build candidate list
        List<FactionAPI> all = Global.getSector().getAllFactions();
        for (FactionAPI f : all) {
            String id = f.getId();
            res.seenFactionIds.add(id);
            if (cfg.blacklist.contains(id)) continue;
            if (!cfg.includeHidden) {
                try {
                    // Some APIs may not expose hidden; skip check to remain compatible
                } catch (Throwable ignored) {}
            }
            if (id.equals("player") || id.equals("neutral")) continue;

            // Whitelist goes in directly
            if (cfg.whitelist.contains(id)) {
                res.combatFactions.add(id);
                continue;
            }

            // Initial test: be permissive, include remaining factions (we can tighten later)
            res.combatFactions.add(id);
        }

        // Stricter validation (optional): ensure faction can actually produce a minimal combat fleet
        if (cfg.validateUsingFleetGen) {
            java.util.Set<String> toRemove = new java.util.HashSet<>();
            for (String id : res.combatFactions) {
                if (!canGenerateFleet(id, cfg.minFleetPointsForValidity, cfg.validationFleetType)) {
                    toRemove.add(id);
                }
            }
            if (!toRemove.isEmpty()) {
                res.combatFactions.removeAll(toRemove);
            }
        }

        Global.getSector().getPersistentData().put(PERSIST_KEY, res);

        if (cfg.debug) {
            Global.getLogger(GladiatorSociety_FactionDiscovery.class).info(
                "GS FactionDiscovery found: " + res.combatFactions.size() + " factions -> " + res.combatFactions);
        }
    }

    public static Set<String> getCombatFactions() {
        Result cached = getCached();
        // Auto-refresh if nothing cached or the set of available faction IDs has changed
        boolean needRefresh = cached.combatFactions.isEmpty();
        try {
            List<FactionAPI> all = Global.getSector().getAllFactions();
            if (!needRefresh) {
                if (all.size() != cached.seenFactionIds.size()) {
                    needRefresh = true;
                } else {
                    for (FactionAPI f : all) {
                        if (!cached.seenFactionIds.contains(f.getId())) { needRefresh = true; break; }
                    }
                }
            }
        } catch (Throwable ignored) {}
        if (needRefresh) {
            refresh();
        }
        return new HashSet<>(cached.combatFactions);
    }

    // Try to create a small test fleet to validate a faction
    private static boolean canGenerateFleet(String factionId, int minCombatPts, String validationFleetType) {
        try {
            FleetParamsV3 params = new FleetParamsV3(
                    null,
                    null,
                    factionId,
                    null,
                    (validationFleetType == null || validationFleetType.isEmpty()) ? FleetTypes.PATROL_SMALL : validationFleetType,
                    Math.max( (float) Math.ceil(minCombatPts * 0.6f), 5f),
                    0f, 0f, 0f, 0f, 0f,
                    1f
            );
            params.ignoreMarketFleetSizeMult = true;
            CampaignFleetAPI fleet = GladiatorSociety_TinyFleetFactoryV2.createFleet(params);
            if (fleet == null || fleet.isEmpty()) return false;
            // sanity: ensure at least one non-fighter combat ship present
            for (com.fs.starfarer.api.fleet.FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                if (!m.isFighterWing()) return true;
            }
            return false;
        } catch (Throwable t) {
            if (Global.getSettings().isDevMode()) {
                Global.getLogger(GladiatorSociety_FactionDiscovery.class).warn("Faction validation failed for: " + factionId, t);
            }
            return false;
        }
    }
}
