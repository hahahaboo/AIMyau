package myau.module.modules;

import myau.Myau;
import myau.enums.FloatModules;
import myau.event.EventTarget;
import myau.events.LivingUpdateEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.RightClickMouseEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.BlockUtil;
import myau.util.ItemUtil;
import myau.util.PlayerUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

public class NoSlow
        extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty swordMode = new ModeProperty("Sword Mode", 2, new String[]{"NONE", "Vanilla", "Blink"});
    public final BooleanProperty onlyKillAuraAutoBlock = new BooleanProperty("OnlyKillAuraAutoBlock", false, () -> this.swordMode.getValue() != 0);
    public final PercentProperty swordMotion = new PercentProperty("Sword Motion", 100, () -> this.swordMode.getValue() != 0);
    public final BooleanProperty swordSprint = new BooleanProperty("Sword Sprint", true, () -> this.swordMode.getValue() != 0);
    public final IntProperty swordBlinkDelay = new IntProperty("Sword Blink Delay", 1, 1, 10, () -> this.swordMode.getValue() == 2);
    public final IntProperty swordBlinkDuration = new IntProperty("Sword Blink Duration", 2, 1, 5, () -> this.swordMode.getValue() == 2);
    public final ModeProperty foodMode = new ModeProperty("food-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT", "BLINK"});
    public final PercentProperty foodMotion = new PercentProperty("food-motion", 100, () -> this.foodMode.getValue() != 0);
    public final BooleanProperty foodSprint = new BooleanProperty("food-sprint", true, () -> this.foodMode.getValue() != 0);
    public final IntProperty foodBlinkDelay = new IntProperty("food-blink-delay", 2, 1, 10, () -> this.foodMode.getValue() == 3);
    public final IntProperty foodBlinkDuration = new IntProperty("food-blink-duration", 1, 1, 5, () -> this.foodMode.getValue() == 3);
    public final ModeProperty bowMode = new ModeProperty("bow-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT", "BLINK"});
    public final PercentProperty bowMotion = new PercentProperty("bow-motion", 100, () -> this.bowMode.getValue() != 0);
    public final BooleanProperty bowSprint = new BooleanProperty("bow-sprint", true, () -> this.bowMode.getValue() != 0);
    public final IntProperty bowBlinkDelay = new IntProperty("bow-blink-delay", 2, 1, 10, () -> this.bowMode.getValue() == 3);
    public final IntProperty bowBlinkDuration = new IntProperty("bow-blink-duration", 1, 1, 5, () -> this.bowMode.getValue() == 3);
    public final BooleanProperty successDetection = new BooleanProperty("success-detection", true, () -> this.swordMode.getValue() == 1 || this.swordMode.getValue() == 2);
    public final BooleanProperty successMessage = new BooleanProperty("success-message", true, this.successDetection::getValue);
    private int lastSlot = -1;
    private boolean noslowSuccess = false;
    private long lastCheckTime = 0L;
    private long lastBlockingTime = 0L;
    private int blinkTimer = 0;

    public NoSlow() {
        super("NoSlow", "Prevents you from getting slowed down by items", Category.MOVEMENT, 0, false, false);
    }

    public boolean isSwordActive() {
        return this.swordMode.getValue() != 0
                && ItemUtil.isHoldingSword()
                && (!this.onlyKillAuraAutoBlock.getValue() || this.isKillAuraAutoBlocking());
    }

    private boolean isKillAuraAutoBlocking() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) {
            return false;
        }
        if (!ItemUtil.isHoldingSword()) {
            return false;
        }
        int mode = killAura.autoBlock.getValue();
        if (mode == 0 || mode == 4) {
            return false;
        }
        return killAura.isBlocking() || killAura.isPlayerBlocking();
    }

    public boolean isFoodActive() {
        return this.foodMode.getValue() != 0 && ItemUtil.isEating();
    }

    public boolean isBowActive() {
        return this.bowMode.getValue() != 0 && ItemUtil.isUsingBow();
    }

    public boolean isFloatMode() {
        return this.foodMode.getValue() == 2 && ItemUtil.isEating() || this.bowMode.getValue() == 2 && ItemUtil.isUsingBow();
    }

    public boolean isBlinkMode() {
        return this.swordMode.getValue() == 2 && ItemUtil.isHoldingSword() || this.foodMode.getValue() == 3 && ItemUtil.isEating() || this.bowMode.getValue() == 3 && ItemUtil.isUsingBow();
    }

    public boolean isAnyActive() {
        return NoSlow.mc.thePlayer.isUsingItem() && (this.isSwordActive() || this.isFoodActive() || this.isBowActive());
    }

    public boolean canSprint() {
        return this.isSwordActive() && this.swordSprint.getValue() != false || this.isFoodActive() && this.foodSprint.getValue() != false || this.isBowActive() && this.bowSprint.getValue() != false;
    }

    public int getMotionMultiplier() {
        if (ItemUtil.isHoldingSword()) {
            return this.swordMotion.getValue();
        }
        if (ItemUtil.isEating()) {
            return this.foodMotion.getValue();
        }
        return ItemUtil.isUsingBow() ? this.bowMotion.getValue() : 100;
    }

    private boolean shouldBlink() {
        if (!this.isBlinkMode()) {
            return false;
        }
        ++this.blinkTimer;
        int delay = 2;
        int duration = 1;
        if (ItemUtil.isHoldingSword()) {
            delay = this.swordBlinkDelay.getValue();
            duration = this.swordBlinkDuration.getValue();
        } else if (ItemUtil.isEating()) {
            delay = this.foodBlinkDelay.getValue();
            duration = this.foodBlinkDuration.getValue();
        } else if (ItemUtil.isUsingBow()) {
            delay = this.bowBlinkDelay.getValue();
            duration = this.bowBlinkDuration.getValue();
        }
        int totalCycle = delay + duration;
        int currentPhase = this.blinkTimer % totalCycle;
        return currentPhase >= delay;
    }

    private void checkNoSlowSuccess() {
        boolean newSuccessState;
        if (!(this.isEnabled() && this.isSwordActive() && this.successDetection.getValue())) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastCheckTime < 500L) {
            return;
        }
        this.lastCheckTime = currentTime;
        boolean wasSprinting = NoSlow.mc.thePlayer.isSprinting();
        boolean isMoving = Math.abs(NoSlow.mc.thePlayer.movementInput.moveForward) > 0.1f || Math.abs(NoSlow.mc.thePlayer.movementInput.moveStrafe) > 0.1f;
        boolean bl = newSuccessState = wasSprinting && isMoving && PlayerUtil.isUsingItem();
        if (newSuccessState != this.noslowSuccess && this.successMessage.getValue()) {
            if (newSuccessState) {
                NoSlow.mc.thePlayer.addChatMessage(new ChatComponentText("§a[NoSlow] §fSuccess - Sword blocking without slowdown!"));
            } else {
                NoSlow.mc.thePlayer.addChatMessage(new ChatComponentText("§c[NoSlow] §fFailed - Normal sword blocking slowdown"));
            }
        }
        this.noslowSuccess = newSuccessState;
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        boolean isCurrentlyBlocking;
        if (!this.isEnabled()) {
            return;
        }
        boolean bl = isCurrentlyBlocking = this.isSwordActive() && PlayerUtil.isUsingItem();
        if (this.isBlinkMode() && this.shouldBlink()) {
            if (this.isSwordActive()) {
                NoSlow.mc.thePlayer.stopUsingItem();
            }
            return;
        }
        if (isCurrentlyBlocking) {
            this.lastBlockingTime = System.currentTimeMillis();
        }
        boolean inSprintProtection = System.currentTimeMillis() - this.lastBlockingTime < 300L;
        boolean playerWantsToSprint = NoSlow.mc.gameSettings.keyBindSprint.isKeyDown();
        if (this.isAnyActive() || inSprintProtection) {
            if (this.isSwordActive() || inSprintProtection) {
                this.checkNoSlowSuccess();
            }
            float multiplier = (float) this.getMotionMultiplier() / 100.0f;
            if (this.isAnyActive()) {
                NoSlow.mc.thePlayer.movementInput.moveForward *= multiplier;
                NoSlow.mc.thePlayer.movementInput.moveStrafe *= multiplier;
            }
            NoSlow.mc.thePlayer.setSprinting((this.canSprint() || inSprintProtection) && playerWantsToSprint && NoSlow.mc.thePlayer.movementInput.moveForward > 0.1f);
        } else {
        }
    }

    @EventTarget(value = 3)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (this.isEnabled() && this.isFloatMode()) {
            int item = NoSlow.mc.thePlayer.inventory.currentItem;
            if (this.lastSlot != item && PlayerUtil.isUsingItem()) {
                this.lastSlot = item;
                Myau.floatManager.setFloatState(true, FloatModules.NO_SLOW);
            }
        } else {
            this.lastSlot = -1;
            Myau.floatManager.setFloatState(false, FloatModules.NO_SLOW);
        }
        if (this.isSwordActive() && this.successDetection.getValue()) {
            this.checkNoSlowSuccess();
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            if (NoSlow.mc.objectMouseOver != null) {
                switch (NoSlow.mc.objectMouseOver.typeOfHit) {
                    case BLOCK: {
                        BlockPos blockPos = NoSlow.mc.objectMouseOver.getBlockPos();
                        if (!BlockUtil.isInteractable(blockPos) || PlayerUtil.isSneaking()) break;
                        return;
                    }
                    case ENTITY: {
                        Entity entityHit = NoSlow.mc.objectMouseOver.entityHit;
                        if (entityHit instanceof EntityVillager) {
                            return;
                        }
                        if (!(entityHit instanceof EntityLivingBase) || !TeamUtil.isShop((EntityLivingBase) entityHit))
                            break;
                        return;
                    }
                }
            }
            if (this.isFloatMode() && !Myau.floatManager.isPredicted() && NoSlow.mc.thePlayer.onGround) {
                event.setCancelled(true);
                NoSlow.mc.thePlayer.motionY = 0.42f;
            }
        }
    }

    @Override
    public void onEnabled() {
        this.blinkTimer = 0;
        this.noslowSuccess = false;
        this.lastCheckTime = 0L;
        this.lastBlockingTime = 0L;
    }

    @Override
    public void onDisabled() {
        this.blinkTimer = 0;
        this.noslowSuccess = false;
        this.lastCheckTime = 0L;
        this.lastBlockingTime = 0L;
        if (NoSlow.mc.thePlayer != null) {
            NoSlow.mc.thePlayer.stopUsingItem();
        }
    }
}
