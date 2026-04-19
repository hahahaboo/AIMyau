package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.util.RandomUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.potion.Potion;

public class Wtap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty minDelay = new IntProperty("min-delay", 25, 0, 250);
    public final IntProperty maxDelay = new IntProperty("max-delay", 50, 0, 250);
    public final IntProperty minDuration = new IntProperty("min-duration", 75, 0, 250);
    public final IntProperty maxDuration = new IntProperty("max-duration", 125, 0, 250);
    public final IntProperty minCooldown = new IntProperty("min-cooldown", 450, 0, 500);
    public final IntProperty maxCooldown = new IntProperty("max-cooldown", 500, 0, 500);
    private final TimerUtil timer = new TimerUtil();
    private boolean active = false;
    private boolean stopForward = false;
    private long delayTicks = 0L;
    private long durationTicks = 0L;
    private long nextCooldown = 0L;

    public Wtap() {
        super("WTap", "WTap", Category.COMBAT, 0, false, false);
    }

    private boolean canTrigger() {
        return !(mc.thePlayer.movementInput.moveForward < 0.8F)
                && !mc.thePlayer.isCollidedHorizontally
                && (!((float) mc.thePlayer.getFoodStats().getFoodLevel() <= 6.0F) || mc.thePlayer.capabilities.allowFlying) && (mc.thePlayer.isSprinting()
                || !mc.thePlayer.isUsingItem() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.gameSettings.keyBindSprint.isKeyDown());
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
                    this.active = false;
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled() && event.getType() == EventType.SEND) {
            if (event.getPacket() instanceof C02PacketUseEntity
                    && ((C02PacketUseEntity) event.getPacket()).getAction() == Action.ATTACK
                    && !this.active
                    && this.timer.hasTimeElapsed(this.nextCooldown)
                    && mc.thePlayer.isSprinting()) {
                this.timer.reset();
                this.active = true;
                this.stopForward = false;
                this.delayTicks = RandomUtil.nextInt(this.minDelay.getValue(), this.maxDelay.getValue());
                this.durationTicks = RandomUtil.nextInt(this.minDuration.getValue(), this.maxDuration.getValue());
                this.nextCooldown = RandomUtil.nextInt(this.minCooldown.getValue(), this.maxCooldown.getValue());
            }
        }
    }
}
