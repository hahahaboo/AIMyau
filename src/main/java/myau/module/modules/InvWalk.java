package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorC0DPacketCloseWindow;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.ModeProperty;
import myau.util.KeyBindUtil;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvWalk extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    // 改成從 0 開始編號，方便閱讀，也加入 UNSPRINT
    public final ModeProperty mode = new ModeProperty("mode", 0, 
            new String[]{"VANILLA", "BRAINSPOOF", "HYPIXEL", "UNSPRINT"});

    private final Queue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
    private boolean keysPressed = false;
    private C16PacketClientStatus pendingStatus = null;
    private int delayTicks = 0;

    // 用來記錄關閉視窗前 sprint 鍵是否被按著（用來恢復）
    private boolean wasSprintKeyDown = false;

    public InvWalk() {
        super("InvWalk", "Allows you to walk while in inventories.", Category.MOVEMENT, 0, false, false);
    }

    public void pressMovementKeys() {
        KeyBinding[] movementKeys = new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump,
                mc.gameSettings.keyBindSprint
        };
        for (KeyBinding keyBinding : movementKeys) {
            KeyBindUtil.updateKeyState(keyBinding.getKeyCode());
        }
        if (Myau.moduleManager.modules.get(Sprint.class).isEnabled()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
        this.keysPressed = true;
    }

    public boolean canInvWalk() {
        if (!(mc.currentScreen instanceof GuiContainer)) {
            return false;
        } else if (mc.currentScreen instanceof GuiContainerCreative) {
            return false;
        } else {
            switch (this.mode.getValue()) {
                case 0: // VANILLA
                    if (!(mc.currentScreen instanceof GuiInventory)) {
                        return false;
                    }
                    return this.pendingStatus != null && this.clickQueue.isEmpty();
                case 1: // BRAINSPOOF
                    return this.clickQueue.isEmpty();
                case 2: // HYPIXEL
                    return true;
                case 3: // UNSPRINT
                    return false; // UNSPRINT 模式不使用原本的 invwalk 按鍵邏輯
                default:
                    return false;
            }
        }
    }

    private boolean shouldUnsprint() {
        if (mode.getValue() != 3) return false;
        if (mc.currentScreen == null) return false;
        return mc.currentScreen instanceof GuiContainer
                && !(mc.currentScreen instanceof GuiContainerCreative);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            while (!this.clickQueue.isEmpty()) {
                PacketUtil.sendPacketNoEvent(this.clickQueue.poll());
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;

        if (mode.getValue() == 3) { // UNSPRINT 模式
            boolean nowShouldUnsprint = shouldUnsprint();

            if (nowShouldUnsprint) {
                // 記錄原本 sprint 鍵狀態，用來關閉時恢復
                wasSprintKeyDown = mc.gameSettings.keyBindSprint.getIsKeyDown();

                if (mc.thePlayer.isSprinting()) {
                    mc.thePlayer.setSprinting(false);
                    PacketUtil.sendPacketNoEvent(new C0BPacketEntityAction(
                            mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
                }
                // 強制放開 sprint 鍵
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            } 
            else {
                // 關閉視窗後，嘗試恢復 sprint
                if (wasSprintKeyDown || 
                    (Myau.moduleManager.getModule(Sprint.class) != null && 
                     Myau.moduleManager.getModule(Sprint.class).isEnabled())) {
                    
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
                    // 可選：強制讓玩家開始 sprint（視伺服器需求）
                    // mc.thePlayer.setSprinting(true);
                }
                wasSprintKeyDown = false;
            }
            
            // UNSPRINT 模式下不執行原本的 pressMovementKeys 邏輯
            return;
        }

        // 其他模式走原本邏輯
        if (this.canInvWalk() && this.delayTicks == 0) {
            this.pressMovementKeys();
        } else {
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
            if (this.delayTicks > 0) {
                this.delayTicks--;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND) return;

        // UNSPRINT 模式下不攔截 click/close packet（只控制 sprint）
        if (mode.getValue() == 3) return;

        // 以下是原本三種模式的 packet 處理邏輯
        if (event.getPacket() instanceof C16PacketClientStatus) {
            if (this.mode.getValue() == 0) { // VANILLA
                C16PacketClientStatus packet = (C16PacketClientStatus) event.getPacket();
                if (packet.getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                    event.setCancelled(true);
                    this.pendingStatus = packet;
                }
            }
        } 
        else if (event.getPacket() instanceof C0DPacketCloseWindow) {
            C0DPacketCloseWindow packet = (C0DPacketCloseWindow) event.getPacket();
            if (this.pendingStatus != null && ((IAccessorC0DPacketCloseWindow) packet).getWindowId() == 0) {
                this.pendingStatus = null;
                event.setCancelled(true);
            }
        } 
        else if (event.getPacket() instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow packet = (C0EPacketClickWindow) event.getPacket();
            switch (this.mode.getValue()) {
                case 0: // VANILLA
                    if (packet.getWindowId() == 0) {
                        if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                            event.setCancelled(true);
                            return;
                        }
                        if (this.pendingStatus != null) {
                            KeyBinding.unPressAllKeys();
                            event.setCancelled(true);
                            this.clickQueue.offer(packet);
                        }
                    }
                    break;
                case 1: // BRAINSPOOF
                    if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                        event.setCancelled(true);
                    } else {
                        KeyBinding.unPressAllKeys();
                        event.setCancelled(true);
                        this.clickQueue.offer(packet);
                        this.delayTicks = 8;
                    }
                    break;
                case 2: // HYPIXEL
                    // HYPIXEL 模式原本就 return true，不特別處理 click
                    break;
            }
            if (this.pendingStatus != null) {
                PacketUtil.sendPacketNoEvent(this.pendingStatus);
                this.pendingStatus = null;
            }
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
        this.delayTicks = 0;
        
        // 模組關閉時也恢復 sprint 鍵（保險）
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), 
                mc.gameSettings.keyBindSprint.getIsKeyDown());
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
