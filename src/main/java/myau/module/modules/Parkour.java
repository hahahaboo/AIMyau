package myau.module.modules;

import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.util.KeyBindUtil;
import myau.util.MoveUtil;
import myau.util.PlayerUtil;
import myau.util.TimerUtil;

public class Parkour extends Module {

    public final BooleanProperty notOnSneaking = new BooleanProperty("not-on-sneaking", true);
    // 新增：更精準的邊緣偵測相關設定（可依需求調整）
    public final BooleanProperty usePredictCheck = new BooleanProperty("use-predict-check", true);

    private final TimerUtil cd = new TimerUtil();

    public Parkour() {
        super("Parkour", "自動跳躍過空隙（Parkour Helper）", Category.PLAYER, 0, false, false);
    }

    @EventTarget
    public void onTick(TickEvent e) {
        if (e.getType() != EventType.PRE) return;
        
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (notOnSneaking.getValue() && PlayerUtil.isSneaking()) {
            return;
        }

        // 釋放 Jump 鍵
        if (!KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) && cd.hasTimeElapsed(10)) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        }

        if (mc.thePlayer.onGround && (mc.thePlayer.motionX != 0 || mc.thePlayer.motionZ != 0)) {
            boolean shouldJump = false;
            
            if (usePredictCheck.getValue()) {
                // === 使用 Eagle 同樣的預測邏輯（推薦）===
                double[] offset = MoveUtil.predictMovement();
                // 檢查往前走一步是否會掉下去（或無法站立）
                shouldJump = !PlayerUtil.canMove(offset[0] * 1.1, offset[1] * 1.1, -0.5); // 略微放大偏移 + 往下檢查
            } else {
                // 保留原本的簡單檢查（作為 fallback）
                shouldJump = isPlayerOverAir();
            }

            if (shouldJump) {
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                cd.reset();
            }
        }
    }

    /**
     * 原有簡單檢查（保留供切換使用）
     */
    private boolean isPlayerOverAir() {
        double x = mc.thePlayer.posX;
        double y = mc.thePlayer.posY - 1.0D;
        double z = mc.thePlayer.posZ;
        net.minecraft.util.BlockPos p = new net.minecraft.util.BlockPos(
                net.minecraft.util.MathHelper.floor_double(x),
                net.minecraft.util.MathHelper.floor_double(y),
                net.minecraft.util.MathHelper.floor_double(z)
        );
        return mc.theWorld.isAirBlock(p);
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        cd.reset();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
    }
}
