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

            // 取得原始滑鼠 delta
            float deltaYaw = (float) mc.mouseHelper.deltaX;
            float deltaPitch = (float) mc.mouseHelper.deltaY;

            // 傳給 FreeLook 處理
            freeLook.handleMouseInput(deltaYaw, deltaPitch);

            // 取消原版 setAngles，讓玩家本身不轉頭
            mc.mouseHelper.deltaX = 0;
            mc.mouseHelper.deltaY = 0;
            ci.cancel();
        }
    }
}
