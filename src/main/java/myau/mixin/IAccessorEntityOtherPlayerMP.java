package myau.mixin;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Maps EntityOtherPlayerMP interpolation / server target fields
 * (Slinky TimingAccess: otherPlayerMPX/Y/Z, yaw, increments).
 */
@SideOnly(Side.CLIENT)
@Mixin(EntityOtherPlayerMP.class)
public interface IAccessorEntityOtherPlayerMP {

    @Accessor("otherPlayerMPX")
    double getOtherPlayerMPX();

    @Accessor("otherPlayerMPY")
    double getOtherPlayerMPY();

    @Accessor("otherPlayerMPZ")
    double getOtherPlayerMPZ();

    @Accessor("otherPlayerMPYaw")
    double getOtherPlayerMPYaw();

    @Accessor("otherPlayerMPPosRotationIncrements")
    int getOtherPlayerMPPosRotationIncrements();
}
