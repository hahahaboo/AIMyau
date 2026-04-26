package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.AttackEvent;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.ChatUtil;
import myau.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;

public class Wtap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty minDelay = new IntProperty("min-delay", 0, 0, 250);
    public final IntProperty maxDelay = new IntProperty("max-delay", 25, 0, 250);
    public final IntProperty minDuration = new IntProperty("min-duration", 85, 0, 250);
    public final IntProperty maxDuration = new IntProperty("max-duration", 115, 0, 250);
    public final BooleanProperty debugLog = new BooleanProperty("debug-log", false);

    private boolean active = false;
    private boolean stopForward = false;
    private long delayTicks = 0L;
    private long durationTicks = 0L;
    private long initialDurationMs = 0L;
    private EntityLivingBase target;   // 新增：用來儲存 AttackEvent 的目標，與 MoreKB 相同

    public Wtap() {
        super("WTap", "WTap", Category.COMBAT, 0, false, false);
    }

    private boolean canTrigger() {
        return !(mc.thePlayer.movementInput.moveForward < 0.8F)
                && !mc.thePlayer.isCollidedHorizontally
                && (!((float) mc.thePlayer.getFoodStats().getFoodLevel() <= 6.0F) || mc.thePlayer.capabilities.allowFlying) && (mc.thePlayer.isSprinting()
                || !mc.thePlayer.isUsingItem() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.gameSettings.keyBindSprint.isKeyDown());
    }

    // 新增：與 MoreKB 完全相同的 AttackEvent 處理
    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        Entity targetEntity = event.getTarget();
        if (targetEntity instanceof EntityLivingBase) {
            this.target = (EntityLivingBase) targetEntity;
        }
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

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled() && event.getType() == EventType.SEND) {
            // 新的觸發條件：當目標 hurtTime == 10 時觸發（與 MoreKB 完全相同）
            if (this.target != null && this.target.hurtTime == 10 && !this.active && mc.thePlayer.isSprinting()) {
                this.active = true;
                this.stopForward = false;
                this.delayTicks = RandomUtil.nextInt(this.minDelay.getValue(), this.maxDelay.getValue());
                this.durationTicks = RandomUtil.nextInt(this.minDuration.getValue(), this.maxDuration.getValue());
                this.initialDurationMs = this.durationTicks;
                this.target = null;   // 避免重複觸發
            }
        }
    }
}
