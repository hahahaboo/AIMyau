package myau.module.modules;

import myau.module.Category;
import myau.module.Module;

public class AbortBreaking extends Module {
    public AbortBreaking() {
        super("AbortBreaking", "Prevents aborting block breaking progress.", Category.WORLD, 0, false, false);
    }
}
