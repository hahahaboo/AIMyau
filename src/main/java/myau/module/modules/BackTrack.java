package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.AttackEvent;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorEntityOtherPlayerMP;
import myau.mixin.IAccessorRenderManager;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.util.ItemUtil;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Port of Slinky BacktrackModule to AIMyau (bt branch).
 * - TimingAccess-style server/client positions via IAccessorEntityOtherPlayerMP
 * - Packet pipeline aligned: mode3 pass-through, mode1 hold by age, sequence/offset
 */
public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int WARMUP_TICKS = 20;
    private static final long POSITION_INTERP_MS = 80L;
    private static final double POS_EPS = 1.0E-6;

    // ========== Slinky-aligned properties ==========
    public final FloatProperty targetDistMin = new FloatProperty("target-dist-min", 2.0F, 0.0F, 12.0F);
    public final FloatProperty targetDistMax = new FloatProperty("target-dist-max", 5.0F, 0.0F, 12.0F);
    public final FloatProperty delay = new FloatProperty("delay", 100.0F, 10.0F, 1000.0F);
    public final FloatProperty maxHurtTime = new FloatProperty("max-hurt-time", 10.0F, 1.0F, 10.0F);
    public final FloatProperty cooldown = new FloatProperty("cooldown", 5.0F, 0.0F, 100.0F);
    public final BooleanProperty disableOnHit = new BooleanProperty("disable-on-hit", false);
    public final BooleanProperty requireMousePressed = new BooleanProperty("require-mouse-pressed", false);
    public final BooleanProperty requireWeapon = new BooleanProperty("require-weapon", false);
    public final BooleanProperty ignoreBots = new BooleanProperty("ignore-bots", true);
    public final BooleanProperty ignoreTeammates = new BooleanProperty("ignore-teammates", true);
    public final BooleanProperty renderRemotePos = new BooleanProperty("render-remote-pos", false);
    public final ColorProperty indicatorColor = new ColorProperty("indicator-color", 0xFFFFFF, () -> this.renderRemotePos.getValue());
    public final FloatProperty indicatorLineWidth = new FloatProperty("indicator-line-width", 1.0F, 1.0F, 4.0F, () -> this.renderRemotePos.getValue());
    public final BooleanProperty indicatorFilled = new BooleanProperty("indicator-filled", true, () -> this.renderRemotePos.getValue());
    public final BooleanProperty indicatorRotate = new BooleanProperty("indicator-rotate", true, () -> this.renderRemotePos.getValue());

    // ========== Runtime ==========
    private final Queue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<TimedPacket>();

    private boolean active;
    private boolean absolute;
    private boolean hasYaw;
    private boolean render;

    private int targetId = -1;
    private int selfId = -1;
    private int cooldownLeft;
    private int interpolation;

    private int delayedSequence;
    private int releasedSequence;
    private int teleportSequence;
    private int globalPacketSequence;

    private long delayMs;

    private Vec3 offset = zero();
    private Vec3 previous = zero();
    private Vec3 current = zero();
    private Vec3 desired = zero();

    private float delayedYaw;
    private float previousYaw;
    private float currentYaw;
    private float desiredYaw;

    private EntityPlayer target;
    private int lastAttackTick = -999;

    private Vec3 positionInterpFrom;
    private Vec3 positionInterpTo;
    private long positionInterpStartMs;

    public BackTrack() {
        super("BackTrack", "Uses lag to make players easier to hit. (Slinky port)", Category.COMBAT, 0, false, false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{(int) this.delay.getValue().floatValue() + "ms"};
    }

    @Override
    public void onEnabled() {
        this.clear();
    }

    @Override
    public void onDisabled() {
        this.releaseAll();
        this.clear();
    }

    // ===================== Attack → target =====================
    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || mc.isSingleplayer() || mc.thePlayer == null) {
            return;
        }
        if (!(event.getTarget() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer attacked = (EntityPlayer) event.getTarget();
        if (!this.isValidCombatTarget(attacked)) {
            return;
        }
        if (this.target != null && this.target != attacked) {
            this.releaseAll();
        }
        this.target = attacked;
        this.targetId = attacked.getEntityId();
        this.selfId = mc.thePlayer.getEntityId();
        this.lastAttackTick = mc.thePlayer.ticksExisted;
    }

    // ===================== Tick (Slinky type 18) =====================
    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE || !this.isEnabled()) {
            return;
        }
        if (mc.isSingleplayer() || mc.thePlayer == null || mc.theWorld == null) {
            this.releaseAll();
            return;
        }
        if (mc.thePlayer.ticksExisted < WARMUP_TICKS) {
            this.releaseAll();
            return;
        }

        this.processDelayedPackets();

        if (this.cooldownLeft > 0) {
            this.cooldownLeft--;
            if (this.cooldownLeft > 0) {
                return;
            }
        }

        EntityPlayer t = this.resolveTarget();
        float low = Math.min(this.targetDistMin.getValue(), this.targetDistMax.getValue());
        float high = Math.max(this.targetDistMin.getValue(), this.targetDistMax.getValue());

        boolean allowed = this.isInputReady()
                && (!this.requireMousePressed.getValue() || mc.gameSettings.keyBindAttack.isKeyDown())
                && (!this.requireWeapon.getValue() || ItemUtil.isHoldingSword())
                && (mc.thePlayer.ticksExisted - this.lastAttackTick <= 20)
                && t != null
                && this.isValidCombatTarget(t)
                && !((float) t.hurtTime > this.maxHurtTime.getValue())
                && this.inDistanceSq(t, low, high);

        if (!allowed) {
            if (this.active) {
                this.cooldownLeft = (int) this.cooldown.getValue().floatValue();
                this.releaseAll();
            }
            this.active = false;
            this.render = false;
            return;
        }

        Vec3 self = clientPos(mc.thePlayer);
        Vec3 server = serverPos(t);
        Vec3 client = clientPos(t);
        int oldId = this.targetId;

        this.delayMs = (long) this.delay.getValue().floatValue();
        this.targetId = t.getEntityId();
        this.selfId = mc.thePlayer.getEntityId();
        this.target = t;

        if (!this.active) {
            // Slinky: only start when server is meaningfully farther than client pos
            if (!(0.01 < distance(self, server) - distance(self, client))) {
                this.render = false;
            } else {
                this.active = true;
                this.offset = zero();
                this.absolute = false;
                this.hasYaw = false;
                this.previous = this.current = this.desired = client;
                this.previousYaw = this.currentYaw = this.desiredYaw = t.rotationYaw;
                this.render = this.renderRemotePos.getValue();
                this.delayedSequence = 0;
                this.releasedSequence = 0;
                this.teleportSequence = 0;
            }
        } else {
            Vec3 candidate = this.absolute ? this.offset : this.offset.add(server);
            if (!(-0.05 > metric(self, server, candidate)) && oldId == this.targetId) {
                this.updatePosition(candidate, this.hasYaw ? this.delayedYaw : t.rotationYaw);
            } else {
                this.offset = zero();
                this.absolute = false;
                this.hasYaw = false;
                this.active = false;
                this.render = false;
                this.cooldownLeft = (int) this.cooldown.getValue().floatValue();
                this.releaseAll();
            }
        }
    }

    // ===================== Packet (Slinky packet()) =====================
    @EventTarget(Priority.LOW)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || mc.isSingleplayer()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (mc.thePlayer.ticksExisted < WARMUP_TICKS) {
            this.clearPending();
            return;
        }

        this.processDelayedPackets();

        if (!this.active) {
            return;
        }

        Packet<?> packet = event.getPacket();
        long now = System.currentTimeMillis();
        int seq = ++this.globalPacketSequence;

        // ----- mode 3: skip delay, still process (do NOT cancel) -----
        if (packet instanceof S29PacketSoundEffect || packet instanceof S06PacketUpdateHealth) {
            return;
        }
        if (packet instanceof S19PacketEntityStatus) {
            try {
                Entity e = ((S19PacketEntityStatus) packet).getEntity(mc.theWorld);
                if (e != null && e.getEntityId() == this.targetId) {
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        // ----- disable_on_hit / rubberband -----
        if (packet instanceof S12PacketEntityVelocity) {
            if (this.disableOnHit.getValue()
                    && ((S12PacketEntityVelocity) packet).getEntityID() == this.selfId) {
                this.active = false;
                this.render = false;
                this.releaseAll();
                return;
            }
        }
        if (packet instanceof S08PacketPlayerPosLook) {
            this.active = false;
            this.render = false;
            this.releaseAll();
            return;
        }
        if (packet instanceof S40PacketDisconnect) {
            this.active = false;
            this.target = null;
            this.releaseAll();
            return;
        }
        if (packet instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                if (id == this.targetId) {
                    this.active = false;
                    this.target = null;
                    this.releaseAll();
                    return;
                }
            }
        }

        // Fresh packet at channelRead HEAD ≈ age 0
        long ageMs = 0L;

        if (ageMs <= this.delayMs) {
            // mode 1: hold
            event.setCancelled(true);
            this.packetQueue.add(new TimedPacket(packet, now, seq));
            if (seq > this.delayedSequence) {
                this.delayedSequence = seq;
                this.publish(packet, false, seq);
            }
        } else {
            // age > delay: process + reconcile
            if (seq > this.releasedSequence) {
                this.publish(packet, true, seq);
                this.releasedSequence = seq;
            }
        }
    }

    /**
     * Slinky publish(e, reconcile) with sequence / teleportSequence guard.
     */
    private boolean publish(Packet<?> packet, boolean reconcile, int packetSequence) {
        if (reconcile && this.teleportSequence > packetSequence) {
            return false;
        }

        boolean relative = packet instanceof S14PacketEntity;
        if (!relative && !(packet instanceof S18PacketEntityTeleport)) {
            return false;
        }

        S14PacketEntity move = relative ? (S14PacketEntity) packet : null;
        S18PacketEntityTeleport teleport = relative ? null : (S18PacketEntityTeleport) packet;

        int id;
        if (relative) {
            Entity ent = move.getEntity(mc.theWorld);
            if (ent == null) {
                return false;
            }
            id = ent.getEntityId();
        } else {
            id = teleport.getEntityId();
        }
        if (id != this.targetId) {
            return false;
        }

        Vec3 value = relative
                ? new Vec3(
                move.func_149062_c() / 32.0,
                move.func_149061_d() / 32.0,
                move.func_149064_e() / 32.0)
                : new Vec3(
                teleport.getX() / 32.0,
                teleport.getY() / 32.0,
                teleport.getZ() / 32.0);

        if (reconcile) {
            this.offset = this.offset.subtract(value);
            if (!relative) {
                this.absolute = false;
            }
        } else {
            if (relative) {
                this.offset = this.offset.add(value);
                if (!move.func_149060_h()) {
                    return true;
                }
            } else {
                this.offset = value;
                this.absolute = true;
            }
            this.delayedYaw = (float) (relative ? move.func_149066_f() : teleport.getYaw()) * 360.0F * 0.00390625F;
            this.hasYaw = true;
            if (!relative) {
                this.teleportSequence = packetSequence;
            }
        }
        return true;
    }

    // ===================== Render (Slinky type 6) =====================
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.isSingleplayer() || !this.render || !this.renderRemotePos.getValue()) {
            return;
        }
        if (!this.active || this.target == null || this.target.isDead) {
            return;
        }

        long now = System.currentTimeMillis();
        if (this.positionInterpTo == null) {
            this.positionInterpFrom = this.current;
            this.positionInterpTo = this.current;
            this.positionInterpStartMs = now;
        } else if (positionChanged(this.current, this.positionInterpTo)) {
            double p = Math.min(1.0, (double) (now - this.positionInterpStartMs) / POSITION_INTERP_MS);
            this.positionInterpFrom = lerp(this.positionInterpFrom, this.positionInterpTo, p);
            this.positionInterpTo = this.current;
            this.positionInterpStartMs = now;
        }

        double progress = Math.min(1.0, (double) (now - this.positionInterpStartMs) / POSITION_INTERP_MS);
        Vec3 drawPos = lerp(this.positionInterpFrom, this.positionInterpTo, progress);
        this.drawBox(drawPos, this.indicatorColor.getValue(), this.indicatorLineWidth.getValue(),
                this.indicatorFilled.getValue());
    }

    // ===================== Helpers =====================

    public static double metric(Vec3 self, Vec3 server, Vec3 delayed) {
        return distance(self, delayed) - distance(self, server);
    }

    private void updatePosition(Vec3 position, float yaw) {
        if (!sameBits(this.desired, position) || yaw != this.desiredYaw) {
            this.interpolation = 3;
        }
        this.desired = position;
        this.previous = this.current;
        this.previousYaw = this.currentYaw;
        this.desiredYaw = yaw;

        if (this.interpolation < 1) {
            this.current = position;
        } else {
            double fraction = 1.0 / (double) this.interpolation;
            this.current = this.current.add(scale(position.subtract(this.current), fraction));
            yaw = (float) ((double) MathHelper.wrapAngleTo180_float(this.desiredYaw - this.currentYaw) * fraction)
                    + this.currentYaw;
            this.interpolation--;
        }
        this.currentYaw = yaw;
    }

    private boolean isValidCombatTarget(EntityPlayer p) {
        if (p == null || p.isDead) {
            return false;
        }
        if (TeamUtil.isFriend(p)) {
            return false;
        }
        if (this.ignoreBots.getValue() && TeamUtil.isBot(p)) {
            return false;
        }
        if (this.ignoreTeammates.getValue() && TeamUtil.isSameTeam(p)) {
            return false;
        }
        return true;
    }

    private EntityPlayer resolveTarget() {
        if (this.target != null && !this.target.isDead && mc.theWorld != null) {
            Entity e = mc.theWorld.getEntityByID(this.targetId);
            if (e instanceof EntityPlayer && e == this.target) {
                return this.target;
            }
        }
        return null;
    }

    private boolean isInputReady() {
        return mc.thePlayer != null && mc.theWorld != null && !mc.isGamePaused();
    }

    private boolean inDistanceSq(EntityPlayer t, float low, float high) {
        double d = mc.thePlayer.getDistanceSqToEntity(t);
        return !(d < (double) (low * low)) && !(d > (double) (high * high));
    }

    private void processDelayedPackets() {
        long now = System.currentTimeMillis();
        while (!this.packetQueue.isEmpty()) {
            TimedPacket timed = this.packetQueue.peek();
            if (timed == null || timed.ageMs(now) <= this.delayMs) {
                break;
            }
            this.packetQueue.poll();
            if (timed.sequence > this.releasedSequence) {
                this.publish(timed.packet, true, timed.sequence);
                this.releasedSequence = timed.sequence;
            }
            processPacket(timed.packet);
        }
    }

    private void releaseAll() {
        while (!this.packetQueue.isEmpty()) {
            TimedPacket timed = this.packetQueue.poll();
            if (timed == null) {
                continue;
            }
            if (timed.sequence > this.releasedSequence) {
                this.publish(timed.packet, true, timed.sequence);
                this.releasedSequence = timed.sequence;
            }
            processPacket(timed.packet);
        }
    }

    private void clearPending() {
        this.packetQueue.clear();
    }

    private void clear() {
        this.clearPending();
        this.active = this.absolute = this.hasYaw = this.render = false;
        this.targetId = this.selfId = -1;
        this.cooldownLeft = this.interpolation = 0;
        this.delayedSequence = this.releasedSequence = this.teleportSequence = this.globalPacketSequence = 0;
        this.delayMs = 0L;
        this.offset = this.previous = this.current = this.desired = zero();
        this.delayedYaw = this.previousYaw = this.currentYaw = this.desiredYaw = 0.0F;
        this.target = null;
        this.positionInterpFrom = this.positionInterpTo = null;
        this.positionInterpStartMs = 0L;
    }

    @SuppressWarnings("unchecked")
    private static void processPacket(Packet<?> packet) {
        if (packet == null || mc.getNetHandler() == null) {
            return;
        }
        try {
            ((Packet<net.minecraft.network.play.INetHandlerPlayClient>) packet)
                    .processPacket(mc.getNetHandler());
        } catch (Throwable ignored) {
        }
    }

    private void drawBox(Vec3 position, int color, float lineWidth, boolean filled) {
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
        double x = position.xCoord - rm.getRenderPosX();
        double y = position.yCoord - rm.getRenderPosY();
        double z = position.zCoord - rm.getRenderPosZ();
        float w = 0.6F;
        float h = 1.8F;
        AxisAlignedBB box = new AxisAlignedBB(
                x - w / 2.0, y, z - w / 2.0,
                x + w / 2.0, y + h, z + w / 2.0);
        int r = color >> 16 & 255;
        int g = color >> 8 & 255;
        int b = color & 255;
        RenderUtil.enableRenderState();
        if (filled) {
            RenderUtil.drawFilledBox(box, r, g, b);
        }
        RenderUtil.drawBoundingBox(box, r, g, b, 255, lineWidth);
        GlStateManager.resetColor();
        RenderUtil.disableRenderState();
    }

    // ----- TimingAccess-style positions -----

    /** Slinky TimingAccess.position */
    private static Vec3 clientPos(Entity e) {
        return new Vec3(e.posX, e.posY, e.posZ);
    }

    /** Slinky TimingAccess.server */
    private static Vec3 serverPos(Entity e) {
        if (e instanceof EntityOtherPlayerMP) {
            IAccessorEntityOtherPlayerMP a = (IAccessorEntityOtherPlayerMP) e;
            return new Vec3(a.getOtherPlayerMPX(), a.getOtherPlayerMPY(), a.getOtherPlayerMPZ());
        }
        return clientPos(e);
    }

    private static double distance(Vec3 a, Vec3 b) {
        double dx = a.xCoord - b.xCoord;
        double dy = a.yCoord - b.yCoord;
        double dz = a.zCoord - b.zCoord;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Vec3 scale(Vec3 v, double s) {
        return new Vec3(v.xCoord * s, v.yCoord * s, v.zCoord * s);
    }

    private static Vec3 zero() {
        return new Vec3(0.0, 0.0, 0.0);
    }

    private static boolean sameBits(Vec3 a, Vec3 b) {
        return Double.doubleToRawLongBits(a.xCoord) == Double.doubleToRawLongBits(b.xCoord)
                && Double.doubleToRawLongBits(a.yCoord) == Double.doubleToRawLongBits(b.yCoord)
                && Double.doubleToRawLongBits(a.zCoord) == Double.doubleToRawLongBits(b.zCoord);
    }

    private static boolean positionChanged(Vec3 a, Vec3 b) {
        return Math.abs(a.xCoord - b.xCoord) > POS_EPS
                || Math.abs(a.yCoord - b.yCoord) > POS_EPS
                || Math.abs(a.zCoord - b.zCoord) > POS_EPS;
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double t) {
        if (t <= 0.0) {
            return from;
        }
        if (t >= 1.0) {
            return to;
        }
        return new Vec3(
                from.xCoord + (to.xCoord - from.xCoord) * t,
                from.yCoord + (to.yCoord - from.yCoord) * t,
                from.zCoord + (to.zCoord - from.zCoord) * t);
    }

    // ----- public API -----
    public boolean isRenderingServerPositionFor(EntityLivingBase entity) {
        return this.active && this.render && this.renderRemotePos.getValue()
                && this.target == entity && this.delayMs > 0L;
    }

    public Vec3 getBacktrackPosition() {
        return this.active && this.delayMs > 0L ? this.current : null;
    }

    public EntityPlayer getBacktrackTarget() {
        return this.active && this.delayMs > 0L ? this.target : null;
    }

    private static final class TimedPacket {
        final Packet<?> packet;
        final long receiveAt;
        final int sequence;

        TimedPacket(Packet<?> packet, long receiveAt, int sequence) {
            this.packet = packet;
            this.receiveAt = receiveAt;
            this.sequence = sequence;
        }

        long ageMs(long now) {
            return now - this.receiveAt;
        }
    }
}
