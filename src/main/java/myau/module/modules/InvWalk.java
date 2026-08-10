package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorC0DPacketCloseWindow;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.KeyBindUtil;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvWalk extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"VANILLA", "UNSPRINT", "LEGIT"});
    public final IntProperty openDelay = new IntProperty("open-delay", 0, 0, 20, () -> this.mode.getValue() == 2);
    public final IntProperty closeDelay = new IntProperty("close-delay", 4, 0, 20, () -> this.mode.getValue() == 2);

    private boolean keysPressed = false;
    private final Queue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
    private C16PacketClientStatus pendingStatus = null;
    private int openDelayTicks = -1;
    private int closeDelayTicks = -1;

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

            // 強制取消 sprint
            mc.thePlayer.setSprinting(false);
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);

        } else {

            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());

            if (Myau.moduleManager.modules.get(Sprint.class).isEnabled()) {
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

        switch (this.mode.getValue()) {
            case 1: // UNSPRINT
                return true;
                
            case 2: // LEGIT
                if (!(mc.currentScreen instanceof GuiInventory)) {
                    return false;
                }
                return this.closeDelayTicks == -1 && this.clickQueue.isEmpty();

            default: // VANILLA
                return true;
        }
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
    public void onUpdate(UpdateEvent event) {

        if (this.isEnabled() && event.getType() == EventType.PRE) {

            if (this.canInvWalk()) {

                this.pressMovementKeys();

                // 確保 GUI 中不 sprint
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
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != 2 || event.getType() != EventType.PRE) return;

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
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != 2 || event.getType() != EventType.SEND) return;

        if (event.getPacket() instanceof C16PacketClientStatus) {
            C16PacketClientStatus packet = (C16PacketClientStatus) event.getPacket();
            if (packet.getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                event.setCancelled(true);
            }
        } else if (event.getPacket() instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow packet = (C0EPacketClickWindow) event.getPacket();
            if (packet.getWindowId() == 0) {
                if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                    event.setCancelled(true);
                    return;
                }
                KeyBinding.unPressAllKeys();
                event.setCancelled(true);
                this.clickQueue.offer(packet);
                if (this.closeDelayTicks < 0 && this.openDelayTicks < 0) {
                    this.pendingStatus = new C16PacketClientStatus(EnumState.OPEN_INVENTORY_ACHIEVEMENT);
                    this.openDelayTicks = this.openDelay.getValue();
                }
                this.closeDelayTicks = this.closeDelay.getValue();
            }
        } else if (event.getPacket() instanceof C0DPacketCloseWindow) {
            C0DPacketCloseWindow packet = (C0DPacketCloseWindow) event.getPacket();
            if (((IAccessorC0DPacketCloseWindow) packet).getWindowId() == 0) {
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
