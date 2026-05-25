package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.module.Module;
import myau.util.ChatUtil;

import java.util.ArrayList;

public class HideCommand extends Command {

    public HideCommand() {
        super(new ArrayList<String>() {{
            add("hide");
        }});
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            ChatUtil.sendFormatted("&cUsage: .hide <module_name>");
            return;
        }

        String moduleName = args.get(1);
        Module module = Myau.moduleManager.getModule(moduleName);

        if (module == null) {
            ChatUtil.sendFormatted("&cModule '" + moduleName + "' not found!");
            return;
        }

        module.setHidden(true);
        ChatUtil.sendFormatted("&aModule '" + module.getName() + "' is now hidden from HUD.");
    }
}
