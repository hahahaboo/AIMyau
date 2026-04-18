package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.MoveInputEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.module.Category;
import myau.util.ItemUtil;
import myau.util.MoveUtil;
import myau.util.PlayerUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;

import java.util.Objects;

public class Eagle extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int sneakDelay = 0;
    public final IntProperty minDelay = new IntProperty("min-delay", 2, 0, 10);
    public final IntProperty maxDelay = new IntProperty("max-delay", 3, 0, 10);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty jumpCheck = new BooleanProperty("jump-check", true);
    public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneaking-only", false);
    public final BooleanProperty sneakFix = new BooleanProperty("sneak-fix", false);

    private boolean sneakFixActive = false;
    private int sneakFixPlaceCount = 0;
    private boolean justFinishedSneakFix = false;

    private boolean canMoveSafely() {
        double[] offset = MoveUtil.predictMovement();
        return PlayerUtil.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
    }

    private boolean shouldSneak() {
        if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        } else if (this.jumpCheck.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
            return false;
        } else if (this.pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
            return false;
        } else if (sneakOnly.getValue() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            return false;
        } else {
            return (!this.blocksOnly.getValue() || ItemUtil.isHoldingBlock()) && mc.thePlayer.onGround;
        }
    }

    private boolean isDiagonalBridging() {
        if (mc.thePlayer == null) return false;

        if (!this.shouldSneak()) {
            return false;
        }

        float yaw = mc.thePlayer.rotationYaw % 360F;
        if (yaw < 0) yaw += 360F;
        float mod90 = yaw % 90F;
        // 符合您示意圖中的斜向搭橋（紅色斜箭頭所示的45度角）
        return mod90 >= 22.5F && mod90 <= 67.5F;
    }

    public Eagle() {
        super("Eagle", "Automatically sneaks at the edge of blocks.", Category.MOVEMENT, 0, false, false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.sneakDelay > 0) {
                this.sneakDelay--;
            }
            if (this.sneakDelay == 0 && this.canMoveSafely()) {
                this.sneakDelay = RandomUtils.nextInt(this.minDelay.getValue(), this.maxDelay.getValue() + 1);
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && mc.currentScreen == null) {

            if (sneakOnly.getValue() && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()) && shouldSneak()) {
                mc.thePlayer.movementInput.sneak = false;
                mc.thePlayer.movementInput.moveForward /= 0.3F;
                mc.thePlayer.movementInput.moveStrafe /= 0.3F;
            }

            boolean normalShouldSneak = this.shouldSneak() && (this.sneakDelay > 0 || this.canMoveSafely());

            if (sneakFix.getValue() && normalShouldSneak && isDiagonalBridging()) {
                if (!sneakFixActive) {
                    sneakFixActive = true;
                    sneakFixPlaceCount = 0;
                }
            }

            boolean fixActiveNow = sneakFix.getValue() && sneakFixActive && isDiagonalBridging();
            boolean doSneak = normalShouldSneak || fixActiveNow;

            // === 本次修正重點：剛完成 2 個方塊放置時，強制 unsneak（解決原本不會鬆開的問題）===
            if (justFinishedSneakFix) {
                doSneak = false;
                justFinishedSneakFix = false;
            }

            if (!mc.thePlayer.movementInput.sneak) {
                if (doSneak) {
                    mc.thePlayer.movementInput.sneak = true;
                    mc.thePlayer.movementInput.moveStrafe *= 0.3F;
                    mc.thePlayer.movementInput.moveForward *= 0.3F;
                }
            } else if (!doSneak) {
                // 當剛完成 sneak-fix 時，強制鬆開並還原移動速度（與 sneakOnly 的 unsneak 行為一致）
                mc.thePlayer.movementInput.sneak = false;
                mc.thePlayer.movementInput.moveStrafe /= 0.3F;
                mc.thePlayer.movementInput.moveForward /= 0.3F;
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || !sneakFix.getValue()) {
            return;
        }
        if (event.getPacket() instanceof C08PacketPlayerBlockPlacement && sneakFixActive && isDiagonalBridging()) {
            sneakFixPlaceCount++;
            if (sneakFixPlaceCount >= 2) {
                sneakFixActive = false;
                sneakFixPlaceCount = 0;
                justFinishedSneakFix = true;   // === 新增：觸發強制 unsneak ===
            }
        }
    }
    
    @Override
    public void onDisabled() {
        this.sneakDelay = 0;
        this.sneakFixActive = false;
        this.sneakFixPlaceCount = 0;
        this.justFinishedSneakFix = false;
    }

    @Override
    public void verifyValue(String name) {
        switch (name) {
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(this.minDelay.getValue(), this.maxDelay.getValue())
                ? new String[]{this.minDelay.getValue().toString()}
                : new String[]{String.format("%d-%d", this.minDelay.getValue(), this.maxDelay.getValue())};
    }
}
