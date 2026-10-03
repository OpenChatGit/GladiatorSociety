package src.data.scripts.campaign;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.loading.WeaponGroupType;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Builds the JSON-backed custom variants used by bounty fleets. */
final class GladiatorSociety_CustomVariantFactory {

    private static final String VARIANT_PATH = "data/config/gsounty/gladiator_variants/";
    private static final Logger LOG = Global.getLogger(GladiatorSociety_CustomVariantFactory.class);

    private GladiatorSociety_CustomVariantFactory() {
    }

    static ShipVariantAPI create(String variantId) {
        return create(variantId, new HashSet<String>());
    }

    private static ShipVariantAPI create(String variantId, Set<String> building) {
        if (variantId == null || variantId.trim().isEmpty()) {
            LOG.warn("Cannot create custom variant: variant ID is empty");
            return null;
        }
        if (!building.add(variantId)) {
            LOG.warn("Cannot create custom variant " + variantId + ": circular module reference detected");
            return null;
        }

        try {
            JSONObject json = Global.getSettings().loadJSON(VARIANT_PATH + variantId + ".variant");
            if (json == null) {
                LOG.warn("Custom variant file not found: " + variantId);
                return null;
            }

            String hullId = json.optString("hullId", "");
            if (hullId.trim().isEmpty() || Global.getSettings().getHullSpec(hullId) == null) {
                LOG.warn("Custom variant " + variantId + " has a missing or unknown hullId: " + hullId);
                return null;
            }

            ShipVariantAPI variant = Global.getSettings().createEmptyVariant(
                    hullId, Global.getSettings().getHullSpec(hullId));
            if (variant == null) {
                LOG.warn("Could not create an empty variant for " + variantId + " (hull " + hullId + ")");
                return null;
            }

            variant.setVariantDisplayName(json.optString("displayName", "Empty"));
            variant.setNumFluxCapacitors(json.optInt("fluxCapacitors", 0));
            variant.setNumFluxVents(json.optInt("fluxVents", 0));
            addMods(variant, json.optJSONArray("hullMods"), false);
            addMods(variant, json.optJSONArray("permaMods"), true);

            for (WeaponGroupSpec group : variant.getWeaponGroups()) {
                for (String slot : group.clone().getSlots()) {
                    group.removeSlot(slot);
                }
            }

            JSONArray weaponGroups = json.optJSONArray("weaponGroups");
            if (weaponGroups != null) {
                int existingGroups = variant.getWeaponGroups().size();
                for (int i = 0; i < weaponGroups.length(); i++) {
                    JSONObject groupJson = weaponGroups.optJSONObject(i);
                    if (groupJson == null) {
                        LOG.warn("Skipping malformed weapon group " + i + " in custom variant " + variantId);
                        continue;
                    }
                    WeaponGroupSpec group = i < existingGroups
                            ? variant.getWeaponGroups().get(i) : new WeaponGroupSpec();
                    group.setType(groupJson.optString("mode", "ALTERNATING").startsWith("A")
                            ? WeaponGroupType.ALTERNATING : WeaponGroupType.LINKED);
                    group.setAutofireOnByDefault(groupJson.optBoolean("autofire", false));

                    JSONObject weapons = groupJson.optJSONObject("weapons");
                    if (weapons != null) {
                        Iterator<?> slots = weapons.keys();
                        while (slots.hasNext()) {
                            String slot = (String) slots.next();
                            group.addSlot(slot);
                            variant.addWeapon(slot, weapons.optString(slot));
                        }
                    }
                    if (i >= existingGroups) {
                        variant.addWeaponGroup(group);
                    }
                }
            }

            JSONArray wings = json.optJSONArray("wings");
            if (wings != null) {
                for (int i = 0; i < wings.length(); i++) {
                    variant.setWingId(i, wings.getString(i));
                }
            }

            JSONArray modules = json.optJSONArray("modules");
            if (modules != null) {
                for (int i = 0; i < modules.length(); i++) {
                    JSONObject module = modules.optJSONObject(i);
                    if (module == null || !module.keys().hasNext()) {
                        LOG.warn("Skipping malformed module " + i + " in custom variant " + variantId);
                        return null;
                    }
                    String slot = (String) module.keys().next();
                    String moduleVariantId = module.optString(slot, "");
                    if (moduleVariantId.isEmpty()) {
                        LOG.warn("Module " + slot + " has no variant ID in custom variant " + variantId);
                        return null;
                    }
                    ShipVariantAPI moduleVariant = Global.getSettings().getVariant(moduleVariantId);
                    if (moduleVariant == null) {
                        moduleVariant = create(moduleVariantId, building);
                    }
                    if (moduleVariant == null) {
                        LOG.warn("Could not create module variant " + moduleVariantId + " for " + variantId);
                        return null;
                    }
                    variant.setModuleVariant(slot, moduleVariant);
                }
            }
            return variant;
        } catch (IOException | JSONException | RuntimeException ex) {
            LOG.warn("Failed to create custom variant " + variantId, ex);
            return null;
        } finally {
            building.remove(variantId);
        }
    }

    private static void addMods(ShipVariantAPI variant, JSONArray mods, boolean permanent) throws JSONException {
        if (mods == null) {
            return;
        }
        for (int i = 0; i < mods.length(); i++) {
            String modId = mods.getString(i);
            if (permanent) {
                variant.addPermaMod(modId);
            } else {
                variant.addMod(modId);
            }
        }
    }
}
