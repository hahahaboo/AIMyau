package myau.mixin;

import myau.Myau;
import myau.module.modules.Jesus;
import myau.module.modules.WorldTime;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin({World.class})
public abstract class MixinWorld {

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

    @Redirect(
            method = "getWorldTime",
            at = @At("RETURN")
    )
    private long getWorldTime(World world) {
        if (Myau.moduleManager == null) {
            return world.getWorldTime();
        }
        WorldTime worldTimeMod = (WorldTime) Myau.moduleManager.modules.get(WorldTime.class);
        if (worldTimeMod != null && worldTimeMod.isEnabled()) {
            return worldTimeMod.time.getValue();
        }
        return world.getWorldTime();
    }
}
