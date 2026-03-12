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

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvWalk extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // === 整合 Grim 進原有 mode 系統 ===
    public final ModeProperty mode = new ModeProperty("mode", 1, 
            new String[]{"VANILLA", "BRAINSPOOF", "HYPIXEL", "GRIM"});

    private final Queue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
    
    // Grim 專用 delayed packets（支援所有容器：背包、chest、shulker、enderchest 等）
    private final List<C0EPacketClickWindow> grimDelayedPackets = new ArrayList<>();

    private boolean keysPressed = false;
    private C16PacketClientStatus pendingStatus = null;
    private int delayTicks = 0;

    // Grim mode 專用變數
    private int grimTimer = 0;
    private boolean wasSprinting = false;
    private int sprintRestoreTicks = 0;
    private boolean clientInvOpen = false;

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
        if (Myau.moduleManager.getModule("Sprint").isEnabled()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
        this.keysPressed = true;
    }

    // === canInvWalk 改成支援所有容器（包含 chest 等）===
    public boolean canInvWalk() {
        if (!(mc.currentScreen instanceof GuiContainer) || mc.currentScreen instanceof GuiContainerCreative) {
            return false;
        }

        int m = this.mode.getValue();

        if (m == 3) { // GRIM mode
            return true; // 任何 GuiContainer（背包、chest、shulker、enderchest...）都可以走
        }

        // 原有 mode 邏輯（不影響 VANILLA/BRAINSPOOF/HYPIXEL）
        switch (m) {
            case 1: // BRAINSPOOF (原本 case 1)
                if (!(mc.currentScreen instanceof GuiInventory)) {
                    return false;
                }
                return this.pendingStatus != null && this.clickQueue.isEmpty();
            case 2: // HYPIXEL
                return this.clickQueue.isEmpty();
            default:
                return true;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;

        // 原有 clickQueue flush（給非 GRIM mode 用）
        while (!this.clickQueue.isEmpty()) {
            PacketUtil.sendPacketNoEvent(this.clickQueue.poll());
        }

        // === GRIM mode 核心：每 20 ticks fake close bypass ===
        if (this.mode.getValue() != 3 || !this.isEnabled() || mc.thePlayer == null) return;

        boolean isInvOpenNow = mc.currentScreen instanceof GuiContainer && 
                               !(mc.currentScreen instanceof GuiContainerCreative);

        if (isInvOpenNow) {
            clientInvOpen = true;
            grimTimer++;

            if (grimTimer >= 20) {
                grimTimer = 0;

                wasSprinting = mc.thePlayer.isSprinting();
                mc.thePlayer.setSprinting(false);  // 取消衝刺

                flushGrimPackets(true);  // 送 delayed + fake close

                sprintRestoreTicks = 4; // 可微調 3~6
            }
        } 
        // 玩家手動關閉任何容器時（currentScreen 變 null）
        else if (clientInvOpen) {
            clientInvOpen = false;
            flushGrimPackets(false); // 只 flush，不補 fake close
        }

        // 恢復衝刺
        if (sprintRestoreTicks > 0) {
            sprintRestoreTicks--;
            if (sprintRestoreTicks == 0 && wasSprinting) {
                mc.thePlayer.setSprinting(true);
            }
        }
    }

    private void flushGrimPackets(boolean sendFakeClose) {
        for (C0EPacketClickWindow p : new ArrayList<>(grimDelayedPackets)) {
            PacketUtil.sendPacketNoEvent(p);
        }
        grimDelayedPackets.clear();

        if (sendFakeClose) {
            // 關鍵：發假 close packet (windowId=0) → server 以為關了，但 client 畫面還開著
            PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

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

        int m = this.mode.getValue();

        if (event.getPacket() instanceof C16PacketClientStatus) {
            if (m == 1) { // 只給 BRAINSPOOF 用
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
            // GRIM mode 不阻擋真正關閉容器，讓玩家能正常關背包/chest
        } 
        else if (event.getPacket() instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow packet = (C0EPacketClickWindow) event.getPacket();

            if (m == 3) { // === GRIM mode：delay 所有容器（包含 chest、shulker 等）===
                event.setCancelled(true);
                grimDelayedPackets.add(packet);

                // 如果有 pendingStatus 順便送出
                if (this.pendingStatus != null) {
                    PacketUtil.sendPacketNoEvent(this.pendingStatus);
                    this.pendingStatus = null;
                }
                return; // 直接 return，不走下面原有邏輯
            }

            // === 原有 mode 邏輯（VANILLA / BRAINSPOOF / HYPIXEL）===
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
        if (this.mode.getValue() == 3) {
            flushGrimPackets(false); // 清掉殘留
        }
        this.delayTicks = 0;
        this.grimTimer = 0;
        this.sprintRestoreTicks = 0;
        this.grimDelayedPackets.clear();
    }

    @Override
    public String[] getSuffix() {
        String suffix = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString());
        return new String[]{suffix};
    }
}
