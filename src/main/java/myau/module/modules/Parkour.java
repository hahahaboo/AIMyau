package myau.module.modules;

import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.util.KeyBindUtil;
import myau.util.PlayerUtil;
import myau.util.TimerUtil;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.AxisAlignedBB;

public class Parkour extends Module {

    public final FloatProperty edgeDistance = new FloatProperty("Edge Distance", 0.08f, 0.01f, 0.4f);
    public final BooleanProperty onlyMoving = new BooleanProperty("only-moving", true);
    public final BooleanProperty notOnSneaking = new BooleanProperty("not-on-sneaking", true);

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

        // 蹲下時不觸發
        if (notOnSneaking.getValue() && PlayerUtil.isSneaking()) {
            return;
        }

        // 釋放跳躍鍵
        if (!KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) && cd.hasTimeElapsed(10)) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        }

        EntityPlayerSP player = mc.thePlayer;

        if (player.onGround 
                && (player.motionX != 0 || player.motionZ != 0 || !onlyMoving.getValue())
                && isAtRealEdge(player)) {
            
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            cd.reset();
        }
    }

    /**
     * 更精準的邊緣偵測 - 讓玩家可以更靠近邊緣才跳
     */
    private boolean isAtRealEdge(EntityPlayerSP player) {
        double x = player.posX;
        double y = player.posY - 0.1;   // 稍微往下偵測腳下
        double z = player.posZ;
        
        // 越小越靠近邊緣（關鍵參數）
        double halfWidth = (player.width / 2.0) - edgeDistance.getValue();

        AxisAlignedBB checkBox = new AxisAlignedBB(
                x - halfWidth, y - 0.5, z - halfWidth,
                x + halfWidth, y + 0.1, z + halfWidth
        );

        // 如果縮小後的碰撞箱下方沒有方塊碰撞 = 即將到邊緣
        return mc.theWorld.getCollidingBoundingBoxes(player, checkBox).isEmpty();
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
