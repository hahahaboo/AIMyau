package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class KnockbackDelay extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private final IntProperty distanceToTarget = new IntProperty("Distance to target", 6, 3, 12);
    private final IntProperty maximumDelay = new IntProperty("Maximum delay", 200, 50, 1000);
    private final PercentProperty chance = new PercentProperty("Chance %", 100);

    private final BooleanProperty inAir = new BooleanProperty("In air", true);
    private final BooleanProperty lookingAtPlayer = new BooleanProperty("Looking at player", false);
    private final BooleanProperty requireLMB = new BooleanProperty("Require LMB", false);
    private final BooleanProperty bidirectional = new BooleanProperty("Bidirectional", true);
    private final BooleanProperty showBox = new BooleanProperty("Show Box", true);

    private final Queue<TimedPacket> inboundQueue = new ConcurrentLinkedQueue<>();
    private boolean blinking = false;
    private long lastBlinkStartTime = 0;
    private double savedX, savedY, savedZ;

    public KnockbackDelay() {
        super("KnockbackDelay", "Delays knockback packets", Category.COMBAT, 0, false, false);
    }

    @Override
    public void onDisabled() {
        flush();
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacketReceive(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE || !isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof S08PacketPlayerPosLook) {
            flush();
            return;
        }

        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity velocityPacket = (S12PacketEntityVelocity) packet;
            if (velocityPacket.getEntityID() == mc.thePlayer.getEntityId()) {
                
                if (blinking) {
                    event.setCancelled(true);
                    inboundQueue.add(new TimedPacket(packet, System.currentTimeMillis()));
                    return;
                }

                if (shouldDelay()) {
                    event.setCancelled(true);
                    inboundQueue.add(new TimedPacket(packet, System.currentTimeMillis()));
                    startBlinking();
                }
                return;
            }
        }

        if (!blinking) return;

        if (packet instanceof S07PacketRespawn) return;
        if (packet instanceof S03PacketTimeUpdate) return;
        if (packet instanceof S06PacketUpdateHealth) return;
        if (packet instanceof S13PacketDestroyEntities) return;
        if (packet instanceof S02PacketChat) return;
        if (packet instanceof S2FPacketSetSlot) return;

        event.setCancelled(true);
        inboundQueue.add(new TimedPacket(packet, System.currentTimeMillis()));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.POST || !isEnabled() || mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
            flush();
            return;
        }

        if (!blinking) return;

        long now = System.currentTimeMillis();
        
        if (now - lastBlinkStartTime >= maximumDelay.getValue()) {
            flush();
            return;
        }

        while (!inboundQueue.isEmpty()) {
            TimedPacket timed = inboundQueue.peek();
            if (timed != null && now - timed.time >= maximumDelay.getValue()) {
                inboundQueue.poll();
                processPacket(timed.packet);
            } else {
                break;
            }
        }

        if (inboundQueue.isEmpty()) {
            stopBlinking();
        }
    }

    private boolean shouldDelay() {
        if (chance.getValue() < 100 && Math.random() * 100 > chance.getValue()) return false;
        
        EntityPlayer target = getNearestPlayer(distanceToTarget.getValue());
        if (target == null) return false;

        if (inAir.getValue() && mc.thePlayer.onGround) return false;
        if (lookingAtPlayer.getValue() && !isLookingAtPlayer(target)) return false;
        if (requireLMB.getValue() && !Mouse.isButtonDown(0)) return false;

        return true;
    }

    private void startBlinking() {
        blinking = true;
        lastBlinkStartTime = System.currentTimeMillis();
        savedX = mc.thePlayer.posX;
        savedY = mc.thePlayer.posY;
        savedZ = mc.thePlayer.posZ;
        if (bidirectional.getValue()) {
            Myau.blinkManager.setBlinkState(true, BlinkModules.KBDELAY);
        }
    }

    private void stopBlinking() {
        blinking = false;
        if (bidirectional.getValue()) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.KBDELAY);
        }
    }

    private void flush() {
        stopBlinking();
        while (!inboundQueue.isEmpty()) {
            TimedPacket timed = inboundQueue.poll();
            if (timed != null) processPacket(timed.packet);
        }
    }

    @SuppressWarnings("unchecked")
    private void processPacket(Packet<?> packet) {
        if (mc.getNetHandler() != null) {
            ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
        }
    }

    private EntityPlayer getNearestPlayer(double range) {
        EntityPlayer closest = null;
        double closestDist = range * range;
        
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead) continue;
            // AntiBot.isBot(player) 已移除 (按要求)
            
            double distSq = mc.thePlayer.getDistanceSqToEntity(player);
            if (distSq <= closestDist) {
                closestDist = distSq;
                closest = player;
            }
        }
        return closest;
    }

    private boolean isLookingAtPlayer(EntityPlayer target) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLook(1.0F);
        double range = distanceToTarget.getValue();
        Vec3 end = eyes.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        
        AxisAlignedBB bb = target.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
        MovingObjectPosition mop = bb.calculateIntercept(eyes, end);
        
        return mop != null;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !showBox.getValue() || !blinking) return;
        
        net.minecraft.client.renderer.entity.RenderManager rm = mc.getRenderManager();
        double x = savedX - rm.viewerPosX;
        double y = savedY - rm.viewerPosY;
        double z = savedZ - rm.viewerPosZ;
        double w = 0.3, h = 1.8;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableLighting();
        GL11.glLineWidth(1.5f);
        GL11.glColor4f(1.0f, 0.5f, 0.0f, 0.6f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x-w, y, z-w); GL11.glVertex3d(x+w, y, z-w);
        GL11.glVertex3d(x+w, y, z-w); GL11.glVertex3d(x+w, y, z+w);
        GL11.glVertex3d(x+w, y, z+w); GL11.glVertex3d(x-w, y, z+w);
        GL11.glVertex3d(x-w, y, z+w); GL11.glVertex3d(x-w, y, z-w);
        GL11.glVertex3d(x-w, y+h, z-w); GL11.glVertex3d(x+w, y+h, z-w);
        GL11.glVertex3d(x+w, y+h, z-w); GL11.glVertex3d(x+w, y+h, z+w);
        GL11.glVertex3d(x+w, y+h, z+w); GL11.glVertex3d(x-w, y+h, z+w);
        GL11.glVertex3d(x-w, y+h, z+w); GL11.glVertex3d(x-w, y+h, z-w);
        GL11.glVertex3d(x-w, y, z-w); GL11.glVertex3d(x-w, y+h, z-w);
        GL11.glVertex3d(x+w, y, z-w); GL11.glVertex3d(x+w, y+h, z-w);
        GL11.glVertex3d(x+w, y, z+w); GL11.glVertex3d(x+w, y+h, z+w);
        GL11.glVertex3d(x-w, y, z+w); GL11.glVertex3d(x-w, y+h, z+w);
        GL11.glEnd();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static class TimedPacket {
        final Packet<?> packet;
        final long time;
        TimedPacket(Packet<?> packet, long time) { this.packet = packet; this.time = time; }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{"Delay: " + maximumDelay.getValue() + "ms", "Bidirectional: " + bidirectional.getValue()};
    }
}
