package myau.management;

import lombok.Getter;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public class RotationManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private float lastUpdate;
    private float yawDelta;
    private float pitchDelta;
    private int priority;
    private boolean snapbacking;
    private float snapbackTargetYaw;
    private float snapbackTargetPitch;
    private float snapbackMaxStep;
    @Getter
    private boolean rotated;

    public RotationManager() {
        this.lastUpdate = Float.NaN;
        this.yawDelta = Float.NaN;
        this.pitchDelta = Float.NaN;
        this.priority = Integer.MIN_VALUE;
        this.snapbacking = false;
        this.snapbackTargetYaw = 0.0F;
        this.snapbackTargetPitch = 0.0F;
        this.snapbackMaxStep = 60.0F;
        this.rotated = false;
    }

    private void applyRotation(float partialTicks) {
        if (mc.thePlayer != null && !Float.isNaN(this.yawDelta) && !Float.isNaN(this.pitchDelta) && !Float.isNaN(this.lastUpdate)) {
            float yaw = this.yawDelta * (partialTicks - this.lastUpdate);
            if (yaw != 0.0F) {
                mc.thePlayer.prevRotationYaw = mc.thePlayer.rotationYaw;
                mc.thePlayer.rotationYaw += yaw;
            }
            float pitch = this.pitchDelta * (partialTicks - this.lastUpdate);
            if (pitch != 0.0F) {
                mc.thePlayer.prevRotationPitch = mc.thePlayer.rotationPitch;
                mc.thePlayer.rotationPitch += pitch;
                mc.thePlayer.rotationPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch, -90.0F, 90.0F);
            }
            this.lastUpdate = partialTicks;
        }
    }

    private void resetRotationState() {
        this.lastUpdate = Float.NaN;
        this.yawDelta = Float.NaN;
        this.pitchDelta = Float.NaN;
        this.priority = Integer.MIN_VALUE;
        this.rotated = false;
    }

    public void setRotation(float yaw, float pitch, int priority, boolean force) {
        if (this.priority <= priority) {
            this.priority = priority;
            this.yawDelta = MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
            this.pitchDelta = MathHelper.clamp_float(pitch - mc.thePlayer.rotationPitch, -90.0F, 90.0F);
            this.lastUpdate = 0.0F;
            this.rotated = force;
            this.applyRotation(0.0F);
        }
    }

    public void startSnapback(float maxStep) {
        if (mc.thePlayer == null) {
            return;
        }
        this.startSnapback(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, maxStep);
    }

    public void startSnapback(float targetYaw, float targetPitch, float maxStep) {
        this.snapbacking = true;
        this.snapbackTargetYaw = targetYaw;
        this.snapbackTargetPitch = targetPitch;
        this.snapbackMaxStep = Math.max(0.0F, maxStep);
    }

    public void cancelSnapback() {
        this.snapbacking = false;
    }

    public boolean isSnapbacking() {
        return this.snapbacking;
    }

    public float[] tickSnapback(float currentYaw, float currentPitch) {
        if (!this.snapbacking) {
            return new float[]{currentYaw, currentPitch};
        }

        float[] smoothed = getSmoothSnapback(
                currentYaw, currentPitch,
                this.snapbackTargetYaw, this.snapbackTargetPitch,
                this.snapbackMaxStep
        );

        if (Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - this.snapbackTargetYaw)) < 0.5F
                && Math.abs(smoothed[1] - this.snapbackTargetPitch) < 0.5F) {
            this.snapbacking = false;
            return new float[]{this.snapbackTargetYaw, this.snapbackTargetPitch};
        }
        return smoothed;
    }

    private static float[] getSmoothSnapback(float currentYaw, float currentPitch,
                                             float targetYaw, float targetPitch,
                                             float maxStep) {
        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;

        if (Math.abs(deltaYaw) < 0.1F) currentYaw = targetYaw;
        if (Math.abs(deltaPitch) < 0.1F) currentPitch = targetPitch;
        if (currentYaw == targetYaw && currentPitch == targetPitch) {
            return new float[]{currentYaw, clampPitch(currentPitch)};
        }

        maxStep *= 1.0F - (float) (Math.random() * 0.2);  // 或用 RandomUtil

        float totalDelta = Math.abs(deltaYaw) + Math.abs(deltaPitch);
        if (totalDelta <= maxStep) {
            currentYaw = targetYaw;
            currentPitch = targetPitch;
        } else if (maxStep > 0.0F) {
            float scale = maxStep / totalDelta;
            currentYaw += deltaYaw * scale;
            currentPitch += deltaPitch * scale;
        }
        return new float[]{currentYaw, clampPitch(currentPitch)};
    }

    private static float clampPitch(float pitch) {
        return pitch < -90.0F ? -90.0F : Math.min(pitch, 90.0F);
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        this.applyRotation(1.0F);
        this.resetRotationState();
    }

    @EventTarget(Priority.HIGHEST)
    public void onRender3D(Render3DEvent event) {
        this.applyRotation(event.getPartialTicks());
    }
}
