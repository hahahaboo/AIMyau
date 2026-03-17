package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.enums.DelayModules;
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
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // 通用計數器
    private int chanceCounter = 0;

    // VANILLA mode 專用
    private boolean pendingExplosion = false;
    private boolean allowNextKnockback = true;

    // JUMP mode 專用
    private boolean jumpFlag = false;

    // DELAY mode 專用
    private int delayChanceCounter = 0;
    private boolean delayActive = false;

    // REVERSE mode 專用
    private boolean reverseFlag = false;

    // LEGIT_TEST mode 專用
    private boolean shouldJump = false;
    private int jumpCooldown = 0;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"VANILLA", "JUMP", "DELAY", "REVERSE", "LEGIT_TEST"});

    // VANILLA 專屬設定
    public final PercentProperty chance = new PercentProperty("chance", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty vertical = new PercentProperty("vertical", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100, () -> this.mode.getValue() == 0);

    // DELAY 專屬設定
    public final IntProperty delayTicks = new IntProperty("delay-ticks", 3, 1, 20, () -> this.mode.getValue() == 2);
    public final PercentProperty delayChance = new PercentProperty("delay-chance", 100, () -> this.mode.getValue() == 2);

    // 通用設定
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);
    public final BooleanProperty debugLog = new BooleanProperty("debug-log", false);

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private boolean canDelay() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        return mc.thePlayer.onGround && (!killAura.isEnabled() || !killAura.shouldAutoBlock());
    }

    public Velocity() {
        super("Velocity", "Reduces knockback", Category.COMBAT, 0, false, false);
    }

    public void onEnable() {
        resetAllStates();
    }

    public void onDisable() {
        resetAllStates();
    }

    private void resetAllStates() {
        chanceCounter = 0;
        delayChanceCounter = 0;
        pendingExplosion = false;
        allowNextKnockback = true;
        jumpFlag = false;
        reverseFlag = false;
        delayActive = false;
        shouldJump = false;
        jumpCooldown = 0;

        Myau.delayManager.setDelayState(false, DelayModules.VELOCITY);
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) return;

        int currentMode = this.mode.getValue();

        if (currentMode == 0) { // VANILLA
            if (!allowNextKnockback || !(Boolean) fakeCheck.getValue()) {
                allowNextKnockback = true;

                if (pendingExplosion) {
                    pendingExplosion = false;
                    if (explosionHorizontal.getValue() > 0) {
                        event.setX(event.getX() * explosionHorizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * explosionHorizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (explosionVertical.getValue() > 0) {
                        event.setY(event.getY() * explosionVertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                } else {
                    chanceCounter = (chanceCounter + chance.getValue()) % 100;
                    if (chance.getValue() == 100 || chanceCounter < chance.getValue()) { // 修正機率判斷更直觀
                        if (horizontal.getValue() > 0) {
                            event.setX(event.getX() * horizontal.getValue() / 100.0);
                            event.setZ(event.getZ() * horizontal.getValue() / 100.0);
                        } else {
                            event.setX(mc.thePlayer.motionX);
                            event.setZ(mc.thePlayer.motionZ);
                        }
                        if (vertical.getValue() > 0) {
                            event.setY(event.getY() * vertical.getValue() / 100.0);
                        } else {
                            event.setY(mc.thePlayer.motionY);
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) return;

        int currentMode = this.mode.getValue();

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;

            if (currentMode == 2) { // DELAY
                if (!reverseFlag && canDelay() && !isInLiquidOrWeb() && !pendingExplosion &&
                        (!allowNextKnockback || !(Boolean) fakeCheck.getValue())) {

                    delayChanceCounter = (delayChanceCounter + delayChance.getValue()) % 100;
                    if (delayChance.getValue() == 100 || delayChanceCounter < delayChance.getValue()) {
                        Myau.delayManager.setDelayState(true, DelayModules.VELOCITY);
                        Myau.delayManager.delayedPacket.offer(packet);
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            if (debugLog.getValue()) {
                ChatUtil.sendFormatted(String.format(
                        "%sVelocity (&otick: %d, x: %.2f, y: %.2f, z: %.2f&r)&r",
                        Myau.clientName, mc.thePlayer.ticksExisted,
                        packet.getMotionX() / 8000.0, packet.getMotionY() / 8000.0, packet.getMotionZ() / 8000.0
                ));
            }
        } else if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            float mx = packet.func_149149_c(), my = packet.func_149144_d(), mz = packet.func_149147_e();
            if (mx == 0 && my == 0 && mz == 0) return;

            pendingExplosion = true;

            if (currentMode == 0 && (explosionHorizontal.getValue() == 0 || explosionVertical.getValue() == 0)) {
                event.setCancelled(true);
            }

            if (debugLog.getValue()) {
                ChatUtil.sendFormatted(String.format(
                        "%sExplosion (&otick: %d, x: %.2f, y: %.2f, z: %.2f&r)&r",
                        Myau.clientName, mc.thePlayer.ticksExisted,
                        mc.thePlayer.motionX + mx, mc.thePlayer.motionY + my, mc.thePlayer.motionZ + mz
                ));
            }
        } else if (event.getPacket() instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
            if (packet.getOpCode() == 2) {
                Entity entity = packet.getEntity(mc.theWorld);
                if (entity != null && entity == mc.thePlayer) {
                    allowNextKnockback = false;
                }
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.POST) return;

        int currentMode = this.mode.getValue();

        if (currentMode == 2 && reverseFlag) {
            if (canDelay() || isInLiquidOrWeb() || Myau.delayManager.getDelay() >= delayTicks.getValue()) {
                Myau.delayManager.setDelayState(false, DelayModules.VELOCITY);
                reverseFlag = false;
            }
        }

        if (currentMode == 2 && delayActive) {
            MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
            delayActive = false;
        }

        if (currentMode == 4) {
            int hurt = mc.thePlayer.hurtTime;
            if (hurt >= 8) {
                if (jumpCooldown <= 0) {
                    shouldJump = true;
                    jumpCooldown = 2;
                }
            } else if (hurt <= 1) {
                shouldJump = false;
                jumpCooldown = 0;
            }

            if (shouldJump && mc.thePlayer.onGround && jumpCooldown <= 0) {
                mc.thePlayer.jump();
                shouldJump = false;
            }

            if (jumpCooldown > 0) jumpCooldown--;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled()) return;

        int currentMode = this.mode.getValue();
        if (currentMode == 1 || currentMode == 2) {
            if (jumpFlag) {
                jumpFlag = false;
                if (mc.thePlayer.onGround && mc.thePlayer.isSprinting() &&
                        !mc.thePlayer.isPotionActive(Potion.jump) && !isInLiquidOrWeb()) {
                    mc.thePlayer.movementInput.jump = true;
                }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        resetAllStates();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString())};
    }
}
