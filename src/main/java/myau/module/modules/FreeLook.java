package myau.module.modules;

import myau.module.Category;
import myau.module.Module;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Arrays;
import java.util.List;

public class FreeLook extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    public float freeYaw, freePitch;
    public float prevFreeYaw, prevFreePitch;
    private int previousPerspective = 0;

    public FreeLook() {
        super("FreeLook", "Look around in 3rd person without changing your direction", Category.RENDER, 0, false, false);
    }
    

    @Override
    public boolean handleSetCommand(String[] parts) {
        return false;
    }
    
    @Override
    public List<String> getSettings() {
        return Arrays.asList("No configurable settings.");
    }

   public boolean isHoldModule() {
return true;
}   
   @Override
    public void onEnable() {
        if (mc.thePlayer == null) return;

        previousPerspective = mc.gameSettings.thirdPersonView;

        mc.gameSettings.thirdPersonView = 1;

        freeYaw = prevFreeYaw = mc.thePlayer.rotationYaw;
        freePitch = prevFreePitch = mc.thePlayer.rotationPitch;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (mc.thePlayer == null || event.phase != TickEvent.Phase.START || !isEnabled()) return;
        
        prevFreeYaw = freeYaw;
        prevFreePitch = freePitch;
    }

    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Specials.Pre event) {
        if (isEnabled() && event.entity == mc.thePlayer) {
            event.setCanceled(true);
        }
    }

    @Override
    public void onDisable() {
        if (mc.thePlayer == null) return;

        mc.gameSettings.thirdPersonView = previousPerspective;
    }

    public void handleMouseInput(float deltaYaw, float deltaPitch) {
        this.freeYaw += deltaYaw * 0.15F;
        this.freePitch -= deltaPitch * 0.15F;

        if (this.freePitch > 90.0F) this.freePitch = 90.0F;
        if (this.freePitch < -90.0F) this.freePitch = -90.0F;
    }
}
