package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BackTrack extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    // Modes
    private final ModeProperty mode = new ModeProperty("Mode", 1, new String[]{"Legacy", "Modern"});

    // Legacy-only
    private final ModeProperty legacyPos = new ModeProperty("Caching Mode", 0, 
            new String[]{"ClientPos", "ServerPos"}, 
            () -> this.mode.getValue() == 0);

    // Modern-only
    private final ModeProperty style = new ModeProperty("Style", 1, 
            new String[]{"Pulse", "Smooth"}, 
            () -> this.mode.getValue() == 1);

    private final FloatProperty distance = new FloatProperty("Distance", 3.5f, 1.0f, 8.0f, 
            () -> this.mode.getValue() == 1);

    private final BooleanProperty smart = new BooleanProperty("Smart", true, 
            () -> this.mode.getValue() == 1);

    // Always visible
    private final IntProperty nextBacktrackDelay = new IntProperty("Next Backtrack Delay", 2000, 0, 10000);
    private final IntProperty maxDelay = new IntProperty("Max Delay", 200, 0, 2000);
    private final IntProperty minDelay = new IntProperty("Min Delay", 100, 0, 2000);

    // ESP (always visible)
    private final ModeProperty espMode = new ModeProperty("ESP Mode", 1, new String[]{"None", "Box", "Model", "Wireframe"});
    private final FloatProperty wireframeWidth = new FloatProperty("Wireframe Width", 1.5f, 0.5f, 5.0f);
    private final IntProperty espColorR = new IntProperty("ESP Color R", 255, 0, 255);
    private final IntProperty espColorG = new IntProperty("ESP Color G", 0, 0, 255);
    private final IntProperty espColorB = new IntProperty("ESP Color B", 0, 0, 255);

    // Data
    private final Queue<QueueData> packetQueue = new LinkedList<>();
    private final Queue<Vec3Data> positions = new LinkedList<>();
    private final Map<EntityLivingBase, java.util.List<Vec3>> backtrackedPlayer = new ConcurrentHashMap<>();

    private EntityLivingBase target;
    private long globalTimer = System.currentTimeMillis();
    private long modernDelay = 80L;

    public BackTrack() {
        super("BackTrack", "Advanced backtracking with Legacy/Modern modes and customizable ESP", 
              Category.COMBAT, 0, false, false);
    }

    @Override
    public void onEnabled() {
        clearPackets();
        backtrackedPlayer.clear();
        target = null;
        globalTimer = System.currentTimeMillis();
        modernDelay = getRandomDelay(minDelay.getValue(), maxDelay.getValue());
    }

    @Override
    public void onDisabled() {
        clearPackets();
        backtrackedPlayer.clear();
        target = null;
    }

    private void clearPackets() {
        packetQueue.clear();
        positions.clear();
    }

    private long getRandomDelay(long min, long max) {
        return min + (long) (Math.random() * (max - min));
    }

    private long getSupposedDelay() {
        return mode.getValue() == 1 ? modernDelay : maxDelay.getValue();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        Packet<?> packet = event.getPacket();

        if (event.getType() == EventType.SEND && packet instanceof C03PacketPlayer) {
            C03PacketPlayer c03 = (C03PacketPlayer) packet;
            if (!c03.isMoving() && !c03.getRotating()) return;

            if (target == null) return;

            if (mode.getValue() == 0) { // Legacy
                handleLegacyMode(c03);
            } else {
                handleModernMode(c03);
            }
        } 
        else if (event.getType() == EventType.RECEIVE) {
            if (packet instanceof S18PacketEntityTeleport) {
                S18PacketEntityTeleport tp = (S18PacketEntityTeleport) packet;
                Entity entity = mc.theWorld.getEntityByID(tp.getEntityId());
                if (entity instanceof EntityLivingBase && entity == target) {
                    handleTeleport((EntityLivingBase) entity);
                }
            } else if (packet instanceof S14PacketEntity) {
                S14PacketEntity ePacket = (S14PacketEntity) packet;
                Entity entity = ePacket.getEntity(mc.theWorld);
                if (entity instanceof EntityLivingBase && entity == target) {
                    handleEntityMove((EntityLivingBase) entity);
                }
            }
        }
    }

    private void handleLegacyMode(C03PacketPlayer packet) {
        if (legacyPos.getValue() == 0) { // ClientPos
            packetQueue.add(new QueueData(packet, System.currentTimeMillis()));
        } else { // ServerPos
            Vec3 serverPos = new Vec3(target.posX, target.posY, target.posZ);
            positions.add(new Vec3Data(serverPos, System.currentTimeMillis()));
        }
    }

    private void handleModernMode(C03PacketPlayer packet) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - globalTimer >= getSupposedDelay()) {
            globalTimer = currentTime;
            modernDelay = getRandomDelay(minDelay.getValue(), maxDelay.getValue());

            packetQueue.add(new QueueData(packet, currentTime));
            positions.add(new Vec3Data(new Vec3(target.posX, target.posY, target.posZ), currentTime));
        }
    }

    private void handleTeleport(EntityLivingBase entity) {
        Vec3 newPos = new Vec3(entity.posX, entity.posY, entity.posZ);
        positions.add(new Vec3Data(newPos, System.currentTimeMillis()));
        storeBacktrackedPosition(entity, newPos);
    }

    private void handleEntityMove(EntityLivingBase entity) {
        Vec3 newPos = new Vec3(entity.posX, entity.posY, entity.posZ);
        storeBacktrackedPosition(entity, newPos);
    }

    private void storeBacktrackedPosition(EntityLivingBase entity, Vec3 pos) {
        backtrackedPlayer.computeIfAbsent(entity, k -> new ArrayList<>()).add(pos);
        java.util.List<Vec3> list = backtrackedPlayer.get(entity);
        if (list.size() > 50) list.remove(0);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        updateTarget();
        cleanOldData();
        processPacketQueue();
    }

    private void updateTarget() {
        target = null;
        double closest = Double.MAX_VALUE;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead || player.getHealth() <= 0) continue;

            double dist = mc.thePlayer.getDistanceToEntity(player);
            if (dist < closest && dist <= 8.0) {
                closest = dist;
                target = player;
            }
        }
    }

    private void cleanOldData() {
        long now = System.currentTimeMillis();
        long maxAge = 5000;

        packetQueue.removeIf(data -> now - data.timestamp > maxAge);
        positions.removeIf(data -> now - data.timestamp > maxAge);

        backtrackedPlayer.values().forEach(list -> {
            if (list.size() > 20) list.clear();
        });
    }

    private void processPacketQueue() {
        if (packetQueue.isEmpty()) return;

        long now = System.currentTimeMillis();
        Iterator<QueueData> it = packetQueue.iterator();

        while (it.hasNext()) {
            QueueData data = it.next();
            if (now - data.timestamp >= getSupposedDelay()) {
                mc.getNetHandler().addToSendQueue(data.packet);
                it.remove();
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (espMode.getValue() == 0 || target == null) return;

        Color color = new Color(espColorR.getValue(), espColorG.getValue(), espColorB.getValue());
        double expand = 0.1;

        if (espMode.getValue() == 1) { // Box
            RenderUtil.drawEntityESP(target, color.getRGB(), expand);
        } else if (espMode.getValue() == 3) { // Wireframe
            RenderUtil.drawEntityWireframe(target, color.getRGB(), wireframeWidth.getValue());
        }
    }

    // Helper classes
    private static class QueueData {
        final Packet<?> packet;
        final long timestamp;

        QueueData(Packet<?> packet, long timestamp) {
            this.packet = packet;
            this.timestamp = timestamp;
        }
    }

    private static class Vec3Data {
        final Vec3 position;
        final long timestamp;

        Vec3Data(Vec3 position, long timestamp) {
            this.position = position;
            this.timestamp = timestamp;
        }
    }
}
