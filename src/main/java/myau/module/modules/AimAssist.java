package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty hSpeed = new FloatProperty("horizontal-speed", 3.0F, 0.0F, 10.0F);
    public final FloatProperty vSpeed = new FloatProperty("vertical-speed", 0.0F, 0.0F, 10.0F);
    public final FloatProperty randomSpeed = new FloatProperty("random-speed", 0.0F, 0.0F, 10.0F);
    public final PercentProperty smoothing = new PercentProperty("smoothing", 50);
    public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 8.0F);
    public final FloatProperty aimPoint = new FloatProperty("aim-point", 0.0F, 0.0F, 1.0F);
    public final IntProperty fov = new IntProperty("fov", 90, 30, 360);
    public final BooleanProperty randomPitch = new BooleanProperty("random-pitch", false);
    public final IntProperty randomTicks = new IntProperty("random-ticks", 10, 1, 40, this.randomPitch::getValue);
    public final FloatProperty randomAngle = new FloatProperty("random-angle", 5.0F, 0.0F, 15.0F, this.randomPitch::getValue);
    public final BooleanProperty weaponOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponOnly::getValue);
    public final BooleanProperty noMouseMove = new BooleanProperty("no-mouse-move", false);
    public final BooleanProperty botChecks = new BooleanProperty("bot-check", true);
    public final BooleanProperty team = new BooleanProperty("teams", true);
    private final TimerUtil timer = new TimerUtil();
    
    public AimAssist() {
        super("AimAssist", "Auto Aim", Category.COMBAT, 0, false, false);
    }

    private float currentPitchOffset = 0.0f;
    private int tickCounter = 0;
    private int currentInterval = 0;
    
    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else if (RotationUtil.distanceToEntity(entityPlayer) > (double) this.range.getValue()) {
                return false;
            } else if (RotationUtil.angleToEntity(entityPlayer) > (float) this.fov.getValue()) {
                return false;
            } else if (RotationUtil.rayTrace(entityPlayer) != null) {
                return false;
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return false;
            } else {
                return (!this.team.getValue() || !TeamUtil.isSameTeam(entityPlayer)) && (!this.botChecks.getValue() || !TeamUtil.isBot(entityPlayer));
            }
        } else {
            return false;
        }
    }

    private boolean isInReach(EntityPlayer entityPlayer) {
        Reach reach = (Reach) Myau.moduleManager.modules.get(Reach.class);
        double distance = reach.isEnabled() ? (double) reach.range.getValue() : 3.0;
        return RotationUtil.distanceToEntity(entityPlayer) <= distance;
    }

    private boolean isLookingAtBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST && mc.currentScreen == null) {
            if (this.randomPitch.getValue()) {
            this.tickCounter++;
            if (this.currentInterval <= 0 || this.tickCounter >= this.currentInterval) {
                float angleVar = this.randomAngle.getValue() + RandomUtil.nextFloat(-1.0f, 1.0f);
                angleVar = Math.max(0.0f, Math.min(15.0f, angleVar));
                int sign = RandomUtil.nextInt(0, 1);
                this.currentPitchOffset = (sign == 0 ? 1.0f : -1.0f) * angleVar;

                int ticksVar = this.randomTicks.getValue() + RandomUtil.nextInt(-5, 5);
                this.currentInterval = Math.max(0, Math.min(40, ticksVar));
                this.tickCounter = 0;
            }
        }
            if (!(Boolean) this.weaponOnly.getValue()
                    || ItemUtil.hasRawUnbreakingEnchant()
                    || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
                boolean attacking = PlayerUtil.isAttacking();
                if (!attacking || !this.isLookingAtBlock()) {
                    if (attacking || !this.timer.hasTimeElapsed(350L)) {
                        List<EntityPlayer> inRange = mc.theWorld
                                .loadedEntityList
                                .stream()
                                .filter(entity -> entity instanceof EntityPlayer)
                                .map(entity -> (EntityPlayer) entity)
                                .filter(this::isValidTarget)
                                .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                                .collect(Collectors.toList());
                        if (!inRange.isEmpty()) {
                            if (inRange.stream().anyMatch(this::isInReach)) {
                                inRange.removeIf(entityPlayer -> !this.isInReach(entityPlayer));
                            }
                            EntityPlayer player = inRange.get(0);
                            if (!(RotationUtil.distanceToEntity(player) <= 0.0)) {
                                // 新增 deadzone 檢查（使用原有 angleToEntity 與 fov，確保與目標選擇邏輯一致）
                                float threshold = this.aimPoint.getValue() * 15.0F;
                                if (RotationUtil.angleToEntity(player) > threshold) {
                                    AxisAlignedBB axisAlignedBB = player.getEntityBoundingBox();
                                    double collisionBorderSize = player.getCollisionBorderSize();
                                    float[] rotation = RotationUtil.getRotationsToBox(
                                            axisAlignedBB.expand(collisionBorderSize, collisionBorderSize, collisionBorderSize),
                                            mc.thePlayer.rotationYaw,
                                            mc.thePlayer.rotationPitch,
                                            180.0F,
                                            (float) this.smoothing.getValue() / 100.0F
                                    );

                                    // Random speed 邏輯：Horizontal/Vertical speed 各自產生 min speed 與 max speed
                                    float rand = this.randomSpeed.getValue();

                                    // Horizontal
                                    float hBase = this.hSpeed.getValue();
                                    float hMin = Math.max(0.0F, hBase - rand);
                                    float hMax = Math.min(10.0F, hBase + rand);
                                    float yaw = RandomUtil.nextFloat(hMin, hMax);

                                    // Vertical
                                    float vBase = this.vSpeed.getValue();
                                    float vMin = Math.max(0.0F, vBase - rand);
                                    float vMax = Math.min(10.0F, vBase + rand);
                                    float pitch = RandomUtil.nextFloat(vMin, vMax);

                                    float targetYaw = rotation[0];
                                    float targetPitch = rotation[1] + this.currentPitchOffset;
                                    Myau.rotationManager
                                            .setRotation(
                                                    mc.thePlayer.rotationYaw + (targetYaw - mc.thePlayer.rotationYaw) * 0.1F * yaw,
                                                    mc.thePlayer.rotationPitch + (targetPitch - mc.thePlayer.rotationPitch) * 0.1F * pitch,
                                                    0,
                                                    false
                                            );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode() && !Myau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            this.timer.reset();
        }
    }
}
