package myau.mixin;

import myau.module.ModuleManager;
import myau.module.modules.Ambience;
import net.minecraft.util.BlockPos;
import net.minecraft.world.biome.BiomeGenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeGenBase.class)
public abstract class MixinBiomeGenBase {

    @Inject(method = "getFloatTemperature", at = @At("HEAD"), cancellable = true)
    private void onGetFloatTemperature(BlockPos pos, CallbackInfoReturnable<Float> cir) {
        Ambience ambience = (Ambience) ModuleManager.getModule(Ambience.class);
        if (ambience != null && ambience.isEnabled()) {
            cir.setReturnValue(ambience.getFloatTemperature(pos, (BiomeGenBase) (Object) this));
        }
    }
}
