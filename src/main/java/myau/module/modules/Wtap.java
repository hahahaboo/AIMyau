package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.AttackEvent;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.ChatUtil;
import myau.util.RandomUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.util.MovingObjectPosition;

public class Wtap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty minDelay = new IntProperty("min-delay", 0, 0, 250);
    public final IntProperty maxDelay = new IntProperty("max-delay", 25, 0, 250);
    public final IntProperty minDuration = new IntProperty("min-duration", 85, 0, 250);
    public final IntProperty maxDuration = new IntProperty("max-duration", 115, 0, 250);
    public final BooleanProperty debugLog = new BooleanProperty("debug-log", false);
    
    private final TimerUtil timer = new TimerUtil();
    private boolean active = false;
    private boolean stopForward = false;
    private long delayTicks = 0L;
    private long durationTicks = 0L;
    private long initialDurationMs = 0L;   // 僅用於 debugLog
    private EntityLivingBase target;       // 用來從 AttackEvent 取得目標

    public Wtap() {
        super("WTap", "WTap", Category.COMBAT, 0, false, false);
    }

    private boolean canTrigger() {
        return !(mc.thePlayer.movementInput.moveForward < 0.8F)
                && !mc.thePlayer.isCollidedHorizontally
                && (!((float) mc.thePlayer.getFoodStats().getFoodLevel() <= 6.0F) || mc.thePlayer.capabilities.allowFlying) 
                && (mc.thePlayer.isSprinting()
                || !mc.thePlayer.isUsingItem() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.gameSettings.keyBindSprint.isKeyDown());
    }

    // 使用 AttackEvent 獲取 target（與 MoreKB 完全相同）
    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        net.minecraft.entity.Entity targetEntity = event.getTarget();
        if (targetEntity instanceof EntityLivingBase) {
            this.target = (EntityLivingBase) targetEntity;
        }
    }

    // 觸發條件：hurtTime == 10（與 MoreKB LEGIT 相同），已移除 cooldown
    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || this.active) {
            return;
        }

        EntityLivingBase entity = null;
        // 優先從 mouseOver 取得
        if (mc.objectMouseOver != null 
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY 
                && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
            entity = (EntityLivingBase) mc.objectMouseOver.entityHit;
        } 
        // fallback 使用 AttackEvent 的 target
        else if (this.target != null) {
            entity = this.target;
        }

        if (entity == null) {
            return;
        }

        // 僅在 hurtTime == 10 時觸發
        if (entity.hurtTime == 10) {
            this.timer.reset();                    // 重置 timer，避免過度觸發
            this.active = true;
            this.stopForward = false;
            this.delayTicks = RandomUtil.nextInt(this.minDelay.getValue(), this.maxDelay.getValue());
            this.durationTicks = RandomUtil.nextInt(this.minDuration.getValue(), this.maxDuration.getValue());
            this.initialDurationMs = this.durationTicks;

            if (this.debugLog.getValue()) {
                ChatUtil.sendFormatted(String.format("%sWTap triggered on hurtTime=10 (target: %s)", 
                        Myau.clientName, entity.getName()));
            }
        }

        this.target = null; // 清空，避免重複使用舊目標
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (this.active) {
            if (!this.stopForward && !this.canTrigger()) {
                this.active = false;
                while (this.delayTicks > 0L) {
                    this.delayTicks -= 50L;
                }
                while (this.durationTicks > 0L) {
                    this.durationTicks -= 50L;
                }
            } else if (this.delayTicks > 0L) {
                this.delayTicks -= 50L;
            } else {
                if (this.durationTicks > 0L) {
                    this.durationTicks -= 50L;
                    this.stopForward = true;
                    mc.thePlayer.movementInput.moveForward = 0.0F;
                }
                if (this.durationTicks <= 0L) {
                    if (this.debugLog.getValue() && this.initialDurationMs > 0L) {
                        ChatUtil.sendFormatted(
                                String.format("%sWTap: stopped movement for %d ms (tick: %d)",
                                        Myau.clientName,
                                        this.initialDurationMs,
                                        mc.thePlayer.ticksExisted
                                )
                        );
                    }
                    this.active = false;
                    this.initialDurationMs = 0L;
                }
            }
        }
    }
}
