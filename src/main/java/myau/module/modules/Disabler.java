package myau.module.modules;

import myau.events.PacketEvent;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;

public class Disabler extends Module {

    private boolean isSending = false;   // 模擬原作者的 sendPacketNoEvent，防止重複觸發

    public Disabler() {
        super("Disabler", "Move Disabler", Category.WORLD);  
        // ↑↑↑ 這裡請改成和你其他 Module（例如 NoSlow）完全一樣的建構子寫法
        // 常見範例：
        // super("Disabler", "Move Disabler", Category.EXPLOIT);
    }

    @EventHandler
    public void onPacketSend(PacketEvent e) {
        if (isSending) return;   // ← 原作者 sendPacketNoEvent 的效果

        // 以下完全照原作者 L1ngGe 的寫法
        if ((e.getPacket() instanceof C0EPacketClickWindow || 
             e.getPacket() instanceof C0DPacketCloseWindow) && 
            mc.thePlayer != null && mc.thePlayer.isSprinting()) {
            
            e.setCancelled(true);

            isSending = true;
            mc.getNetHandler().addToSendQueue(
                new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));

            mc.getNetHandler().addToSendQueue(e.getPacket());   // 原封包

            mc.getNetHandler().addToSendQueue(
                new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
            isSending = false;
        }
    }
}