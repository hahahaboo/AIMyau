package myau.mixin;

import myau.Myau;
import myau.module.modules.FreeLook;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MouseHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHelper.class)
public class MixinMouse {

    @Inject(method = "mouseXYChange", at = @At("HEAD"), cancellable = true)
    public void onMouseXYChange(CallbackInfo ci) {
        FreeLook freeLook = (FreeLook) Myau.moduleManager.modules.get(FreeLook.class);
        
        if (freeLook != null && freeLook.isEnabled()) {
            Minecraft mc = Minecraft.getMinecraft();

            float deltaYaw = (float) mc.mouseHelper.deltaX;
            float deltaPitch = (float) mc.mouseHelper.deltaY;

            if (deltaYaw != 0 || deltaPitch != 0) {
                freeLook.handleMouseInput(deltaYaw, deltaPitch);
            }

            // 清空 delta 防止原版處理
            mc.mouseHelper.deltaX = 0;
            mc.mouseHelper.deltaY = 0;

            ci.cancel();
        }
    }
}
