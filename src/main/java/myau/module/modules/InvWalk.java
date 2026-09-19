package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorC0DPacketCloseWindow;
import myau.mixin.IAccessorEntityLivingBase;
import myau.mixin.IAccessorKeyBinding;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.ui.impl.clickgui.normal.ClickGuiScreen;
import myau.util.KeyBindUtil;
import myau.util.MoveUtil;
import myau.util.PacketUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvWalk extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0,
            new String[]{"VANILLA", "UNSPRINT", "LEGIT", "WATCHDOG"});

    public final IntProperty openDelay = new IntProperty("open-delay", 0, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty closeDelay = new IntProperty("close-delay", 4, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty moveDelay = new IntProperty("move-delay", 4, 0, 20, () -> this.mode.getValue() == 2);

    // ===== Watchdog =====
    public final IntProperty ticks = new IntProperty("ticks", 1, 1, 20,
            () -> this.mode.getValue() == 3);
    public final BooleanProperty measureChestOpen = new BooleanProperty("measure-chest-open", true,
            () -> this.mode.getValue() == 3);

    private boolean keysPressed = false;
    private final Queue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
    private C16PacketClientStatus pendingStatus = null;
    private int openDelayTicks = -1;
    private int closeDelayTicks = -1;
    private int moveDelayTicks = 0;

    // ===== Watchdog state =====
    public static boolean inventoryClicking = false;
    public static boolean chestOpenConfirmed = false;

    private boolean inputDelayPassed = false;
    private long inputBlockStart = 0L;
    private int openSentTick = -1;
    private int openLatencyTicks = -1;
    private int chestOpenTick = -1;
    private BlockPos pendingChestPos;
    private boolean openPending = false;
    private boolean awaitingChestGui = false;
    private boolean sentWindowPacket = false;
    private int groundTicks = 0;
    private final Map<Entity, Boolean> everMoved = new WeakHashMap<>();

    public InvWalk() {
        super("InvWalk", "Allows you to walk while in inventories.", Category.MOVEMENT, 0, false, false);
    }

    public void pressMovementKeys() {
        KeyBinding[] movementKeys = new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump
        };

        for (KeyBinding keyBinding : movementKeys) {
            KeyBindUtil.updateKeyState(keyBinding.getKeyCode());
        }

        boolean unsprintMode = this.mode.getValue() == 1;

        if (unsprintMode && mc.currentScreen instanceof GuiContainer) {
            mc.thePlayer.setSprinting(false);
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        } else {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
            if (Myau.moduleManager.modules.get(Sprint.class) != null
                    && Myau.moduleManager.modules.get(Sprint.class).isEnabled()) {
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
            }
        }

        this.keysPressed = true;
    }

    public boolean canInvWalk() {
        if (!(mc.currentScreen instanceof GuiContainer)) {
            return false;
        }
        if (mc.currentScreen instanceof GuiContainerCreative) {
            return false;
        }
        if (!this.screenAllowsMovement()) {
            return false;
        }

        switch (this.mode.getValue()) {
            case 1: // UNSPRINT
                return true;
            case 2: // LEGIT
                if (!(mc.currentScreen instanceof GuiInventory)) {
                    return false;
                }
                return this.closeDelayTicks == -1 && this.moveDelayTicks == 0 && this.clickQueue.isEmpty();
            case 3: // WATCHDOG
                if (mc.currentScreen instanceof GuiInventory || mc.currentScreen instanceof GuiChest) {
                    return ture;
                } else {
                    return false;
                }
            default: // VANILLA
                return true;
        }
    }

    private boolean screenAllowsMovement() {
        return !(mc.currentScreen instanceof GuiChat)
                && !(mc.currentScreen instanceof ClickGuiScreen);
    }

    private boolean temporaryStackIsEmpty() {
        if (mc.thePlayer.inventory.getItemStack() != null) return false;
        if (mc.thePlayer.inventoryContainer instanceof ContainerPlayer) {
            ContainerPlayer containerPlayer = (ContainerPlayer) mc.thePlayer.inventoryContainer;
            for (int i = 0; i < containerPlayer.craftMatrix.getSizeInventory(); i++) {
                if (containerPlayer.craftMatrix.getStackInSlot(i) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.everMoved.clear();
        this.resetOpenMeasurement();
        inventoryClicking = false;
        chestOpenConfirmed = false;
        this.inputDelayPassed = false;
        this.inputBlockStart = 0L;
        this.groundTicks = 0;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        if (this.mode.getValue() == 3) {
            this.watchdogTick();
            return;
        }

        if (this.canInvWalk()) {
            this.pressMovementKeys();
            if (this.mode.getValue() == 1 && mc.currentScreen instanceof GuiContainer) {
                mc.thePlayer.setSprinting(false);
            }
        } else {
            if (this.keysPressed) {
                if (mc.currentScreen != null) {
                    KeyBinding.unPressAllKeys();
                }
                this.keysPressed = false;
            }
        }
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != 3 || mc.thePlayer == null) {
            return;
        }
        if (mc.currentScreen != null && this.canInvWalk()) {
            this.pressMovementKeys();
        }
        this.watchdogMotion();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }

        if (mc.thePlayer.onGround) {
            this.groundTicks++;
        } else {
            this.groundTicks = 0;
        }

        if (this.mode.getValue() == 3) {
            this.trackMovedEntities();
            return;
        }

        // LEGIT
        if (this.mode.getValue() != 2) return;

        if (this.moveDelayTicks > 0) {
            this.moveDelayTicks--;
        }

        if (this.openDelayTicks >= 0) {
            this.openDelayTicks--;
            return;
        }
        while (!this.clickQueue.isEmpty()) {
            PacketUtil.sendPacketNoEvent(this.clickQueue.poll());
        }
        if (this.closeDelayTicks > 0) {
            if (this.temporaryStackIsEmpty()) {
                this.closeDelayTicks--;
            }
        } else if (this.closeDelayTicks == 0) {
            if (mc.currentScreen instanceof GuiInventory) {
                PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
            }
            this.closeDelayTicks = -1;
            this.moveDelayTicks = this.moveDelay.getValue();
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.thePlayer.movementInput == null) {
            return;
        }
        if (this.mode.getValue() != 3 || !this.canInvWalk()) {
            return;
        }

        boolean inChest = mc.currentScreen instanceof GuiChest;
        if (chestOpenConfirmed && mc.thePlayer.ticksExisted % 5 != 0 && inChest) {
            this.zeroInput();
        }
        if (!inChest) {
            chestOpenConfirmed = false;
        }

        boolean containerBusy = inChest || inventoryClicking;
        if (containerBusy && (mc.thePlayer.isPotionActive(Potion.moveSpeed) || !mc.thePlayer.onGround)) {
            this.zeroInput();
        } else if (inventoryClicking && !this.inputDelayPassed) {
            this.zeroInput();
            if (this.inputBlockStart == 0L) {
                this.inputBlockStart = System.currentTimeMillis();
            }
        } else {
            this.inputBlockStart = 0L;
        }
        if (this.inputBlockStart != 0L
                && System.currentTimeMillis() - this.inputBlockStart >= 60L) {
            this.inputDelayPassed = true;
            this.inputBlockStart = 0L;
        }
        if (!containerBusy) {
            this.inputDelayPassed = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND) {
            return;
        }

        Packet<?> packet = event.getPacket();

        // ===== Watchdog =====
        if (this.mode.getValue() == 3) {
            this.trackWindowPacket(packet);
            this.watchdogPacket(event, packet);
            return;
        }

        // ===== LEGIT =====
        if (this.mode.getValue() != 2) return;

        if (packet instanceof C16PacketClientStatus) {
            C16PacketClientStatus status = (C16PacketClientStatus) packet;
            if (status.getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                event.setCancelled(true);
            }
        } else if (packet instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow click = (C0EPacketClickWindow) packet;
            if (click.getWindowId() == 0) {
                if ((click.getMode() == 3 || click.getMode() == 4) && click.getSlotId() == -999) {
                    event.setCancelled(true);
                    return;
                }
                KeyBinding.unPressAllKeys();
                event.setCancelled(true);
                this.clickQueue.offer(click);
                if (this.closeDelayTicks < 0 && this.openDelayTicks < 0) {
                    this.pendingStatus = new C16PacketClientStatus(EnumState.OPEN_INVENTORY_ACHIEVEMENT);
                    this.openDelayTicks = this.openDelay.getValue();
                }
                this.closeDelayTicks = this.closeDelay.getValue();
            }
        } else if (packet instanceof C0DPacketCloseWindow) {
            C0DPacketCloseWindow close = (C0DPacketCloseWindow) packet;
            if (((IAccessorC0DPacketCloseWindow) close).getWindowId() == 0) {
                if (!this.clickQueue.isEmpty()) {
                    this.clickQueue.clear();
                }
                if (this.openDelayTicks >= 0) {
                    this.openDelayTicks = -1;
                }
                if (this.closeDelayTicks >= 0) {
                    this.closeDelayTicks = -1;
                } else {
                    event.setCancelled(true);
                }
            }
        }
        if (this.pendingStatus != null) {
            PacketUtil.sendPacketNoEvent(this.pendingStatus);
            this.pendingStatus = null;
        }
    }

    // ==================== Watchdog 核心 ====================

    private void trackWindowPacket(Packet<?> packet) {
        if (packet instanceof C03PacketPlayer) {
            this.sentWindowPacket = false;
            return;
        }
        if (packet instanceof C0EPacketClickWindow
                || packet instanceof C0DPacketCloseWindow
                || (packet instanceof C16PacketClientStatus
                && ((C16PacketClientStatus) packet).getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT)) {
            this.sentWindowPacket = true;
        }
    }

    private void watchdogPacket(PacketEvent event, Packet<?> packet) {
        if (!event.isCancelled() && mc.thePlayer != null && mc.theWorld != null
                && this.measureChestOpen.getValue()) {
            if (packet instanceof C08PacketPlayerBlockPlacement) {
                BlockPos position = ((C08PacketPlayerBlockPlacement) packet).getPosition();
                if (!(mc.currentScreen instanceof GuiChest) && this.isChest(position)) {
                    this.pendingChestPos = position;
                    this.openPending = true;
                }
            } else if (packet instanceof C02PacketUseEntity) {
                C02PacketUseEntity use = (C02PacketUseEntity) packet;
                Entity entity = use.getEntityFromWorld(mc.theWorld);
                if (!(mc.currentScreen instanceof GuiChest)
                        && use.getAction() != C02PacketUseEntity.Action.ATTACK
                        && this.isNpcEntity(entity)) {
                    this.pendingChestPos = null;
                    this.openPending = true;
                }
            }
        }
        if (packet instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow click = (C0EPacketClickWindow) packet;
            if (mc.currentScreen instanceof GuiInventory && click.getMode() < 1
                    && click.getClickedItem() != null) {
                inventoryClicking = true;
            }
        }
    }

    private void trackMovedEntities() {
        if (mc.theWorld == null) return;
        for (Object raw : mc.theWorld.loadedEntityList) {
            if (!(raw instanceof Entity)) continue;
            Entity entity = (Entity) raw;
            if (Boolean.TRUE.equals(this.everMoved.get(entity))) continue;

            boolean movedThisTick = entity.getPositionVector().distanceTo(
                    new Vec3(entity.lastTickPosX, entity.lastTickPosY, entity.lastTickPosZ)) > 0.1
                    || (entity.ticksExisted > 10
                    && (Math.abs(entity.prevPosX - entity.posX) > 0.2
                    || Math.abs(entity.prevPosY - entity.posY) > 0.2
                    || Math.abs(entity.prevPosZ - entity.posZ) > 0.2));
            if (movedThisTick) {
                this.everMoved.put(entity, Boolean.TRUE);
            }
        }
    }

    private void watchdogMotion() {
        if (this.mode.getValue() != 3 || !this.canInvWalk()) {
            return;
        }

        if (!(mc.currentScreen instanceof GuiInventory)) {
            inventoryClicking = false;
        }
        if (mc.currentScreen instanceof GuiInventory && !inventoryClicking && !this.sentWindowPacket) {
            PacketUtil.sendPacketNoEvent(
                    new C0DPacketCloseWindow(mc.thePlayer.inventoryContainer.windowId));
        }

        boolean containerBusy = mc.currentScreen instanceof GuiChest || inventoryClicking;

        if (containerBusy) {
            mc.thePlayer.setSprinting(false);
            if (!mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
                ((IAccessorKeyBinding) mc.gameSettings.keyBindSprint).setPressed(false);
                ((IAccessorKeyBinding) mc.gameSettings.keyBindJump).setPressed(false);
            }
        }
    }

    private void watchdogTick() {
        if (mc.theWorld == null || mc.thePlayer.ticksExisted < 50) return;
        this.beginOpenMeasurement();
        this.finishOpenMeasurement();
        this.updateChestOpenState();
    }

    private void beginOpenMeasurement() {
        if (!this.openPending) return;
        this.openSentTick = mc.thePlayer.ticksExisted;
        this.openPending = false;
        this.awaitingChestGui = true;
    }

    private void finishOpenMeasurement() {
        if (!this.awaitingChestGui) return;
        if (mc.currentScreen instanceof GuiChest) {
            this.openLatencyTicks = mc.thePlayer.ticksExisted - this.openSentTick;
            if (this.chestOpenTick == -1) {
                this.chestOpenTick = mc.thePlayer.ticksExisted;
            }
            this.awaitingChestGui = false;
            return;
        }
        if (mc.thePlayer.ticksExisted - this.openSentTick > 40) {
            this.awaitingChestGui = false;
        }
    }

    private void updateChestOpenState() {
        if (!(mc.currentScreen instanceof GuiChest)) {
            this.chestOpenTick = -1;
            chestOpenConfirmed = false;
            return;
        }
        if (this.chestOpenTick == -1) {
            this.chestOpenTick = mc.thePlayer.ticksExisted;
        }
        if (!chestOpenConfirmed && this.openLatencyTicks >= 0
                && mc.thePlayer.ticksExisted - this.chestOpenTick
                >= this.openLatencyTicks - this.ticks.getValue()) {
            chestOpenConfirmed = true;
        }
    }

    private void resetOpenMeasurement() {
        this.openSentTick = -1;
        this.openLatencyTicks = -1;
        this.chestOpenTick = -1;
        this.pendingChestPos = null;
        this.openPending = false;
        this.awaitingChestGui = false;
    }

    private boolean isChest(BlockPos position) {
        if (position == null || mc.theWorld == null || position.equals(new BlockPos(-1, -1, -1))) {
            return false;
        }
        Block block = mc.theWorld.getBlockState(position).getBlock();
        return block == Blocks.chest || block == Blocks.trapped_chest || block == Blocks.ender_chest;
    }

    private boolean isNpcEntity(Entity entity) {
        if (entity == null || entity == mc.thePlayer) return false;
        if (entity instanceof EntityPlayer) {
            return mc.getNetHandler() != null
                    && mc.getNetHandler().getPlayerInfo(((EntityPlayer) entity).getUniqueID()) == null;
        }
        if (Boolean.TRUE.equals(this.everMoved.get(entity))) return false;
        return this.isStationary(entity);
    }

    private boolean isStationary(Entity entity) {
        return Math.abs(entity.posX - entity.lastTickPosX) < 0.03
                && Math.abs(entity.posY - entity.lastTickPosY) < 0.03
                && Math.abs(entity.posZ - entity.lastTickPosZ) < 0.03
                && Math.abs(entity.motionX) < 0.03
                && Math.abs(entity.motionY) < 0.03
                && Math.abs(entity.motionZ) < 0.03;
    }

    private void zeroInput() {
        mc.thePlayer.movementInput.moveForward = 0.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
    }

    private boolean isMoving() {
        return mc.thePlayer.movementInput != null
                && (mc.thePlayer.movementInput.moveForward != 0.0F
                || mc.thePlayer.movementInput.moveStrafe != 0.0F);
    }

    private void strafe(double speed) {
        if (this.isMoving()) {
            MoveUtil.setSpeed(speed, MoveUtil.getMoveYaw());
        }
    }

    private void stop() {
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionZ = 0.0;
    }

    private int countHeldDirectionKeys() {
        int held = 0;
        KeyBinding[] keys = new KeyBinding[]{
                mc.gameSettings.keyBindForward, mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindBack, mc.gameSettings.keyBindLeft};
        for (KeyBinding key : keys) {
            if (key.isKeyDown()) held++;
        }
        return held;
    }

    private void preventDiagonalSpeed() {
        if (this.countHeldDirectionKeys() == 1 || !this.isMoving()) return;
        double correction = mc.thePlayer.onGround
                ? 0.0026000750109401644 : 5.199896488849598E-4;
        MoveUtil.addSpeed(-correction, MoveUtil.getMoveYaw());
    }

    @Override
    public void onDisabled() {
        if (this.keysPressed) {
            if (mc.currentScreen != null) {
                KeyBinding.unPressAllKeys();
            }
            this.keysPressed = false;
        }
        if (this.pendingStatus != null) {
            PacketUtil.sendPacketNoEvent(this.pendingStatus);
            this.pendingStatus = null;
        }
        this.clickQueue.clear();
        this.openDelayTicks = -1;
        this.closeDelayTicks = -1;
        this.moveDelayTicks = 0;

        // Watchdog cleanup
        inventoryClicking = false;
        chestOpenConfirmed = false;
        this.inputDelayPassed = false;
        this.inputBlockStart = 0L;
        this.groundTicks = 0;
        this.everMoved.clear();
        this.resetOpenMeasurement();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{
                CaseFormat.UPPER_UNDERSCORE.to(
                        CaseFormat.UPPER_CAMEL,
                        this.mode.getModeString())
        };
    }
}
