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
    
    public final IntProperty time = new IntProperty("time", 6000, 1, 24000);

    public WorldTime() {
        super("WorldTime", "改變玩家端的天空時間 (client-side only)", Category.WORLD, 0, false, false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || Minecraft.getMinecraft().theWorld == null) {
            return;
        }
        // 只在啟用時強制設定時間
        ((World) Minecraft.getMinecraft().theWorld).setWorldTime(this.time.getValue());
    }

    @Override
    public void onEnabled() {
        if (Minecraft.getMinecraft().theWorld != null) {
            ((World) Minecraft.getMinecraft().theWorld).setWorldTime(this.time.getValue());
        }
    }
}
