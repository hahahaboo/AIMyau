package myau.module.modules;

import myau.event.EventTarget;
import myau.events.*;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.util.KeyBindUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;

public class AntiAFK extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private final IntProperty time = new IntProperty("Time", 30, 10, 300);

    private final TimerUtil afkTimer = new TimerUtil();
    private final TimerUtil action1Timer = new TimerUtil();
    private final TimerUtil action2Timer = new TimerUtil();
    private final TimerUtil action3Timer = new TimerUtil();

    private boolean isAFK = false;
    private int strafeTicks = 0;
    private float lastStrafe = 0f;

    public AntiAFK() {
        super("AntiAFK", "防止因長時間不動作而被伺服器踢出", Category.MISC, 0, false, false);
        addProperties(time);
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        resetAll();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        resetAll();
        // 清理按鍵狀態
        if (mc.gameSettings != null) {
            mc.gameSettings.keyBindForward.setKeyDown(false);
            mc.gameSettings.keyBindBack.setKeyDown(false);
            mc.gameSettings.keyBindLeft.setKeyDown(false);
            mc.gameSettings.keyBindRight.setKeyDown(false);
        }
    }

    private void resetAll() {
        isAFK = false;
        afkTimer.reset();
        action1Timer.reset();
        action2Timer.reset();
        action3Timer.reset();
        strafeTicks = 0;
        lastStrafe = 0f;
    }

    @EventTarget
    public void onTick(TickEvent e) {
        if (mc.thePlayer == null) return;

        if (!isAFK) {
            if (afkTimer.hasTimeElapsed(time.getValue() * 1000L)) {
                enterAFK();
            }
        } else {
            handleAFKActions();
        }
    }

    @EventTarget
    public void onKey(KeyEvent e) {
        if (e.isPressed()) {
            int key = e.getKey();
            if (isMovementKey(key) || 
                key == mc.gameSettings.keyBindJump.getKeyCode() || 
                key == mc.gameSettings.keyBindSneak.getKeyCode()) {
                exitAFK();
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent e) {
        exitAFK();
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent e) {
        exitAFK();
    }

    @EventTarget
    public void onJump(JumpEvent e) {
        exitAFK();
    }

    private boolean isMovementKey(int key) {
        return key == mc.gameSettings.keyBindForward.getKeyCode() ||
               key == mc.gameSettings.keyBindBack.getKeyCode() ||
               key == mc.gameSettings.keyBindLeft.getKeyCode() ||
               key == mc.gameSettings.keyBindRight.getKeyCode();
    }

    private void enterAFK() {
        isAFK = true;
        afkTimer.reset();
        action1Timer.reset();
        action2Timer.reset();
        action3Timer.reset();
        strafeTicks = 0;
        performAction1(); // 進入時立即執行動作1
    }

    private void exitAFK() {
        if (isAFK) {
            isAFK = false;
            resetAll();
        }
        afkTimer.reset();
    }

    private void handleAFKActions() {
        if (action1Timer.hasTimeElapsed(30000)) {
            performAction1();
            action1Timer.reset();
        }
        if (action2Timer.hasTimeElapsed(20000)) {
            performAction2();
            action2Timer.reset();
        }
        if (action3Timer.hasTimeElapsed(50000)) {
            performAction3();
            action3Timer.reset();
        }
    }

    private void performAction1() {
        strafeTicks = 10; // 約 0.5 秒
        lastStrafe = 1.0f; // 先向右
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent e) {
        if (isAFK && strafeTicks > 0) {
            e.setStrafe(lastStrafe);
            strafeTicks--;
            if (strafeTicks == 5) {
                lastStrafe = -1.0f; // 切換向左
            }
            if (strafeTicks <= 0) {
                lastStrafe = 0f;
            }
        }
    }

    private void performAction2() {
        KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
    }

    private void performAction3() {
        KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindJump.getKeyCode());
    }
}
