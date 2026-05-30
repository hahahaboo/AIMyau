package myau.module.modules;

import myau.event.EventTarget;
import myau.events.AttackEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.util.RandomUtil;
import myau.util.RotationUtil;
import net.minecraft.entity.EntityLivingBase;

public class HitSelect extends Module {

    public final FloatProperty chance;
    public final BooleanProperty bestTiming;

    private EntityLivingBase currentTarget = null;
    private boolean inBestTimingMode = false;   // 是否處於最佳時機模式（只判斷4）
    private boolean shouldHit = false;

    public HitSelect() {
        super("HitSelect", "Selective hitting with timing and chance control", Category.COMBAT, 0, false, false);
        
        this.chance = new FloatProperty("chance", 80.0F, 0.0F, 100.0F);
        this.bestTiming = new BooleanProperty("best-timing", true);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            return;
        }

        EntityLivingBase target = (EntityLivingBase) event.getTarget();
        if (target == null) {
            resetState();
            return;
        }

        // 目標變更時重設
        if (currentTarget != target) {
            resetState();
            currentTarget = target;
        }

        // 如果處於 best-timing 模式，直接執行判斷4
        if (inBestTimingMode) {
            if (!checkJudgment4(target)) {
                event.setCancelled(true);
                inBestTimingMode = false;   // 失敗後退出最佳模式
            } else {
                shouldHit = true;
                // 繼續允許攻擊
            }
            return;
        }

        // ==================== 正常完整流程 ====================

        // 判斷1: A在hurttime 且 不在地上
        boolean judgment1 = mc.thePlayer.hurtTime > 0 && !mc.thePlayer.onGround;
        if (!judgment1) {
            event.setCancelled(true);
            return;
        }

        // 判斷2: chance 是否觸發
        if (!RandomUtil.chance(this.chance.getValue() / 100.0)) {
            event.setCancelled(true);
            return;
        }

        // 判斷3: A是否在下落 (falling)
        boolean isFalling = mc.thePlayer.motionY < 0;
        if (!isFalling) {
            event.setCancelled(true);
            return;
        }

        // 判斷4: B不在hurttime 且 可被打到 (distance <= 3)
        if (!checkJudgment4(target)) {
            event.setCancelled(true);
            return;
        }

        // 可以攻擊
        shouldHit = true;

        // 判斷5: best-timing
        if (this.bestTiming.getValue()) {
            inBestTimingMode = true;   // 進入最佳時機模式，下次直接判斷4
        } else {
            resetState();   // false 則下次從頭開始
        }
    }

    private boolean checkJudgment4(EntityLivingBase target) {
        return target.hurtTime <= 0 && RotationUtil.distanceToEntity(target) <= 3.0;
    }

    private void resetState() {
        currentTarget = null;
        inBestTimingMode = false;
        shouldHit = false;
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
