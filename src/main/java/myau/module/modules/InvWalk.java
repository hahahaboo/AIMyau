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

        int sprintKeyCode = mc.gameSettings.keyBindSprint.getKeyCode();
        boolean shouldSprint = (this.mode.getValue() != 4);

        if (shouldSprint) {
            Module sprintMod = Myau.moduleManager.getModule("Sprint");
            if (sprintMod != null && sprintMod.isEnabled()) {
                KeyBindUtil.setKeyBindState(sprintKeyCode, true);
            } else {
                KeyBindUtil.setKeyBindState(sprintKeyCode, false);
            }
        } else {
            // UNSPRINT: 強制不 sprint
            KeyBindUtil.setKeyBindState(sprintKeyCode, false);
            KeyBinding.setKeyBindState(sprintKeyCode, false);
        }

        this.keysPressed = true;
    }

    public boolean canInvWalk() {
        if (!(mc.currentScreen instanceof GuiContainer) || mc.currentScreen instanceof GuiContainerCreative) {
            return false;
        }

        int m = this.mode.getValue();

        if (m == 4) {
            return true;
        }

        switch (m) {
            case 1:
                if (!(mc.currentScreen instanceof GuiInventory)) return false;
                return this.pendingStatus != null && this.clickQueue.isEmpty();
            case 2:
                return this.clickQueue.isEmpty();
            default:
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
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        boolean isInGui = mc.currentScreen instanceof GuiContainer && !(mc.currentScreen instanceof GuiContainerCreative);

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

        // 核心：UNSPRINT 模式 + 在任何 GUI 時，強制覆蓋 sprint 狀態（對抗 Sprint 模組的每 tick set true）
        if (this.mode.getValue() == 4 && isInGui && mc.thePlayer != null) {
            // 1. 客戶端強制關 sprint
            mc.thePlayer.setSprinting(false);

            // 2. 強制把 sprint 鍵「鬆開」狀態（覆蓋 Sprint 模組的 set true）
            int sprintCode = mc.gameSettings.keyBindSprint.getKeyCode();
            KeyBindUtil.setKeyBindState(sprintCode, false);
            KeyBinding.setKeyBindState(sprintCode, false);

            // 3. 發 STOP_SPRINTING packet 告訴伺服器停止（防同步問題）
            PacketUtil.sendPacketNoEvent(
                new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING)
            );
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND) return;

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

            int m = this.mode.getValue();
            switch (m) {
                case 1:
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
                case 2:
                    if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                        event.setCancelled(true);
                    } else {
                        KeyBinding.unPressAllKeys();
                        event.setCancelled(true);
                        this.clickQueue.offer(packet);
                        this.delayTicks = 8;
                    }
                    break;
                case 4:
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
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
