package myau.mixin;

import myau.Myau;
import myau.event.EventManager;
import myau.events.KnockbackEvent;
import myau.events.SafeWalkEvent;
import myau.mixin.interfaces.IMixinEntity;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin({Entity.class})
public abstract class MixinEntity implements IMixinEntity {

```
@Shadow public World worldObj;
@Shadow public double posX;
@Shadow public double posY;
@Shadow public double posZ;
@Shadow public double motionX;
@Shadow public double motionY;
@Shadow public double motionZ;
@Shadow public float rotationYaw;
@Shadow public float rotationPitch;
@Shadow public float prevRotationYaw;
@Shadow public float prevRotationPitch;
@Shadow public boolean onGround;

private double trueX;
private double trueY;
private double trueZ;
private boolean truePos;

@Shadow
public boolean isRiding() {
    return false;
}

@Inject(method = "setPosition", at = @At("HEAD"))
private void updateTruePosition(double x, double y, double z, CallbackInfo ci) {
    this.trueX = x;
    this.trueY = y;
    this.trueZ = z;
    this.truePos = true;
}

@Inject(method = "setVelocity", at = @At("HEAD"), cancellable = true)
private void setVelocity(double x, double y, double z, CallbackInfo ci) {
    if ((Entity)(Object)this instanceof EntityPlayerSP) {
        KnockbackEvent event = new KnockbackEvent(x, y, z);
        EventManager.call(event);

        if (event.isCancelled()) {
            ci.cancel();
            this.motionX = event.getX();
            this.motionY = event.getY();
            this.motionZ = event.getZ();
        }
    }
}

@Inject(method = "setAngles", at = @At("HEAD"), cancellable = true)
private void setAngles(CallbackInfo ci) {
    if ((Entity)(Object)this instanceof EntityPlayerSP && Myau.rotationManager != null && Myau.rotationManager.isRotated()) {
        ci.cancel();
    }
}

@ModifyVariable(method = "moveEntity", ordinal = 0, at = @At("STORE"), name = {"flag"})
private boolean moveEntity(boolean safeWalk) {
    if ((Entity)(Object)this instanceof EntityPlayerSP) {
        SafeWalkEvent event = new SafeWalkEvent(safeWalk);
        EventManager.call(event);
        return event.isSafeWalk();
    }
    return safeWalk;
}

@Override
public double getTrueX() {
    return trueX;
}

@Override
public double getTrueY() {
    return trueY;
}

@Override
public double getTrueZ() {
    return trueZ;
}

@Override
public boolean getTruePos() {
    return truePos;
}

}
