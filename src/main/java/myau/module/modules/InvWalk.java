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
                // 刻意不包含 keyBindSprint，下面單獨處理
        };

        for (KeyBinding keyBinding : movementKeys) {
            KeyBindUtil.updateKeyState(keyBinding.getKeyCode());
        }

        boolean shouldSprint = false;

        // 決定是否要啟動 sprint
        if (this.mode.getValue() != 4) {  // 非 UNSPRINT 模式才看 Sprint 模組
            if (Myau.moduleManager.getModule("Sprint").isEnabled()) {  // 注意：這裡假設 Sprint 模組叫 "Sprint"
                shouldSprint = true;
            }
        }
        // UNSPRINT 模式下故意不 sprint

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

        // UNSPRINT 模式：只要是容器介面就允許走路（但上面會強制不 sprint）
        if (m == 4) {
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
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.canInvWalk() && this.delayTicks == 0) {
                this.pressMovementKeys();

                // UNSPRINT 模式額外強制關閉 sprint（保險）
                if (this.mode.getValue() == 4) {
                    mc.thePlayer.setSprinting(false);
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
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
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && event.getType() == EventType.SEND) {
            if (event.getPacket() instanceof C16PacketClientStatus) {
                if (this.mode.getValue() == 1) {  // 只在 VANILLA 模式處理這個邏輯
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
                    case 4: // UNSPRINT
                        // 不需要特別封包處理，直接允許正常點擊，但走路不停 sprint
                        break;
                }

                if (this.pendingStatus != null) {
                    PacketUtil.sendPacketNoEvent(this.pendingStatus);
                    this.pendingStatus = null;
                }
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
