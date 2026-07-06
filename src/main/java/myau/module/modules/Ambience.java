package myau.module.modules;

import myau.event.EventTarget;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.module.Module;
import myau.module.Category;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S03PacketTimeUpdate;

public final class Ambience extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty time = new IntProperty("Time", 0, 0, 24000);
    public final IntProperty speed = new IntProperty("Time Speed", 0, 0, 20);

    public Ambience() {
        super("Ambience", "改變玩家端的天空時間 (client-side only)", Category.RENDER, 0, false, true);
    }

    @Override
    public void onDisabled() {
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled() && mc.theWorld != null) {
            mc.theWorld.setWorldTime(
                    (long) (time.getValue() + (System.currentTimeMillis() * speed.getValue())));
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && mc.theWorld != null) {
            if (event.getPacket() instanceof S03PacketTimeUpdate) {
                event.setCancelled(true);
            }
        }
    }
}
