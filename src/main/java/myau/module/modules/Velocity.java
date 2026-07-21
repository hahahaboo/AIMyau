package myau.module.modules;

import myau.Myau;
import myau.enums.DelayModules;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.mixin.IAccessorEntity;
import myau.module.Module;
import myau.module.Category;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.ChatUtil;
import myau.util.MoveUtil;
import myau.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import java.util.Random;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private boolean pendingExplosion = false;
    private boolean allowNext = true;
    private boolean delayActive = false;
    private boolean slot = false;
    private boolean attack = false;
    private boolean swing = false;
    private boolean block = false;
    private boolean inventory = false;
    private boolean dig = false;
    private int reduceTicks = 0;
    private final Random randomChance = new Random();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"VANILLA", "DELAY", "ATTACKREDUCE"});
    public final IntProperty delayTicks = new IntProperty("delay-ticks", 3, 1, 20, () -> this.mode.getValue() == 1);
    public final PercentProperty delayChance = new PercentProperty("delay-chance", 100, () -> this.mode.getValue() == 1);
    public final PercentProperty chance = new PercentProperty("chance", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty vertical = new PercentProperty("vertical", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100, () -> this.mode.getValue() == 0);
    public final BooleanProperty reduce = new BooleanProperty("Reduce", true, () -> this.mode.getValue() == 2);
    public final BooleanProperty reachCheck = new BooleanProperty("Reach-check", false, () -> this.mode.getValue() == 2);
    public final BooleanProperty tickExactEnable = new BooleanProperty("TickExact", true, () -> this.mode.getValue() == 2);
    public final IntProperty tick500 = new IntProperty("500", 3, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty tick1000 = new IntProperty("1000", 4, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty tick2000 = new IntProperty("2000", 4, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty tick3000 = new IntProperty("3000", 5, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty tick4000 = new IntProperty("4000", 6, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty tick5000 = new IntProperty("5000", 6, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty tick6000 = new IntProperty("6000", 7, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty tick7000 = new IntProperty("7000", 7, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty tick8000 = new IntProperty("8000", 8, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty tick9000 = new IntProperty("9000", 8, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty tick10000 = new IntProperty("10000", 9, 0, 20, () -> this.mode.getValue() == 2);
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);
    public final BooleanProperty debugLog = new BooleanProperty("debug-log", false);

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private boolean canDelay() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        return mc.thePlayer.onGround && (!killAura.isEnabled() || !killAura.shouldAutoBlock());
    }

    private int calculateTicks(int motionX, int motionZ) {
        double kb = Math.hypot(motionX, motionZ);
        if (!tickExactEnable.getValue()) {
            double ticks = 6.43153527E-4 * kb + 2.9419087136;
            int result = (int) Math.round(ticks);
            if (result < 1) result = 1;
            if (result > 10) result = 10;
            return result;
        }
        if (kb <= 500) return tick500.getValue();
        if (kb <= 1000) return tick1000.getValue();
        if (kb <= 2000) return tick2000.getValue();
        if (kb <= 3000) return tick3000.getValue();
        if (kb <= 4000) return tick4000.getValue();
        if (kb <= 5000) return tick5000.getValue();
        if (kb <= 6000) return tick6000.getValue();
        if (kb <= 7000) return tick7000.getValue();
        if (kb <= 8000) return tick8000.getValue();
        if (kb <= 9000) return tick9000.getValue();
        return tick10000.getValue();
    }

    private boolean badPackets() {
        return this.slot || this.attack || this.swing || this.block || this.inventory || this.dig;
    }

    private void resetBadPackets() {
        this.slot = false;
        this.swing = false;
        this.attack = false;
        this.block = false;
        this.inventory = false;
        this.dig = false;
    }

    public Velocity() {
        super("Velocity", "Reduces knockback", Category.COMBAT, 0, false, false);
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            this.pendingExplosion = false;
            this.allowNext = true;
            return;
        }

        if (!this.allowNext || !(Boolean) this.fakeCheck.getValue()) {
            this.allowNext = true;

            if (this.pendingExplosion) {
                this.pendingExplosion = false;
                if (this.mode.getValue() == 0) {  // 只在 VANILLA 模式生效
                    if (this.explosionHorizontal.getValue() > 0) {
                        event.setX(event.getX() * (double) this.explosionHorizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * (double) this.explosionHorizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (this.explosionVertical.getValue() > 0) {
                        event.setY(event.getY() * (double) this.explosionVertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                }
            } else {
                if (this.mode.getValue() == 0) {  // 只在 VANILLA 模式生效
                    boolean applyThisTime = this.randomChance.nextDouble() <= (double) this.chance.getValue() / 100.0;
                    if (applyThisTime) {
                        if (this.horizontal.getValue() > 0) {
                            event.setX(event.getX() * (double) this.horizontal.getValue() / 100.0);
                            event.setZ(event.getZ() * (double) this.horizontal.getValue() / 100.0);
                        } else {
                            event.setX(mc.thePlayer.motionX);
                            event.setZ(mc.thePlayer.motionZ);
                        }
                        if (this.vertical.getValue() > 0) {
                            event.setY(event.getY() * (double) this.vertical.getValue() / 100.0);
                        } else {
                            event.setY(mc.thePlayer.motionY);
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.POST) {
            if (this.delayActive && (
                    this.canDelay() ||
                    this.isInLiquidOrWeb() ||
                    Myau.delayManager.getDelay() >= (long) this.delayTicks.getValue()
            )) {
                if (this.debugLog.getValue()) {
                    ChatUtil.sendFormatted(
                        String.format("%sVelocity: delay %d ticks (tick: %d)",
                        Myau.clientName,
                        Myau.delayManager.getDelay(),
                        mc.thePlayer.ticksExisted
                        )
                    );
                }

                Myau.delayManager.setDelayState(false, DelayModules.VELOCITY);
                this.delayActive = false;

                if (this.debugLog.getValue()) {
                    ChatUtil.sendFormatted(
                        String.format("%sVelocity: delay ends (tick: %d)",
                        Myau.clientName,
                        mc.thePlayer.ticksExisted
                        )
                    );
                }
            }
        }

        if (this.mode.getValue() == 2 && this.reduce.getValue() && event.getType() == EventType.PRE) {
            if (this.reduceTicks > 0) {
                this.reduceTicks--;
                KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                if (killAura != null && killAura.isEnabled() 
                    && killAura.getTarget() != null 
                    && !killAura.isBlocking()) {
                        EntityLivingBase target = killAura.getTarget();
                        if(this.reachCheck.getValue() && RotationUtil.distanceToEntity(target) > killAura.attackRange.getValue()){
                            return;
                        }
                        if (!((IAccessorEntity) mc.thePlayer).getIsInWeb() 
                            && mc.thePlayer.isSprinting()
                            && MoveUtil.isMoving()
                            && target != mc.thePlayer
                            && !this.badPackets()) {
                                EventManager.call(new AttackEvent(target));
                                mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
                                mc.thePlayer.motionX *= 0.6;
                                mc.thePlayer.motionZ *= 0.6;
                                mc.thePlayer.setSprinting(false);
                                if (this.debugLog.getValue()) {
                                    ChatUtil.sendFormatted(Myau.clientName + "Attack reduce " + (this.reduceTicks + 1)  + " tick");
                                }
                        }
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            return;
        }

        if (this.mode.getValue() == 2 && event.getType() == EventType.SEND) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof C09PacketHeldItemChange) {
                this.slot = true;
            } else if (packet instanceof C0APacketAnimation) {
                this.swing = true;
            } else if (packet instanceof C02PacketUseEntity) {
                C02PacketUseEntity useEntity = (C02PacketUseEntity) packet;
                if (useEntity.getAction() == C02PacketUseEntity.Action.ATTACK) {
                    this.attack = true;
                }
            } else if (packet instanceof C08PacketPlayerBlockPlacement) {
                this.block = true;
            } else if (packet instanceof C07PacketPlayerDigging) {
                this.block = true;
                this.dig = true;
            } else if (packet instanceof C0DPacketCloseWindow ||
                    packet instanceof C0EPacketClickWindow ||
                    (packet instanceof C16PacketClientStatus &&
                    ((C16PacketClientStatus) packet).getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT)) {
                this.inventory = true;
            } else if (packet instanceof C03PacketPlayer) {
                this.resetBadPackets();
            }
        }

        if (event.getType() != EventType.RECEIVE) return;
        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                LongJump longJump = (LongJump) Myau.moduleManager.modules.get(LongJump.class);

                // DELAY 模式
                if (this.mode.getValue() == 1
                        && !this.delayActive
                        && !this.canDelay()
                        && !this.isInLiquidOrWeb()
                        && !this.pendingExplosion
                        && (!this.allowNext || !(Boolean) this.fakeCheck.getValue())
                        && (!longJump.isEnabled() || !longJump.canStartJump())) {

                    boolean applyDelayThisTime = this.randomChance.nextDouble() <= (double) this.delayChance.getValue() / 100.0;
                    if (applyDelayThisTime) {
                        Myau.delayManager.setDelayState(true, DelayModules.VELOCITY);
                        Myau.delayManager.delayedPacket.offer(packet);
                        event.setCancelled(true);
                        this.delayActive = true;
                        if (this.debugLog.getValue()) {
                            ChatUtil.sendFormatted(
                                String.format("%sVelocity: delay start (tick: %d)",
                                Myau.clientName,
                                mc.thePlayer.ticksExisted
                            )
                        );
                    }
                        return;
                    }
                }

                if (this.mode.getValue() == 2 && this.reduce.getValue()) {
                    this.reduceTicks = this.calculateTicks(packet.getMotionX(), packet.getMotionZ());
                }

                if (this.debugLog.getValue()) {
                    ChatUtil.sendFormatted(
                            String.format(
                                    "%sVelocity (tick: %d, x: %.2f, y: %.2f, z: %.2f)",
                                    Myau.clientName,
                                    mc.thePlayer.ticksExisted,
                                    (double) packet.getMotionX() / 8000.0,
                                    (double) packet.getMotionY() / 8000.0,
                                    (double) packet.getMotionZ() / 8000.0
                            )
                    );
                }
            }
        } 
        else if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                this.pendingExplosion = true;
                if (this.mode.getValue() == 0) {  // 只在 VANILLA 模式觸發
                    if (this.explosionHorizontal.getValue() == 0 || this.explosionVertical.getValue() == 0) {
                        event.setCancelled(true);
                    }
                }

                if (this.debugLog.getValue()) {
                    ChatUtil.sendFormatted(
                            String.format(
                                    "%sExplosion (tick: %d, x: %.2f, y: %.2f, z: %.2f)",
                                    Myau.clientName,
                                    mc.thePlayer.ticksExisted,
                                    mc.thePlayer.motionX + (double) packet.func_149149_c(),
                                    mc.thePlayer.motionY + (double) packet.func_149144_d(),
                                    mc.thePlayer.motionZ + (double) packet.func_149147_e()
                            )
                    );
                }
            }
        } 
        else if (event.getPacket() instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
            Entity entity = packet.getEntity(mc.theWorld);
            if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                this.allowNext = false;
            }
        }
    }

    @Override
    public void onEnabled() {
        reduceTicks = 0;
        this.resetBadPackets();
    }

    @Override
    public void onDisabled() {
        reduceTicks = 0;
        this.resetBadPackets();
    }
    
    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }
}
