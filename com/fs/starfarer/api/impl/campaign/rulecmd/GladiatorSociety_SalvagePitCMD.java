package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;
import src.data.scripts.campaign.GladiatorSociety_SalvagePit;

/**
 * Handles dialog commands for the Salvage Pit entry and exit portals.
 *
 * Commands:
 *   GladiatorSociety_SalvagePitCMD spawnPortal   - spawns entry portal next to player
 *   GladiatorSociety_SalvagePitCMD enter          - teleports player into the pit
 *   GladiatorSociety_SalvagePitCMD exit           - teleports player back out
 *   GladiatorSociety_SalvagePitCMD displayEntry   - shows entry portal dialog options
 *   GladiatorSociety_SalvagePitCMD displayExit    - shows exit portal dialog options
 */
public class GladiatorSociety_SalvagePitCMD extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        String arg = params.get(0).getString(memoryMap);

        switch (arg) {
            case "spawnPortal":
                GladiatorSociety_SalvagePit.spawnEntryGate(dialog.getInteractionTarget());
                dialog.getTextPanel().addParagraph(
                        "A Gate has appeared nearby. Fly to it to enter the Salvage Pit.",
                        java.awt.Color.GREEN);
                return true;

            case "enter":
                GladiatorSociety_SalvagePit.enterSalvagePit(dialog);
                return true;

            case "exit":
                GladiatorSociety_SalvagePit.exitSalvagePit(dialog);
                return true;

            case "displayEntry":
                displayEntryDialog(dialog);
                return true;

            case "displayExit":
                displayExitDialog(dialog);
                return true;
        }
        return false;
    }

    private void displayEntryDialog(InteractionDialogAPI dialog) {
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        dialog.getTextPanel().addParagraph(
                "The Gladiator Society has activated a Gate to the Salvage Pit - " +
                "a lawless debris field teeming with scavengers and abandoned cargo. " +
                "Everything you find in there is yours to keep.");
        dialog.getTextPanel().addParagraph(
                "WARNING: The Gate will close behind you. Find the Exit Gate inside to return.",
                java.awt.Color.ORANGE);

        opts.addOption("Enter the Salvage Pit", "gs_pit_enter");
        opts.setShortcut("gs_pit_enter", Keyboard.KEY_G, false, false, false, false);

        opts.addOption("Leave", "defaultLeave");
        opts.setShortcut("defaultLeave", Keyboard.KEY_ESCAPE, false, false, false, false);
    }

    private void displayExitDialog(InteractionDialogAPI dialog) {
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        dialog.getTextPanel().addParagraph(
                "The Exit Gate hums with energy. Stepping through will return you to where you came from. " +
                "The Salvage Pit will collapse behind you.");

        opts.addOption("Return to the Sector", "gs_pit_exit_confirm", "Leave");
        opts.setShortcut("gs_pit_exit_confirm", Keyboard.KEY_G, false, false, false, false);

        opts.addOption("Stay a little longer", "gs_pit_exit_cancel");
        opts.setShortcut("gs_pit_exit_cancel", Keyboard.KEY_ESCAPE, false, false, false, false);
    }
}
