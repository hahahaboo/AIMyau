package myau.module.modules;

import myau.event.EventTarget;
import myau.events.AttackEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import myau.util.RotationUtil;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;

public class HitSelect extends Module {

    public final PercentProperty chance;
    public final BooleanProperty bestTiming;

    private EntityLivingBase currentTarget = null;
    private boolean inBestTimingMode = false;
    private boolean readyToHit = false;

    private int chanceCounter = 0;
    private int prevHurtTime = 0;   // 用來偵測玩家是否剛受到傷害

    public HitSelect() {
        super("HitSelect", "Selective hitting with timing and chance control", Category.COMBAT, 0, false, false);
        
        this.chance = new PercentProperty("chance", 80);
        this.bestTiming = new BooleanProperty("best-timing", true);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            return;
        }

        // 傷害偵測：玩家受到傷害時重設 inBestTimingMode
        if (mc.thePlayer.hurtTime > 0 && prevHurtTime == 0) {
            resetBestTimingMode();
        }
        prevHurtTime = mc.thePlayer.hurtTime;

        EntityLivingBase target = getTarget(event);
        if (target == null) {
            resetState();
            event.setCancelled(true);
            return;
        }

        // 目標變更時重設
        if (currentTarget != target) {
            resetState();
            currentTarget = target;
        }

        // ==================== 主要邏輯 ====================

        if (inBestTimingMode) {
            // best-timing 模式：只持續檢查判斷4，直到滿足為止
            if (checkJudgment4(target)) {
                readyToHit = true;
            } else {
                readyToHit = false;
            }
        } else {
            // 正常模式：完整執行判斷1~4
            readyToHit = performFullCheck(target);
        }

        // 如果還沒準備好，就持續 cancel hit
        if (!readyToHit) {
            event.setCancelled(true);
            return;
        }

        // 可以 hit
        if (this.bestTiming.getValue()) {
            inBestTimingMode = true;
        } else {
            resetState(); // 非 best-timing 模式下，打完一次就重置
        }
    }

    /**
     * 執行完整判斷流程 (1→2→3→4)
     */
    private boolean performFullCheck(EntityLivingBase target) {
        // 判斷1: A在hurttime 且 不在地上
        if (!(mc.thePlayer.hurtTime > 0 && !mc.thePlayer.onGround)) {
            return false;
        }

        // 判斷2: chance (Velocity 風格)
        this.chanceCounter = this.chanceCounter % 100 + this.chance.getValue();
        if (this.chanceCounter < 100) {
            return false;
        }
        this.chanceCounter = 0;

        // 判斷3: A正在下落
        if (!(mc.thePlayer.motionY < 0)) {
            return false;
        }

        // 判斷4: B不在hurttime 且 距離 <= 3
        return checkJudgment4(target);
    }

    private boolean checkJudgment4(EntityLivingBase target) {
        return target.hurtTime <= 0 && RotationUtil.distanceToEntity(target) <= 3.0;
    }

    private EntityLivingBase getTarget(AttackEvent event) {
        EntityLivingBase target = (EntityLivingBase) event.getTarget();
        if (target != null) {
            return target;
        }

        // Fallback: mouse over
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            if (mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
                return (EntityLivingBase) mc.objectMouseOver.entityHit;
            }
        }
        return null;
    }

    /** 僅重設最佳時機模式（玩家受傷時使用） */
    private void resetBestTimingMode() {
        inBestTimingMode = false;
        readyToHit = false;
    }

    private void resetState() {
        currentTarget = null;
        inBestTimingMode = false;
        readyToHit = false;
        chanceCounter = 0;
        prevHurtTime = 0;
    }

    @Override
    public void onEnabled() {
        resetState();
    }

    @Override
    public void onDisabled() {
        resetState();
    }
}
