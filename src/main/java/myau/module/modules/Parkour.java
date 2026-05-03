package myau.module.modules;

import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Category;
import myau.module.Module;
import myau.util.KeyBindUtil;
import myau.util.PlayerUtil;
import myau.util.TimerUtil;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

public class Parkour extends Module {

    private final TimerUtil cd = new TimerUtil();

    public Parkour() {
        super("Parkour", "自動跳躍過空隙（Parkour Helper）", Category.PLAYER, 0, false, false);
    }

    @EventTarget
    public void onTick(TickEvent e) {
        if (e.getType() != EventType.PRE) return;
        
        if (!PlayerUtil.isPlayerInGame()) {  // 使用 AIMyau 的檢查（雖然 PlayerUtil 沒有直接 isPlayerInGame，但我會用 mc 判斷）
            return;
        }

        // 模擬 original 的 firstFinish + release jump key
        if (!KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) && hasFinishedCooldown()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            resetCooldown(); // 避免重複
        }

        // 偵測需要跳躍的情況：站在地面、前面是空氣、且有水平移動
        if (mc.thePlayer.onGround 
                && isPlayerOverAir() 
                && (mc.thePlayer.motionX != 0 || mc.thePlayer.motionZ != 0)) {
            
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            cd.reset();  // 開始 10 tick cooldown
        }
    }

    private boolean hasFinishedCooldown() {
        return cd.hasTimeElapsed(10);  // 原 CoolDown(10)
    }

    private void resetCooldown() {
        // TimerUtil 已經在 reset 時更新時間
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
}
