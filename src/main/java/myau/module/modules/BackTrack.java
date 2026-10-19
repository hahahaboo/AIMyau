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
import myau.property.properties.IntProperty;
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

import java.util.ArrayDeque;

/**
 * Slinky BacktrackModule logic on AIMyau.
 *
 * Packet modes (Slinky PacketPipeline):
 *   0 = pass (or queue only to preserve order behind held packets)
 *   1 = hold while active && ageMs <= delay
 *   3 = immediate process, never hold (S06 / S29 / target S19)
 * Mode 2 (discard) is never used.
 *
 * Stopping active does NOT drop packets — queue is always drained via processPacket
 * (same idea as pipeline poll after mode is no longer 1).
 */
public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int WARMUP_TICKS = 20;
    private static final long POSITION_INTERP_MS = 80L;
    private static final double POS_EPS = 1.0E-6;

    // ----- Slinky-aligned properties -----
    public final FloatProperty targetDistMin = new FloatProperty("target-dist-min", 2.0F, 0.0F, 6.0F);
    public final FloatProperty targetDistMax = new FloatProperty("target-dist-max", 6.0F, 0.0F, 12.0F);
    public final FloatProperty delay = new FloatProperty("delay", 100.0F, 0.0F, 1000.0F);
    public final IntProperty maxHurtTime = new IntProperty("max-hurt-time", 10, 1, 10);
    public final IntProperty cooldown = new IntProperty("cooldown", 250, 0, 2000);
    public final BooleanProperty flushOnDamage = new BooleanProperty("flush-on-damage", false);
    public final BooleanProperty requireMousePressed = new BooleanProperty("require-mouse-pressed", false);
    public final BooleanProperty requireWeapon = new BooleanProperty("require-weapon", false);
    public final BooleanProperty ignoreBots = new BooleanProperty("ignore-bots", true);
    public final BooleanProperty ignoreTeammates = new BooleanProperty("ignore-teammates", true);
    public final BooleanProperty renderRemotePos = new BooleanProperty("render-remote-pos", true);
    public final ColorProperty indicatorColor =
            new ColorProperty("indicator-color", 0xFFFFFF, () -> this.renderRemotePos.getValue());
    public final FloatProperty indicatorLineWidth =
            new FloatProperty("indicator-line-width", 1.0F, 1.0F, 4.0F, () -> this.renderRemotePos.getValue());
    public final BooleanProperty indicatorFilled =
            new BooleanProperty("indicator-filled", true, () -> this.renderRemotePos.getValue());
    public final BooleanProperty indicatorRotate =
            new BooleanProperty("indicator-rotate", true, () -> this.renderRemotePos.getValue());

    // ----- runtime (Slinky fields + local pipeline queue) -----
    private final ArrayDeque<TimedPacket> inbound = new ArrayDeque<TimedPacket>();

    private boolean active;
    private boolean absolute;
    private boolean hasYaw;
    private boolean render;

    private int targetId;
    private int selfId;
    /** 冷卻結束的 wall-clock 時間；0 = 未在冷卻 */
    private long cooldownUntilMs;
    private int interpolation;
    private int delayedSequence;
    private int releasedSequence;
    private int teleportSequence;
    private int packetSequenceCounter;

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
        super("BackTrack", "Uses lag to make players easier to hit. (Slinky logic)", Category.COMBAT, 0, false, false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{(int) this.delay.getValue().floatValue() + "ms"};
    }

    @Override
    public void onEnabled() {
        // Slinky clear() only resets state — any leftover must be drained, never dropped
        this.drainInbound();
        this.resetState();
    }

    @Override
    public void onDisabled() {
        // Like pipeline drain when hold ends: always process, never clear-drop
        this.drainInbound();
        this.resetState();
    }

    // ===================== Target (AttackEvent approximates combat target) =====================
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
            // target swap: release held packets first (no drop)
            this.drainInbound();
        }
        this.target = attacked;
        this.targetId = attacked.getEntityId();
        this.selfId = mc.thePlayer.getEntityId();
        this.lastAttackTick = mc.thePlayer.ticksExisted;
    }

    // ===================== Tick = Slinky event type 18 =====================
    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE || !this.isEnabled()) {
            return;
        }
        if (mc.isSingleplayer() || mc.thePlayer == null || mc.theWorld == null) {
            this.deactivateAndDrain();
            return;
        }
        if (mc.thePlayer.ticksExisted < WARMUP_TICKS) {
            this.deactivateAndDrain();
            return;
        }

        // ~ PacketPipeline.poll
        this.pollInbound();

        // 可選：自己受傷時立刻放光（main 舊版 flush-on-damage）
        if (this.flushOnDamage.getValue()
                && this.active
                && mc.thePlayer.hurtTime > 0) {
            int cd = this.cooldown.getValue();
            this.cooldownUntilMs = cd <= 0 ? 0L : System.currentTimeMillis() + (long) cd;
            this.deactivateAndDrain();
            return;
        }

        if (this.cooldownUntilMs > 0L) {
            if (System.currentTimeMillis() < this.cooldownUntilMs) {
                return;
            }
            this.cooldownUntilMs = 0L;
        }

        EntityPlayer t = this.resolveTarget();
        float low = Math.min(this.targetDistMin.getValue(), this.targetDistMax.getValue());
        float high = Math.max(this.targetDistMin.getValue(), this.targetDistMax.getValue());

        // Slinky allowed checks (+ optional AIMyau bot/team filters)
        boolean allowed = this.isInputReady()
                && (!this.requireMousePressed.getValue() || mc.gameSettings.keyBindAttack.isKeyDown())
                && (!this.requireWeapon.getValue() || ItemUtil.isHoldingSword())
                && (mc.thePlayer.ticksExisted - this.lastAttackTick <= 20)
                && t != null
                && this.isValidCombatTarget(t)
                && !(t.hurtTime > this.maxHurtTime.getValue())
                && this.inDistanceSq(t, low, high);

        if (!allowed) {
            if (this.active) {
                int cd = this.cooldown.getValue();
                this.cooldownUntilMs = cd <= 0 ? 0L : System.currentTimeMillis() + (long) cd;
                // Slinky: active=false → poll no longer mode1 → packets release
                this.deactivateAndDrain();
            } else {
                this.active = false;
                this.render = false;
            }
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
            // Slinky: only start if server is farther from self than client pos by > 0.01
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
                int cd = this.cooldown.getValue();
                this.cooldownUntilMs = cd <= 0 ? 0L : System.currentTimeMillis() + (long) cd;
                this.drainInbound();
            }
        }
    }

    // ===================== Packet = Slinky event type 2 + PacketPipeline.accept =====================
    @EventTarget(Priority.LOW)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || mc.isSingleplayer()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        // poll first (age grows for queued entries)
        this.pollInbound();

        if (mc.thePlayer.ticksExisted < WARMUP_TICKS) {
            // Slinky has no clear-drop; drain any hold
            this.deactivateAndDrain();
            return;
        }

        Packet<?> packet = event.getPacket();
        long now = System.currentTimeMillis();
        int sequence = ++this.packetSequenceCounter;

        // Not active → mode 0: do not intercept (Slinky packet() returns immediately)
        if (!this.active) {
            // Still preserve order: if something is in queue, queue this too (mode 0 + non-empty queue)
            if (!this.inbound.isEmpty()) {
                event.setCancelled(true);
                this.inbound.addLast(new TimedPacket(packet, now, sequence));
            }
            return;
        }

        // ----- mode 3: immediate, never hold -----
        if (packet instanceof S29PacketSoundEffect || packet instanceof S06PacketUpdateHealth) {
            return; // do not cancel
        }
        if (packet instanceof S19PacketEntityStatus) {
            try {
                Entity e = ((S19PacketEntityStatus) packet).getEntity(mc.theWorld);
                if (e != null && e.getEntityId() == this.targetId) {
                    return; // mode 3 for target status only
                }
            } catch (Throwable ignored) {
            }
        }

        // 自己的 S12：一律 flush（對齊 main 舊版 shouldFlush），當前包不 cancel
        if (packet instanceof S12PacketEntityVelocity) {
            if (((S12PacketEntityVelocity) packet).getEntityID() == this.selfId) {
                this.active = false;
                this.render = false;
                this.drainInbound();
                return;
            }
        }
        if (packet instanceof S08PacketPlayerPosLook) {
            this.active = false;
            this.render = false;
            this.drainInbound();
            return;
        }
        if (packet instanceof S40PacketDisconnect) {
            this.active = false;
            this.target = null;
            this.drainInbound();
            return;
        }
        if (packet instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                if (id == this.targetId) {
                    this.active = false;
                    this.target = null;
                    this.drainInbound();
                    return;
                }
            }
        }

        // ----- accept with age 0 -----
        // active && age<=delay → mode 1 (hold)
        // Slinky also holds when mode 0 but queue non-empty; with age 0 and active always mode1
        long ageMs = 0L;
        int mode = this.classify(packet, ageMs, sequence);

        if (mode == 1 || (mode == 0 && !this.inbound.isEmpty())) {
            event.setCancelled(true);
            this.inbound.addLast(new TimedPacket(packet, now, sequence));
            if (mode == 1 && sequence > this.delayedSequence) {
                this.delayedSequence = sequence;
                this.publish(packet, false, sequence);
            }
        }
        // mode 0 + empty queue, or mode 3: leave uncancelled → vanilla process
    }

    /**
     * Mirrors Slinky BacktrackModule.packet() dispatchMode assignment for a given age.
     */
    private int classify(Packet<?> packet, long ageMs, int sequence) {
        if (!this.active) {
            return 0;
        }

        // mode 3 cases already returned in onPacket before classify for accept;
        // still needed when polling queued entries
        if (packet instanceof S29PacketSoundEffect || packet instanceof S06PacketUpdateHealth) {
            return 3;
        }
        if (packet instanceof S19PacketEntityStatus) {
            try {
                Entity e = ((S19PacketEntityStatus) packet).getEntity(mc.theWorld);
                if (e != null && e.getEntityId() == this.targetId) {
                    return 3;
                }
            } catch (Throwable ignored) {
            }
        }

        if (ageMs <= this.delayMs) {
            // mode 1 hold path also runs publish on newer sequence at accept time
            return 1;
        }

        // age > delay → mode 0 + reconcile publish (Slinky else branch)
        if (sequence > this.releasedSequence) {
            this.publish(packet, true, sequence);
            this.releasedSequence = sequence;
        }
        return 0;
    }

    /**
     * Slinky PacketPipeline.poll: release from head while mode != 1.
     */
    private void pollInbound() {
        long now = System.currentTimeMillis();
        while (!this.inbound.isEmpty()) {
            TimedPacket head = this.inbound.peekFirst();
            if (head == null) {
                break;
            }
            long age = now - head.receiveAt;
            int mode = this.classify(head.packet, age, head.sequence);
            if (mode == 1) {
                break; // keep holding head and everything behind
            }
            // mode 0 or 3 → deliver
            this.inbound.removeFirst();
            processPacket(head.packet);
        }
    }

    /**
     * Force-deliver entire queue (active ended / disable / world issues).
     * Equivalent to poll after active=false (all mode 0).
     * NEVER discard without process.
     */
    private void drainInbound() {
        while (!this.inbound.isEmpty()) {
            TimedPacket timed = this.inbound.pollFirst();
            if (timed == null) {
                continue;
            }
            // reconcile as packets leave after hold (age treated as past delay)
            if (timed.sequence > this.releasedSequence) {
                this.publish(timed.packet, true, timed.sequence);
                this.releasedSequence = timed.sequence;
            }
            processPacket(timed.packet);
        }
    }

    private void deactivateAndDrain() {
        this.active = false;
        this.render = false;
        this.drainInbound();
    }

    /**
     * Slinky publish(e, reconcile) with teleportSequence guard.
     */
    private void publish(Packet<?> packet, boolean reconcile, int packetSequence) {
        if (reconcile && this.teleportSequence > packetSequence) {
            return;
        }

        boolean relative = packet instanceof S14PacketEntity;
        if (!relative && !(packet instanceof S18PacketEntityTeleport)) {
            return;
        }

        S14PacketEntity move = relative ? (S14PacketEntity) packet : null;
        S18PacketEntityTeleport teleport = relative ? null : (S18PacketEntityTeleport) packet;

        int id;
        if (relative) {
            Entity ent = move.getEntity(mc.theWorld);
            if (ent == null) {
                return;
            }
            id = ent.getEntityId();
        } else {
            id = teleport.getEntityId();
        }
        if (id != this.targetId) {
            return;
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
                    return;
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
    }

    // ===================== Render = Slinky type 6 =====================
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

    // ===================== helpers =====================

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

    private void resetState() {
        this.active = this.absolute = this.hasYaw = this.render = false;
        this.targetId = this.selfId = this.interpolation = 0;
        this.cooldownUntilMs = 0L;
        this.delayedSequence = this.releasedSequence = this.teleportSequence = this.packetSequenceCounter = 0;
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
        } catch (Throwable t) {
            // Slinky uses fireChannelRead (no swallow). Log if you have a logger.
            t.printStackTrace();
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

    /** TimingAccess.position */
    private static Vec3 clientPos(Entity e) {
        return new Vec3(e.posX, e.posY, e.posZ);
    }

    /** TimingAccess.server */
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
    }
}
