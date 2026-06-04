package myau.module.modules;

import myau.module.Module;
import myau.module.Category;

public class AntiObfuscate extends Module {
    public AntiObfuscate() {
        super("AntiObfuscate", " ", Category.RENDER, 0, false, true);
    }

    public String stripObfuscated(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("§k", "");
    }
}
