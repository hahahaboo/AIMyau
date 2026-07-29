package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.module.Category;
import myau.util.BlockUtil;
import myau.util.ItemUtil;
import myau.util.MoveUtil;
import myau.util.PlayerUtil;
import myau.util.RotationUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;

import java.util.Objects;

public class Eagle extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int sneakDelay = 0;
    private int tellyDelayTicks = 0;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Sneak", "Telly"});

    public final IntProperty minDelay = new IntProperty("min-delay", 2, 0, 10);
    public final IntProperty maxDelay = new IntProperty("max-delay", 3, 0, 10);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty jumpCheck = new BooleanProperty("jump-check", true);
    public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true, () -> this.mode.getValue() == 0);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneaking-only", false, () -> this.mode.getValue() == 0);

    // --- Telly-only properties ---
    public final IntProperty tellyDelay = new IntProperty("telly-delay", 1, 0, 10, () -> this.mode.getValue() == 1);
    public final BooleanProperty tellySwing = new BooleanProperty("telly-swing", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty tellySilent = new BooleanProperty("telly-silent", true, () -> this.mode.getValue() == 1);

    private boolean canMoveSafely() {
        double[] offset = MoveUtil.predictMovement();
        return PlayerUtil.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
    }

    /**
     * 共用的邊緣觸發條件（Sneak 與 Telly 都會用到）
     */
    private boolean edgeConditionsMet() {
        if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        } else if (this.jumpCheck.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
            return false;
        } else if (this.pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
            return false;
        } else {
            return mc.thePlayer.onGround;
        }
    }

    private boolean shouldSneak() {
        if (!this.edgeConditionsMet()) {
            return false;
        } else if (sneakOnly.getValue() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            return false;
        } else {
            return !this.blocksOnly.getValue() || ItemUtil.isHoldingBlock();
        }
    }

    private boolean shouldTelly() {
        return this.edgeConditionsMet() && ItemUtil.isHoldingBlock();
    }

    public Eagle() {
        super("Eagle", "Automatically sneaks at the edge of blocks.", Category.MOVEMENT, 0, false, false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.mode.getValue() == 0) {
                if (this.sneakDelay > 0) {
                    this.sneakDelay--;
                }
                if (this.sneakDelay == 0 && this.canMoveSafely()) {
                    this.sneakDelay = RandomUtils.nextInt(this.minDelay.getValue(), this.maxDelay.getValue() + 1);
                }
            } else {
                if (this.tellyDelayTicks > 0) {
                    this.tellyDelayTicks--;
                }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && mc.currentScreen == null && this.mode.getValue() == 0) {

            if (sneakOnly.getValue() && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()) && shouldSneak()) {
                mc.thePlayer.movementInput.sneak = false;
                mc.thePlayer.movementInput.moveForward /= 0.3F;
                mc.thePlayer.movementInput.moveStrafe /= 0.3F;
            }

            if (!mc.thePlayer.movementInput.sneak) {
                if (this.shouldSneak() && (this.sneakDelay > 0 || this.canMoveSafely())) {
                    mc.thePlayer.movementInput.sneak = true;
                    mc.thePlayer.movementInput.moveStrafe *= 0.3F;
                    mc.thePlayer.movementInput.moveForward *= 0.3F;
                }
            }
        }
    }

    // --- Telly implementation ---

    /**
     * 找出腳下（玩家目前站立方塊的下一格）是否為空/可替換，若是就回傳該座標作為放置目標。
     */
    private BlockPos getTellyTarget() {
        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int y = MathHelper.floor_double(mc.thePlayer.posY) - 1;
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        BlockPos pos = new BlockPos(x, y, z);
        return BlockUtil.isReplaceable(pos) ? pos : null;
    }

    /**
     * 依序尋找 targetPos 周圍是否有現存的實體方塊可以當作放置支撐點。
     * 優先順序：UP（點擊腳下再下面那格的頂面，最自然的架橋方式）> 水平四面 > DOWN
     */
    private EnumFacing findTellyFacing(BlockPos targetPos) {
        EnumFacing[] preferOrder = {
                EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.DOWN
        };
        for (EnumFacing facing : preferOrder) {
            BlockPos supportPos = targetPos.offset(facing.getOpposite());
            if (!BlockUtil.isReplaceable(supportPos) && !BlockUtil.isInteractable(supportPos)) {
                return facing;
            }
        }
        return null;
    }

    private void handleTelly(UpdateEvent event) {
        if (this.tellyDelayTicks > 0 || !this.shouldTelly()) {
            return;
        }

        BlockPos targetPos = this.getTellyTarget();
        if (targetPos == null) {
            return;
        }

        EnumFacing facing = this.findTellyFacing(targetPos);
        if (facing == null) {
            return;
        }

        BlockPos supportPos = targetPos.offset(facing.getOpposite());
        Vec3 hitVec = BlockUtil.getClickVec(supportPos, facing);

        double dx = hitVec.xCoord - mc.thePlayer.posX;
        double dy = hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
        double dz = hitVec.zCoord - mc.thePlayer.posZ;
        float[] rotations = RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());

        event.setRotation(rotations[0], rotations[1], 2);
        event.setPervRotation(this.tellySilent.getValue() ? mc.thePlayer.rotationYaw : rotations[0], 2);

        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), supportPos, facing, hitVec)) {
            if (this.tellySwing.getValue()) {
                mc.thePlayer.swingItem();
            }
            this.tellyDelayTicks = this.tellyDelay.getValue();
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && this.mode.getValue() == 1 && event.getType() == EventType.PRE) {
            this.handleTelly(event);
        }
    }

    @Override
    public void onDisabled() {
        this.sneakDelay = 0;
        this.tellyDelayTicks = 0;
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
        if (this.mode.getValue() == 1) {
            return new String[]{this.mode.getModeString()};
        }
        return Objects.equals(this.minDelay.getValue(), this.maxDelay.getValue())
                ? new String[]{this.mode.getModeString() + " " + this.minDelay.getValue()}
                : new String[]{this.mode.getModeString() + " " + String.format("%d-%d", this.minDelay.getValue(), this.maxDelay.getValue())};
    }
}
