package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.module.Module;
import myau.util.ChatUtil;

import java.util.ArrayList;

public class ShowCommand extends Command {

    public ShowCommand() {
        super(new ArrayList<String>() {{
            add("show");
        }});
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            ChatUtil.sendFormatted("&cUsage: .show <module_name>");
            return;
        }

        String moduleName = args.get(1);
        Module module = Myau.moduleManager.getModule(moduleName);

        if (module == null) {
            ChatUtil.sendFormatted("&cModule '" + moduleName + "' not found!");
            return;
        }

        module.setHidden(false);
        ChatUtil.sendFormatted("&aModule '" + module.getName() + "' is now visible on HUD.");
    }
}
