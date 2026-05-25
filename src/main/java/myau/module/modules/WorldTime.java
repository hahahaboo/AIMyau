package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.world.World;

public class WorldTime extends Module {
    
    public final IntProperty time = new IntProperty("time", 1000, 1, 24000);

    public WorldTime() {
        super("WorldTime", "改變玩家端的天空時間 (client-side only)", Category.RENDER, 0, false, true);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE) {
            return;
        }
        if (event.getPacket() instanceof S03PacketTimeUpdate) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) {
            ((World) mc.theWorld).setWorldTime(this.time.getValue());
        }
    }

    @Override
    public void onEnabled() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) {
            ((World) mc.theWorld).setWorldTime(this.time.getValue());
        }
    }
}
