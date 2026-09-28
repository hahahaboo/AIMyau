package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.module.modules.ClickGUIModule;
import myau.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class ClickGuiCommand extends Command {

    public ClickGuiCommand() {
        super(new ArrayList<>(Arrays.asList("clickgui", "gui")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        ClickGUIModule guiModule = (ClickGUIModule) Myau.moduleManager.getModule("ClickGUI");

        if (guiModule == null) {
            ChatUtil.sendFormatted(String.format("%sClickGUI module not found!", Myau.clientName));
            return;
        }

        // 無參數：直接開關
        if (args.size() < 2) {
            guiModule.toggle();
            ChatUtil.sendFormatted(String.format("%sClickGUI %s&r",
                    Myau.clientName, guiModule.isEnabled() ? "&aopened" : "&cclosed"));
            return;
        }

        String sub = args.get(1).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "open":
            case "toggle":
                guiModule.toggle();
                ChatUtil.sendFormatted(String.format("%sClickGUI %s&r",
                        Myau.clientName, guiModule.isEnabled() ? "&aopened" : "&cclosed"));
                break;

            case "save":
                // 切換「記住位置」
                boolean next = !guiModule.saveGuiState.getValue();
                guiModule.saveGuiState.setValue(next);
                ChatUtil.sendFormatted(String.format("%sClickGUI Save Position %s&r",
                        Myau.clientName, next ? "&aenabled" : "&cdisabled"));
                break;

            default:
                ChatUtil.sendFormatted(String.format(
                        "%sUsage: .%s [&oopen&r/&otoggle&r/&osave&r]&r",
                        Myau.clientName, args.get(0).toLowerCase(Locale.ROOT)));
                break;
        }
    }
}
