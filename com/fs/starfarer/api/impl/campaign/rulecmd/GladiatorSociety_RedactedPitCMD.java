package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import src.data.scripts.campaign.GladiatorSociety_RedactedPit;

import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * Commands:
 *   GladiatorSociety_RedactedPitCMD spawnPortal  - spawns entry gate
 *   GladiatorSociety_RedactedPitCMD enter        - enters the pit
 *   GladiatorSociety_RedactedPitCMD exit         - exits the pit
 *   GladiatorSociety_RedactedPitCMD displayEntry - entry gate dialog
 *   GladiatorSociety_RedactedPitCMD displayExit  - exit gate dialog
 */
public class GladiatorSociety_RedactedPitCMD extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        String arg = params.get(0).getString(memoryMap);

        switch (arg) {
            case "spawnPortal":
                if (!GladiatorSociety_RedactedPit.isRATInstalled()) {
                    dialog.getTextPanel().addParagraph(
                            "[REDACTED] requires the 'Random Assortment of Things' mod to be installed.",
                            Color.RED);
                    return true;
                }
                GladiatorSociety_RedactedPit.spawnEntryGate(dialog.getInteractionTarget());
                dialog.getTextPanel().addParagraph(
                        "A Gate has appeared nearby. Fly to it to enter [REDACTED].",
                        Color.GREEN);
                return true;
            case "enter":
                GladiatorSociety_RedactedPit.enterRedactedPit(dialog);
                return true;
            case "exit":
                GladiatorSociety_RedactedPit.exitRedactedPit(dialog);
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
                "The Gate pulses with an unsettling energy. Sensor readings beyond it are... corrupted. " +
                "Whatever is in there, it is not from this sector.",
                Color.WHITE);
        dialog.getTextPanel().addParagraph(
                "WARNING: Abyssal entities detected. This is a one-way trip until you find the Exit Gate. " +
                "Expect extreme resistance.",
                Color.RED);

        opts.addOption("Enter [REDACTED]", "gs_redacted_enter");
        opts.setShortcut("gs_redacted_enter", Keyboard.KEY_G, false, false, false, false);
        opts.addOption("Leave", "defaultLeave");
        opts.setShortcut("defaultLeave", Keyboard.KEY_ESCAPE, false, false, false, false);
    }

    private void displayExitDialog(InteractionDialogAPI dialog) {
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        dialog.getTextPanel().addParagraph(
                "The Exit Gate hums. Stepping through will return you to the sector. " +
                "The anomaly will collapse behind you.");

        opts.addOption("Return to the Sector", "gs_redacted_exit_confirm");
        opts.setShortcut("gs_redacted_exit_confirm", Keyboard.KEY_G, false, false, false, false);
        opts.addOption("Stay", "gs_redacted_exit_cancel");
        opts.setShortcut("gs_redacted_exit_cancel", Keyboard.KEY_ESCAPE, false, false, false, false);
    }
}
