package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

public class WorldTime extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    public final IntProperty time = new IntProperty("time", 1000, 1, 24000);

    public WorldTime() {
        super("WorldTime", "改變玩家端的的天空時間 (client-side only)", Category.WORLD);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.theWorld == null) {
            return;
        }
        // 只在啟用時強制設定時間
        ((World) mc.theWorld).setWorldTime(this.time.getValue());
    }

    @Override
    public void onEnabled() {
        if (mc.theWorld != null) {
            ((World) mc.theWorld).setWorldTime(this.time.getValue());
        }
    }
}
