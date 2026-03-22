package myau.module.modules;

import myau.module.Category;
import myau.module.Module;

public class NoHitDelay extends Module {
    public NoHitDelay() {
        super("NoHitDelay", "Remove hit delay", Category.PLAYER, 0, true, true);
    }
}
