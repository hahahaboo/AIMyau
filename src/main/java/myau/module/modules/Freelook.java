package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Freelook extends Module {

    public final BooleanProperty hold = new BooleanProperty("Hold", true);
    public final BooleanProperty invertPitch = new BooleanProperty("Invert Pitch", false);
    public final BooleanProperty lockPitch = new BooleanProperty("Lock Pitch", true);
    public final BooleanProperty customFov = new BooleanProperty("Custom FOV", false);
    public final FloatProperty fov = new FloatProperty("FOV", 90f, 10f, 150f);

    public static boolean perspectiveToggled = false;
    public static float cameraYaw = 0f;
    public static float cameraPitch = 0f;

    private boolean prevKeyState = false;
    private int previousPerspective = 0;
    private float lastFov = 70f;

    public Freelook() {
        super("Freelook", "自由視角（不影響玩家移動方向）", Category.RENDER, 56, false, false);
    }

    @Override
    public void onEnabled() {
        lastFov = mc.gameSettings.fovSetting;
    }

    @Override
    public void onDisabled() {
        if (perspectiveToggled) resetPerspective();
    }

    @EventTarget
    public void onTick(TickEvent e) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;

        boolean down = KeyBindUtil.isKeyDown(getKey());
        if (down != prevKeyState) {
            onKeyStateChanged(down);
            prevKeyState = down;
        }
    }

    private void onKeyStateChanged(boolean state) {
        if (state) {
            if (!perspectiveToggled) {
                enterPerspective();
            } else if (!hold.getValue()) {
                resetPerspective();
            }
        } else if (hold.getValue() && perspectiveToggled) {
            resetPerspective();
        }
    }

    private void enterPerspective() {
        perspectiveToggled = true;
        previousPerspective = mc.gameSettings.thirdPersonView;
        applyThirdPersonView(1);
        lastFov = mc.gameSettings.fovSetting;
        cameraYaw = mc.thePlayer.rotationYaw;
        cameraPitch = mc.thePlayer.rotationPitch;
    }

    public void resetPerspective() {
        perspectiveToggled = false;
        applyThirdPersonView(previousPerspective);

        if (mc.currentScreen == null && mc.inGameHasFocus) {
            mc.mouseHelper.grabMouseCursor();
        }

        if (hold.getValue() || customFov.getValue()) {
            mc.gameSettings.fovSetting = lastFov;
        }
    }

    private void applyThirdPersonView(int view) {
        view = Math.max(0, Math.min(2, view));
        mc.gameSettings.thirdPersonView = view;

        if (mc.entityRenderer != null) {
            mc.entityRenderer.loadEntityShader(view == 0 ? mc.getRenderViewEntity() : null);
        }

        if (mc.renderGlobal != null) {
            mc.renderGlobal.setDisplayListEntitiesDirty();
        }
    }

    public static boolean overrideMouse(Minecraft mc) {
        if (!mc.inGameHasFocus || !perspectiveToggled) return true;

        Freelook fl = (Freelook) Myau.moduleManager.modules.get(Freelook.class);
        if (fl == null || !fl.isEnabled()) return true;

        mc.mouseHelper.mouseXYChange();

        float sens = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        float mult = sens * sens * sens * 8.0f;

        int dx = ((myau.mixin.IAccessorMouseHelper) mc.mouseHelper).getDeltaX();
        int dy = ((myau.mixin.IAccessorMouseHelper) mc.mouseHelper).getDeltaY();

        cameraYaw += dx * mult * 0.15f;
        float fdy = dy * mult * 0.15f;

        if (fl.invertPitch.getValue()) fdy = -fdy;
        cameraPitch += fdy;

        if (fl.lockPitch.getValue()) {
            cameraPitch = Math.max(-90f, Math.min(90f, cameraPitch));
        }

        if (fl.customFov.getValue()) {
            mc.gameSettings.fovSetting = fl.fov.getValue();
        }

        return false;   // 返回 false = 取消原版滑鼠對玩家旋轉的影響
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui != null && perspectiveToggled && hold.getValue()) {
            resetPerspective();
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load e) {
        if (perspectiveToggled) resetPerspective();
    }
}
