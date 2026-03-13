package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
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
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvWalk extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random random = new Random();

    public final ModeProperty mode = new ModeProperty("mode", 1,
            new String[]{"VANILLA", "BRAINSPOOF", "HYPIXEL", "GRIM"});

    private final Queue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
    private final List<C0EPacketClickWindow> grimDelayedPackets = new ArrayList<>();

    private boolean keysPressed = false;
    private C16PacketClientStatus pendingStatus = null;
    private int delayTicks = 0;

    // Grim mode 變數
    private int grimTimer = 0;
    private int movementRestoreTicks = 0;
    private boolean clientInvOpen = false;

    // 只記住 W A S D + Jump 的狀態
    private final Map<KeyBinding, Boolean> savedMovementKeys = new HashMap<>();

    public InvWalk() {
        super("InvWalk", "Allows you to walk while in inventories.", Category.MOVEMENT, 0, false, false);
    }

    private KeyBinding[] getMovementKeysToStop() {
        return new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump
        };
    }

    private void saveAndStopMovement() {
        savedMovementKeys.clear();
        for (KeyBinding key : getMovementKeysToStop()) {
            boolean isDown = KeyBindUtil.isKeyDown(key.getKeyCode());
            savedMovementKeys.put(key, isDown);
            if (isDown) {
                KeyBindUtil.setKeyBindState(key.getKeyCode(), false);
            }
        }
        // 嚴格不碰 sprint 狀態，讓遊戲自己處理（不移動 → sprint off）
    }

    private void restoreMovement() {
        for (Map.Entry<KeyBinding, Boolean> entry : savedMovementKeys.entrySet()) {
            if (entry.getValue()) {
                KeyBindUtil.setKeyBindState(entry.getKey().getKeyCode(), true);
            }
        }
        savedMovementKeys.clear();
        // 不主動 setSprinting(true)，Sprint module 會處理
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
        this.keysPressed = true;
    }

    public boolean canInvWalk() {
        if (!(mc.currentScreen instanceof GuiContainer) || mc.currentScreen instanceof GuiContainerCreative) {
            return false;
        }

        int m = this.mode.getValue();

        if (m == 3) {
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

    private void flushGrimPackets() {
        for (C0EPacketClickWindow p : new ArrayList<>(grimDelayedPackets)) {
            PacketUtil.sendPacketNoEvent(p);
        }
        grimDelayedPackets.clear();
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;

        while (!this.clickQueue.isEmpty()) {
            PacketUtil.sendPacketNoEvent(this.clickQueue.poll());
        }

        if (this.mode.getValue() != 3 || !this.isEnabled() || mc.thePlayer == null) return;

        boolean isInvOpenNow = mc.currentScreen instanceof GuiContainer &&
                               !(mc.currentScreen instanceof GuiContainerCreative);

        if (isInvOpenNow) {
            clientInvOpen = true;
            grimTimer++;

            if (grimTimer >= 18 + random.nextInt(7)) {
                grimTimer = 0;

                saveAndStopMovement();

                flushGrimPackets();

                movementRestoreTicks = random.nextInt(5) + 4;  // 4~8 ticks
            }
        } else if (clientInvOpen) {
            clientInvOpen = false;
            flushGrimPackets();
            restoreMovement();
        }

        if (movementRestoreTicks > 0) {
            movementRestoreTicks--;
            if (movementRestoreTicks == 0) {
                restoreMovement();
            }
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
            if (m == 1) {
                C16PacketClientStatus packet = (C16PacketClientStatus) event.getPacket();
                if (packet.getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                    event.setCancelled(true);
                    this.pendingStatus = packet;
                }
            }
        } else if (event.getPacket() instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow packet = (C0EPacketClickWindow) event.getPacket();

            if (m == 3) {
                event.setCancelled(true);
                grimDelayedPackets.add(packet);

                if (this.pendingStatus != null) {
                    PacketUtil.sendPacketNoEvent(this.pendingStatus);
                    this.pendingStatus = null;
                }
                return;
            }

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
            flushGrimPackets();
            restoreMovement();
        }
        this.delayTicks = 0;
        this.grimTimer = 0;
        this.movementRestoreTicks = 0;
        this.grimDelayedPackets.clear();
        this.savedMovementKeys.clear();
    }

    @Override
    public String[] getSuffix() {
        String suffix = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString());
        return new String[]{suffix};
    }
}
