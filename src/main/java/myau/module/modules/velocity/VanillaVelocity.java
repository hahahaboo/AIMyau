package myau.module.modules.velocity;

import myau.events.KnockbackEvent;
import myau.events.PacketEvent;
import myau.module.modules.Velocity;
import myau.property.properties.PercentProperty;
import net.minecraft.network.play.server.S27PacketExplosion;
import java.util.Random;

public class VanillaVelocity extends VelocityMode {

    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 100);
    public final PercentProperty vertical = new PercentProperty("vertical", 100);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100);

    private final Random randomChance = new Random();

    public VanillaVelocity(String name, Velocity parent) {
        super(name, parent);
    }

    @Override
    public void onKnockback(KnockbackEvent event) {
        Velocity p = getParent();
        if (!p.isEnabled() || event.isCancelled()) {
            p.pendingExplosion = false;
            p.allowNext = true;
            return;
        }

        if (!p.allowNext) {
            p.allowNext = true;

            if (p.pendingExplosion) {
                p.pendingExplosion = false;
                // Explosion 處理
                if (explosionHorizontal.getValue() > 0) {
                    event.setX(event.getX() * (double) explosionHorizontal.getValue() / 100.0);
                    event.setZ(event.getZ() * (double) explosionHorizontal.getValue() / 100.0);
                } else {
                    event.setX(mc.thePlayer.motionX);
                    event.setZ(mc.thePlayer.motionZ);
                }
                if (explosionVertical.getValue() > 0) {
                    event.setY(event.getY() * (double) explosionVertical.getValue() / 100.0);
                } else {
                    event.setY(mc.thePlayer.motionY);
                }
            } else {
                // 一般 knockback + chance 處理（完整還原原版邏輯）
                boolean applyThisTime = randomChance.nextDouble() <= (double) chance.getValue() / 100.0;
                if (applyThisTime) {
                    if (horizontal.getValue() > 0) {
                        event.setX(event.getX() * (double) horizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * (double) horizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (vertical.getValue() > 0) {
                        event.setY(event.getY() * (double) vertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                }
            }
        }
    }

    @Override
    public void onPacket(PacketEvent event) {
        Velocity p = getParent();
        if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                p.pendingExplosion = true;
                if (explosionHorizontal.getValue() == 0 || explosionVertical.getValue() == 0) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
