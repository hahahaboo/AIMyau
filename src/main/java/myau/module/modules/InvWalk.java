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
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;

import java.util.ArrayList;
import java.util.List;
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

    private void forceStopSprinting() {
        if (mc.thePlayer.isSprinting()) {
            PacketUtil.sendPacketNoEvent(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
            mc.thePlayer.setSprinting(false);
        }
    }

    private void forceStartSprinting() {
        if (!mc.thePlayer.isSprinting() && wasSprinting) {
            // 先送 START packet
            PacketUtil.sendPacketNoEvent(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
            mc.thePlayer.setSprinting(true);
            // 補一個 dummy C03 來 sync 狀態給 Grim simulation
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer(mc.thePlayer.onGround));
        }
    }

    private void flushGrimPackets(boolean sendFakeClose) {
        for (C0EPacketClickWindow p : new ArrayList<>(grimDelayedPackets)) {
            PacketUtil.sendPacketNoEvent(p);
        }
        grimDelayedPackets.clear();

        if (sendFakeClose && mc.thePlayer.inventory.getItemStack() == null) {
            PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
        }
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

            if (grimTimer >= 20) {  // 基礎 20，但下面會 random reset
                grimTimer = 0;

                wasSprinting = mc.thePlayer.isSprinting();
                forceStopSprinting();

                flushGrimPackets(true);

                // random restore delay 6~10 ticks，更自然避 simulation flag
                sprintRestoreTicks = random.nextInt(5) + 6;
            }
        } else if (clientInvOpen) {
            clientInvOpen = false;
            forceStopSprinting();
            flushGrimPackets(false);
        }

        if (sprintRestoreTicks > 0) {
            sprintRestoreTicks--;
            if (sprintRestoreTicks == 0) {
                forceStartSprinting();
            }
        }

        // 讓 grimTimer 更 random，避免固定 20 被 timer/simulation 抓
        if (grimTimer > 0 && grimTimer % 5 == 0) {
            if (random.nextBoolean()) grimTimer--;  // 偶爾提前一點
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
        } else if (event.getPacket() instanceof C0DPacketCloseWindow) {
            C0DPacketCloseWindow packet = (C0DPacketCloseWindow) event.getPacket();
            if (this.pendingStatus != null && ((IAccessorC0DPacketCloseWindow) packet).getWindowId() == 0) {
                this.pendingStatus = null;
                event.setCancelled(true);
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
            forceStopSprinting();
            flushGrimPackets(false);
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
