package myau.management;

import lombok.Getter;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public class RotationManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private float lastUpdate;
    private float yawDelta;
    private float pitchDelta;
    private int priority;
    @Getter
    private boolean rotated;

    // smooth snapback（把 server rotation 拉回 client 視角）
    private boolean snapbacking;
    private float snapbackMaxStep;

    public RotationManager() {
        this.lastUpdate = Float.NaN;
        this.yawDelta = Float.NaN;
        this.pitchDelta = Float.NaN;
        this.priority = Integer.MIN_VALUE;
        this.rotated = false;
        this.snapbacking = false;
        this.snapbackMaxStep = 60.0F;
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

    /** 開始把 server rotation 平滑轉回 client 視角 */
    public void startSnapback(float maxStep) {
        this.snapbacking = true;
        this.snapbackMaxStep = Math.max(0.0F, maxStep);
    }

    public void cancelSnapback() {
        this.snapbacking = false;
    }

    public boolean isSnapbacking() {
        return this.snapbacking;
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE || !this.snapbacking || mc.thePlayer == null) {
            return;
        }

        float clientYaw = mc.thePlayer.rotationYaw;
        float clientPitch = mc.thePlayer.rotationPitch;

        float currentYaw = event.isRotated() ? event.getNewYaw() : event.getYaw();
        float currentPitch = event.isRotated() ? event.getNewPitch() : event.getPitch();

        float[] smoothed = getSmoothSnapback(
                currentYaw, currentPitch,
                clientYaw, clientPitch,
                this.snapbackMaxStep
        );

        // 只改 server rotation，不碰 client 視角
        event.setRotation(smoothed[0], smoothed[1], Integer.MAX_VALUE);
        event.setPervRotation(smoothed[0], Integer.MAX_VALUE);

        if (Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - clientYaw)) < 0.5F
                && Math.abs(smoothed[1] - clientPitch) < 0.5F) {
            this.snapbacking = false;
        }
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

    private static float[] getSmoothSnapback(float currentYaw, float currentPitch,
                                             float targetYaw, float targetPitch,
                                             float maxStep) {
        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;

        if (Math.abs(deltaYaw) < 0.1F) {
            currentYaw = targetYaw;
        }
        if (Math.abs(deltaPitch) < 0.1F) {
            currentPitch = targetPitch;
        }
        if (currentYaw == targetYaw && currentPitch == targetPitch) {
            return new float[]{currentYaw, clampPitch(currentPitch)};
        }

        maxStep *= 1.0F - (float) (Math.random() * 0.2);

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
}
