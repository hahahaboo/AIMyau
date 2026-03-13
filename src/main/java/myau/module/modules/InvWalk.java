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
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvWalk extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 1, 
            new String[]{"VANILLA", "BRAINSPOOF", "HYPIXEL", "UNSPRINT"});

    private final Queue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
    private boolean keysPressed = false;
    private C16PacketClientStatus pendingStatus = null;
    private int delayTicks = 0;

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

        // 決定是否要按 sprint 鍵
        boolean shouldSprint = (this.mode.getValue() != 4);

        if (shouldSprint) {
            Module sprintModule = Myau.moduleManager.getModule("Sprint");
            if (sprintModule != null && sprintModule.isEnabled()) {
                shouldSprint = true;
            } else {
                shouldSprint = false;
            }
        }

        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), shouldSprint);
        this.keysPressed = true;
    }

    public boolean canInvWalk() {
        if (!(mc.currentScreen instanceof GuiContainer)) {
            return false;
        }
        if (mc.currentScreen instanceof GuiContainerCreative) {
            return false;
        }

        int m = this.mode.getValue();

        if (m == 4) { // UNSPRINT
            return true;
        }

        switch (m) {
            case 1: // VANILLA
                if (!(mc.currentScreen instanceof GuiInventory)) {
                    return false;
                }
                return this.pendingStatus != null && this.clickQueue.isEmpty();
            case 2: // BRAINSPOOF
                return this.clickQueue.isEmpty();
            default: // HYPIXEL
                return true;
        }
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
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        boolean inContainer = (mc.currentScreen instanceof GuiContainer && !(mc.currentScreen instanceof GuiContainerCreative));

        if (this.canInvWalk() && this.delayTicks == 0) {
            this.pressMovementKeys();

            // UNSPRINT 模式：強制停止 sprint（每 tick 執行）
            if (this.mode.getValue() == 4 && mc.thePlayer != null) {
                mc.thePlayer.setSprinting(false);
                // 正確使用靜態方法強制把 sprint 鍵設為未按下
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            }
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

        // 額外保險：只要在容器畫面 + UNSPRINT 模式，就每 tick 強制關 sprint
        if (this.mode.getValue() == 4 && inContainer && mc.thePlayer != null) {
            mc.thePlayer.setSprinting(false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND) {
            return;
        }

        if (event.getPacket() instanceof C16PacketClientStatus) {
            if (this.mode.getValue() == 1) {
                C16PacketClientStatus packet = (C16PacketClientStatus) event.getPacket();
                if (packet.getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                    event.setCancelled(true);
                    this.pendingStatus = packet;
                }
            }
        } else if (event.getPacket() instanceof C0DPacketCloseWindow) {
            C0DPacketCloseWindow packet = (C0DPacketCloseWindow) event.getPacket();
            if (this.pendingStatus != null && ((IAccessorC0DPacketCloseWindow) packet).getWindowId() == 0) {
                this.pendingStatus = null;
                event.setCancelled(true);
            }
        } else if (event.getPacket() instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow packet = (C0EPacketClickWindow) event.getPacket();

            switch (this.mode.getValue()) {
                case 1: // VANILLA
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
                case 2: // BRAINSPOOF
                    if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                        event.setCancelled(true);
                    } else {
                        KeyBinding.unPressAllKeys();
                        event.setCancelled(true);
                        this.clickQueue.offer(packet);
                        this.delayTicks = 8;
                    }
                    break;
                case 4: // UNSPRINT - 不特別處理點擊封包
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

        // 關閉模組時重置 sprint 鍵狀態（可選）
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
