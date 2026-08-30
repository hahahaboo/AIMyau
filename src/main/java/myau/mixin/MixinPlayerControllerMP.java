package myau.mixin;

import myau.Myau;
import myau.event.EventManager;
import myau.events.CancelUseEvent;
import myau.events.WindowClickEvent;
import myau.module.modules.AbortBreaking;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin({PlayerControllerMP.class})
public abstract class MixinPlayerControllerMP {
    @Inject(
            method = {"windowClick"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void windowClick(
            int windowId, int slotId, int mouseButtonClicked, int mode, EntityPlayer entityPlayer, CallbackInfoReturnable<ItemStack> callbackInfoReturnable
    ) {
        WindowClickEvent event = new WindowClickEvent(windowId, slotId, mouseButtonClicked, mode);
        EventManager.call(event);
        if (event.isCancelled()) {
            callbackInfoReturnable.cancel();
        }
    }

    @Inject(
            method = {"onStoppedUsingItem"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void onStoppedUsingItem(CallbackInfo callbackInfo) {
        CancelUseEvent event = new CancelUseEvent();
        EventManager.call(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = {"getIsHittingBlock"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void getIsHittingBlock(CallbackInfoReturnable<Boolean> cir) {
        if (Myau.moduleManager != null
                && Myau.moduleManager.modules.get(AbortBreaking.class) != null
                && Myau.moduleManager.modules.get(AbortBreaking.class).isEnabled()) {
            cir.setReturnValue(false);
        }
    }
}
