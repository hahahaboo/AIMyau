package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.MovingObjectPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AntiFireball extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty range = new FloatProperty("range", 5.0F, 3.0F, 8.0F);
    public final IntProperty fov = new IntProperty("fov", 360, 1, 360);
    public final BooleanProperty rotations = new BooleanProperty("rotations", true);
    public final PercentProperty smoothing = new PercentProperty("smoothing", 0, this.rotations::getValue);
    public final IntProperty angleStep = new IntProperty("angle-step", 90, 30, 180, this.rotations::getValue);
    public final BooleanProperty smoothBack = new BooleanProperty("smooth-back", false, this.rotations::getValue);
    public final BooleanProperty throughWalls = new BooleanProperty("through-walls", true, this.rotations::getValue);
    public final BooleanProperty swing = new BooleanProperty("swing", true);
    public final ModeProperty moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT", "STRICT"});
    public final ModeProperty showTarget = new ModeProperty("show-target", 0, new String[]{"NONE", "DEFAULT", "HUD"});
    private final ArrayList<EntityFireball> farList = new ArrayList<>();
    private final ArrayList<EntityFireball> nearList = new ArrayList<>();
    private EntityFireball target = null;
    /** rotations 開啟時：raycast 已成功過一次，下一 tick 才允許攻擊 */
    private boolean rotationReady = false;

    public AntiFireball() {
        super("AntiFireball", "Fight back fireballs", Category.PLAYER, 0, false, false);
    }

    private boolean isValidTarget(EntityFireball entityFireball) {
        if (entityFireball.getEntityBoundingBox().hasNaN()) {
            return false;
        }
        if (RotationUtil.distanceToEntity(entityFireball) > (double) this.range.getValue() + 3.0) {
            return false;
        }
        if (RotationUtil.angleToEntity(entityFireball) > (float) this.fov.getValue()) {
            return false;
        }
        // rotations 開啟且不允許穿牆時，中間有方塊就視為無效
        if (this.rotations.getValue() && !this.throughWalls.getValue() && RotationUtil.rayTrace(entityFireball) != null) {
            return false;
        }
        return true;
    }

    private void doAttackAnimation() {
        if (this.swing.getValue()) {
            mc.thePlayer.swingItem();
        } else {
            PacketUtil.sendPacket(new C0APacketAnimation());
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            List<EntityFireball> fireballs = mc.theWorld
                    .loadedEntityList
                    .stream()
                    .filter(entity -> entity instanceof EntityFireball)
                    .map(entity -> (EntityFireball) entity)
                    .collect(Collectors.toList());
            this.farList.removeIf(entityFireball -> !fireballs.contains(entityFireball));
            this.nearList.removeIf(entityFireball -> !fireballs.contains(entityFireball));
            for (EntityFireball fireball : fireballs) {
                if (!this.farList.contains(fireball) && !this.nearList.contains(fireball)) {
                    if (RotationUtil.distanceToEntity(fireball) > 3.0) {
                        this.farList.add(fireball);
                    } else {
                        this.nearList.add(fireball);
                    }
                }
            }

            EntityFireball previous = this.target;
            if (mc.thePlayer.capabilities.allowFlying) {
                this.target = null;
            } else {
                this.target = this.farList.stream()
                        .filter(this::isValidTarget)
                        .min(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                        .orElse(null);
            }

            // 只在「有目標 → 沒有下一個目標」的那一瞬間呼叫一次
            if (previous != null && this.target == null) {
                this.rotationReady = false;
                if (this.rotations.getValue() && this.smoothBack.getValue()) {
                    Myau.rotationManager.startSnapback(this.smoothing.getValue(), this.angleStep.getValue());
                }
            }

            // 目標更換時重置 aim 狀態
            if (this.target != previous) {
                this.rotationReady = false;
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            EntityFireball fireball = this.target;
            if (TeamUtil.isEntityLoaded(fireball)) {
                // 計算旋轉（使用與 KillAura 相同的 angleStep + smoothing）
                float angleStepVal = this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F);
                float smoothingVal = this.smoothing.getValue() / 100.0F;
                float[] rotations = RotationUtil.getRotationsToBox(
                        this.target.getEntityBoundingBox(),
                        event.getYaw(),
                        event.getPitch(),
                        this.rotations.getValue() ? angleStepVal : 180.0F,
                        this.rotations.getValue() ? smoothingVal : 0.0F
                );

                if (this.rotations.getValue()
                        && !ItemUtil.isHoldingNonEmpty()
                        && !ItemUtil.isUsingBow()
                        && !ItemUtil.hasHoldItem()) {
                    event.setRotation(rotations[0], rotations[1], 0);
                    event.setPervRotation(this.moveFix.getValue() != 0 ? rotations[0] : mc.thePlayer.rotationYaw, 0);
                }

                if (!Myau.playerStateManager.attacking
                        && !Myau.playerStateManager.digging
                        && !Myau.playerStateManager.placing) {

                    this.doAttackAnimation();

                    // 距離檢查
                    if (RotationUtil.distanceToEntity(this.target) <= (double) this.range.getValue()) {
                        boolean canHit = true;

                        if (this.rotations.getValue()) {
                            float useYaw = event.isRotated() ? event.getNewYaw() : event.getYaw();
                            float usePitch = event.isRotated() ? event.getNewPitch() : event.getPitch();
                            MovingObjectPosition mop = RotationUtil.rayTrace(
                                    this.target.getEntityBoundingBox(),
                                    useYaw,
                                    usePitch,
                                    this.range.getValue()
                            );
                            boolean rayHit = mop != null;

                            if (rayHit) {
                                // 第一次 raycast 成功的 tick 只標記，下一 tick 才攻擊
                                if (this.rotationReady) {
                                    canHit = true;
                                } else {
                                    this.rotationReady = true;
                                    canHit = false;
                                }
                            } else {
                                this.rotationReady = false;
                                canHit = false;
                            }
                        } else {
                            this.rotationReady = false;
                        }

                        if (canHit) {
                            PacketUtil.sendPacket(new C02PacketUseEntity(this.target, Action.ATTACK));
                            PlayerUtil.attackEntity(this.target);
                        }
                    } else {
                        this.rotationReady = false;
                    }
                }
            } else {
                this.rotationReady = false;
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getValue() == 1
                    && RotationState.isActived()
                    && RotationState.getPriority() == 0.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled()) {
            if (this.showTarget.getValue() != 0 && TeamUtil.isEntityLoaded(this.target)) {
                Color color = new Color(-1);
                switch (this.showTarget.getValue()) {
                    case 1:
                        double dist = (this.target.posX - this.target.lastTickPosX) * (mc.thePlayer.posX - this.target.posX)
                                + (this.target.posY - this.target.lastTickPosY)
                                * (mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight() - this.target.posY - (double) this.target.height / 2.0)
                                + (this.target.posZ - this.target.lastTickPosZ) * (mc.thePlayer.posZ - this.target.posZ);
                        if (dist < 0.0) {
                            color = new Color(16733525);
                        } else {
                            color = new Color(5635925);
                        }
                        break;
                    case 2:
                        color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
                }
                RenderUtil.enableRenderState();
                RenderUtil.drawEntityBox(this.target, color.getRed(), color.getGreen(), color.getBlue());
                RenderUtil.disableRenderState();
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.farList.clear();
        this.nearList.clear();
        this.rotationReady = false;
    }
}
