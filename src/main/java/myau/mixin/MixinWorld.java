package myau.mixin;

import myau.Myau;
import myau.module.ModuleManager;
import myau.module.modules.Ambience;
import myau.module.modules.Jesus;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin({World.class})
public abstract class MixinWorld {

    // ==================== 原有功能（完全保留） ====================
    @Redirect(
            method = {"handleMaterialAcceleration"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;isPushedByWater()Z"
            )
    )
    private boolean handleMaterialAcceleration(Entity entity) {
        if (entity instanceof EntityPlayerSP && Myau.moduleManager != null) {
            Jesus jesus = (Jesus) Myau.moduleManager.modules.get(Jesus.class);
            if (jesus.isEnabled() && jesus.noPush.getValue()) {
                return false;
            }
        }
        return entity.isPushedByWater();
    }

    @Redirect(
            method = {"rayTraceBlocks(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;ZZZ)Lnet/minecraft/util/MovingObjectPosition;"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/BlockPos;)Lnet/minecraft/block/state/IBlockState;"
            )
    )
    private IBlockState rayTraceBlocks(World world, BlockPos blockPos) {
        return world.getBlockState(blockPos);
    }

    // ==================== 新增：Ambience Weather 支援 ====================
    @Inject(method = "getRainStrength", at = @At("HEAD"), cancellable = true)
    private void onGetRainStrength(float partialTicks, CallbackInfoReturnable<Float> cir) {
        Ambience ambience = (Ambience) ModuleManager.getModule(Ambience.class);
        if (ambience != null && ambience.isEnabled()) {
            String mode = ambience.weather.getModeString();
            if ("Heavy Snow".equals(mode) || "Light Snow".equals(mode) || "Nether Particles".equals(mode)) {
                cir.setReturnValue(0.75F); // 維持雨強度讓粒子系統運作，但 skipRainParticles 會轉成雪
            }
        }
    }
}
