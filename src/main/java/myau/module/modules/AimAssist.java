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
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty hSpeed = new FloatProperty("horizontal-speed", 3.0F, 0.0F, 10.0F);
    public final FloatProperty vSpeed = new FloatProperty("vertical-speed", 0.0F, 0.0F, 10.0F);
    public final FloatProperty hRandom = new FloatProperty("horizontal-random", 0.0F, 0.0F, 10.0F);
    public final FloatProperty vRandom = new FloatProperty("vertical-random", 0.0F, 0.0F, 10.0F);
    public final PercentProperty smoothing = new PercentProperty("smoothing", 50);
    public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 8.0F);
    public final FloatProperty aimPoint = new FloatProperty("aim-point", 0.0F, 0.0F, 1.0F);
    public final IntProperty fov = new IntProperty("fov", 90, 30, 360);
    public final ModeProperty sort = new ModeProperty("sort", 0, new String[]{"DISTANCE", "HEALTH", "HURT_TIME", "FOV"});
    public final BooleanProperty randomPitch = new BooleanProperty("random-pitch", false);
    public final IntProperty randomTicks = new IntProperty("random-ticks", 10, 1, 40, this.randomPitch::getValue);
    public final FloatProperty randomAngle = new FloatProperty("random-angle", 5.0F, 0.0F, 15.0F, this.randomPitch::getValue);
    public final BooleanProperty weaponOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponOnly::getValue);
    public final BooleanProperty botChecks = new BooleanProperty("bot-check", true);
    public final BooleanProperty team = new BooleanProperty("teams", true);
    
    private final TimerUtil timer = new TimerUtil();
    
    private float currentPitchOffset = 0.0f;
    private int tickCounter = 0;
    private int currentInterval = 0;
    
    public AimAssist() {
        super("AimAssist", "Auto Aim", Category.COMBAT, 0, false, false);
    }

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
                    angleVar = Math.max(0.5f, Math.min(15.0f, angleVar));
        
                    int sign = RandomUtil.nextInt(0, 1);
                    this.currentPitchOffset = (sign == 0 ? 1.0f : -1.0f) * angleVar;

                    int baseTicks = this.randomTicks.getValue();
                    int ticksVar = baseTicks + RandomUtil.nextInt(-2, 3);
        
                    this.currentInterval = Math.max(1, Math.min(40, ticksVar));
                    this.tickCounter = 0;
        
                    if (baseTicks == 1) {
                        this.currentInterval = 1;
                    }
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
                                .collect(Collectors.toList());

                        // 新增：根據 sort 設定進行排序（與 KillAura 完全相同邏輯）
                        if (!inRange.isEmpty()) {
                            inRange.sort((p1, p2) -> {
                                int sortBase = 0;
                                switch (this.sort.getValue()) {
                                    case 1: // HEALTH
                                        sortBase = Float.compare(TeamUtil.getHealthScore(p1), TeamUtil.getHealthScore(p2));
                                        break;
                                    case 2: // HURT_TIME
                                        sortBase = Integer.compare(p1.hurtResistantTime, p2.hurtResistantTime);
                                        break;
                                    case 3: // FOV
                                        sortBase = Float.compare(
                                                RotationUtil.angleToEntity(p1),
                                                RotationUtil.angleToEntity(p2)
                                        );
                                        break;
                                    // case 0: DISTANCE → 使用 distance 作為 fallback
                                }
                                return sortBase != 0 
                                        ? sortBase 
                                        : Double.compare(RotationUtil.distanceToEntity(p1), RotationUtil.distanceToEntity(p2));
                            });
                        }

                        if (!inRange.isEmpty()) {
                            if (inRange.stream().anyMatch(this::isInReach)) {
                                inRange.removeIf(entityPlayer -> !this.isInReach(entityPlayer));
                            }
                            EntityPlayer player = inRange.get(0);
                            if (!(RotationUtil.distanceToEntity(player) <= 0.0)) {
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

                                    float hRand = this.hRandom.getValue() / 2.0F;
                                    float vRand = this.vRandom.getValue() / 2.0F;

                                    float hBase = this.hSpeed.getValue();
                                    float hMin = Math.max(0.0F, hBase - hRand);
                                    float hMax = Math.min(10.0F, hBase + hRand);
                                    float yaw = RandomUtil.nextFloat(hMin, hMax);

                                    float vBase = this.vSpeed.getValue();
                                    float vMin = Math.max(0.0F, vBase - vRand);
                                    float vMax = Math.min(10.0F, vBase + vRand);
                                    float pitch = RandomUtil.nextFloat(vMin, vMax);

                                    float targetYaw = rotation[0];
                                    float targetPitch = rotation[1] + this.currentPitchOffset;

                                    float interpYaw = mc.thePlayer.rotationYaw + (targetYaw - mc.thePlayer.rotationYaw) * 0.1F * yaw;
                                    float interpPitch = mc.thePlayer.rotationPitch + (targetPitch - mc.thePlayer.rotationPitch) * 0.1F * pitch;

                                    float[] complied = RotationUtil.GCDfix(
                                            interpYaw,
                                            interpPitch,
                                            mc.thePlayer.rotationYaw,
                                            mc.thePlayer.rotationPitch
                                    );
                                    interpYaw = complied[0];
                                    interpPitch = complied[1];

                                    Myau.rotationManager
                                            .setRotation(
                                                    interpYaw,
                                                    interpPitch,
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

    @Override
    public String[] getSuffix() {
        float hRand = this.hRandom.getValue() / 2.0F;
        float hBase = this.hSpeed.getValue();
        float hMin = Math.max(0.0F, hBase - hRand);
        float hMax = Math.min(10.0F, hBase + hRand);
        
        if (Math.abs(hMin - hMax) < 0.01F) {  // 如果幾乎相等
            return new String[]{String.format("%.1f", hMin)};
        } else {
            return new String[]{String.format("%.1f-%.1f", hMin, hMax)};
        }
    }
}
