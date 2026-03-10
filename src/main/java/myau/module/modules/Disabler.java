package myau.module.modules;


import myau.events.PacketEvent;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;


public class Disabler extends Module {


    private boolean isSending = false; 
    public Disabler() {
        super("Disabler", "Move Disabler", Category.EXPLOIT, 0, false, false);
    }



    @EventHandler
    public void onPacketSend(PacketEvent e) {
        if (isSending) return;   


        if ((e.getPacket() instanceof C0EPacketClickWindow || 
             e.getPacket() instanceof C0DPacketCloseWindow) && 
            mc.thePlayer != null && mc.thePlayer.isSprinting()) {

            
            e.setCancelled(true);


            isSending = true;
            mc.getNetHandler().addToSendQueue(
                new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));


            mc.getNetHandler().addToSendQueue(e.getPacket());   


            mc.getNetHandler().addToSendQueue(
                new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
            isSending = false;
        }
    }


}
