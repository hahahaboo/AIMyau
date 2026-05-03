package myau.module.modules;

import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.util.KeyBindUtil;
import myau.util.PlayerUtil;
import myau.util.TimerUtil;

public class Parkour extends Module {

    public final BooleanProperty notOnSneaking = new BooleanProperty("not-on-sneaking", true);

    private final TimerUtil cd = new TimerUtil();

    public Parkour() {
        super("Parkour", "自動跳躍過空隙（Parkour Helper）", Category.PLAYER, 0, false, false);
    }

    @EventTarget
    public void onTick(TickEvent e) {
        if (e.getType() != EventType.PRE) return;
        
        // 【重要修正】加入模組啟用檢查，解決「關閉後仍運作」的問題
        if (!this.isEnabled()) {
            return;
        }

        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        // 蹲下時不觸發 Parkour
        if (notOnSneaking.getValue() && PlayerUtil.isSneaking()) {
            return;
        }

        if (!KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) && cd.hasTimeElapsed(10)) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        }

        if (mc.thePlayer.onGround
                && isPlayerOverAir()
                && (mc.thePlayer.motionX != 0 || mc.thePlayer.motionZ != 0)) {
           
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            cd.reset();
        }
    }

    /**
     * 對應原 Utils.Player.playerOverAir()
     * 檢查玩家腳下是否為空氣
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
        // 可選：模組關閉時釋放 Jump 按鍵
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
    }
}
