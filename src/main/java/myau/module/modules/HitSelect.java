package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.potion.Potion;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.HashMap;

/**
 * Hit Select：在目標 hurtTime 過高時暫緩攻擊，並與 KillAura / AutoClicker 協作。
 * 邏輯移植自 OpenB4 HitSelectModule，並適配 AIMyau 的 Event / Property 系統。
 */
public class HitSelect extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ===== Settings（對應原版 Slider/Button）=====
    /** 0 = 不啟用 pause；數值越大越早開始 hold（hurtTime 門檻越高） */
    public final IntProperty pause = new IntProperty("pause", 200, 0, 500); // 單位約 ms 概念，對應 hurtLimit
    /** 至少要連續 combo 幾下才開始 hold；0 = 不限制 */
    public final IntProperty onlyInCombo = new IntProperty("only-in-combo", 0, 0, 10);
    public final BooleanProperty disableOnLowHealth = new BooleanProperty("disable-on-low-health", false);
    public final BooleanProperty fakeSwing = new BooleanProperty("fake-swing", true);
    public final BooleanProperty forceFirstHit = new BooleanProperty("force-first-hit", false);
    public final BooleanProperty onlyCriticalHits = new BooleanProperty("only-critical-hits", false);
    public final BooleanProperty onlyWhileJumping = new BooleanProperty("only-while-jumping", false);
    public final BooleanProperty resetComboOnSwap = new BooleanProperty("reset-combo-on-swap", true);
    public final BooleanProperty resetOnDamage = new BooleanProperty("reset-on-damage", true);
    public final BooleanProperty manual = new BooleanProperty("manual", true);
    public final BooleanProperty autoClicker = new BooleanProperty("auto-clicker", true);
    public final BooleanProperty killAura = new BooleanProperty("kill-aura", true);

    // ===== 狀態 =====
    private int combo;
    private int lastTargetId = -1;
    private long lastAttack;
    private long damagedAt;
    private int hurtLimit;
    private final HashMap<Integer, Long> firstHits = new HashMap<>();

    public HitSelect() {
        super("HitSelect", "Filters unnecessary hits based on hurtTime / combo", Category.COMBAT, 0, false, false);
    }

    public static boolean on() {
        HitSelect hs = (HitSelect) Myau.moduleManager.modules.get(HitSelect.class);
        return hs != null && hs.isEnabled();
    }

    public static HitSelect get() {
        return (HitSelect) Myau.moduleManager.modules.get(HitSelect.class);
    }

    /** 給 KillAura 用：若應 hold 則回傳 true（不應攻擊） */
    public static boolean holdForKillAura(Entity e) {
        HitSelect hs = get();
        if (hs == null || !hs.isEnabled() || !hs.killAura.getValue()) {
            return false;
        }
        return hs.hold(e);
    }

    /** 核心 hold 判斷 */
    public boolean hold(Entity entity) {
        if (!this.isEnabled() || !(entity instanceof EntityLivingBase)) {
            return false;
        }
        if (disableOnLowHealth.getValue()
                && !mc.thePlayer.capabilities.isCreativeMode
                && mc.thePlayer.getHealth() <= 4.0f) {
            return false;
        }
        if (onlyInCombo.getValue() > 0 && combo < onlyInCombo.getValue()) {
            return false;
        }
        if (onlyWhileJumping.getValue() && !GameSettings.isKeyDown(mc.gameSettings.keyBindJump)) {
            return false;
        }
        if (damagedAt != 0L && Math.abs(System.currentTimeMillis() - damagedAt) < 170L) {
            return false;
        }

        boolean shouldHold = shouldHold((EntityLivingBase) entity);

        if (shouldHold && forceFirstHit.getValue()) {
            long now = System.currentTimeMillis();
            Long t = firstHits.get(entity.getEntityId());
            // 超過 10 秒沒打過這個目標 → 強制允許第一下
            if (t == null || now - t > 10000L) {
                shouldHold = false;
            }
            firstHits.put(entity.getEntityId(), now);
        }

        if (shouldHold && fakeSwing.getValue()) {
            // 原版 PlayerUtils.restartSwing() → 這裡簡單再 swing 一次製造視覺
            mc.thePlayer.swingItem();
        }
        return shouldHold;
    }

    private boolean shouldHold(EntityLivingBase e) {
        // pause > 0 時：hurtTime 高於門檻就 hold
        if (pause.getValue() > 0 && e.hurtTime > hurtLimit) {
            return true;
        }
        // onlyCriticalHits：非 crit 條件就 hold（即不攻擊）
        if (onlyCriticalHits.getValue()) {
            boolean canCrit = !mc.thePlayer.onGround
                    && mc.thePlayer.fallDistance != 0.0f
                    && !mc.thePlayer.isInWater()
                    && mc.thePlayer.ridingEntity == null
                    && !mc.thePlayer.isPotionActive(Potion.blindness)
                    && !mc.thePlayer.isOnLadder();
            return !canCrit;
        }
        return false;
    }

    // ===== 事件 =====

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;

        // 送出攻擊 → 記錄 target / time
        if (event.getType() == EventType.SEND) {
            if (event.getPacket() instanceof C09PacketHeldItemChange) {
                if (resetComboOnSwap.getValue()) {
                    combo = 0;
                }
            } else if (event.getPacket() instanceof C02PacketUseEntity) {
                C02PacketUseEntity p = (C02PacketUseEntity) event.getPacket();
                if (onlyInCombo.getValue() > 0
                        && p.getAction() == C02PacketUseEntity.Action.ATTACK) {
                    Entity e = p.getEntityFromWorld(mc.theWorld);
                    if (e != null) {
                        lastTargetId = e.getEntityId();
                        lastAttack = System.currentTimeMillis();
                    }
                }
            }
        }

        // 收到 hurt status → 增加 combo
        if (event.getType() == EventType.RECEIVE
                && event.getPacket() instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus p = (S19PacketEntityStatus) event.getPacket();
            if (onlyInCombo.getValue() > 0
                    && p.getOpCode() == 2
                    && Math.abs(System.currentTimeMillis() - lastAttack) < 750L) {
                Entity e = p.getEntity(mc.theWorld);
                if (e != null && e.getEntityId() == lastTargetId) {
                    ++combo;
                }
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (!this.isEnabled() || event.isCancelled()) return;

        // AutoClicker 協作：若 AutoClicker 正在點、且本模組的 autoClicker 開關關著，就不管
        AutoClicker ac = (AutoClicker) Myau.moduleManager.modules.get(AutoClicker.class);
        if (!autoClicker.getValue() && ac != null && ac.isEnabled()
                && mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }
        if (!manual.getValue()) return;

        if (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectType.ENTITY) {
            return;
        }
        if (hold(mc.objectMouseOver.entityHit)) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        // 對應原 guiUpdate：hurtLimit = 10 - pause/50
        hurtLimit = 10 - pause.getValue() / 50;

        if (combo > 0 && Math.abs(System.currentTimeMillis() - lastAttack) > 1250L) {
            combo = 0;
        }
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled()) return;
        combo = 0;
        if (resetOnDamage.getValue()) {
            KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
            if (ka != null && ka.isEnabled() && ka.getTarget() != null) {
                // 原 forceAttacks(2)：這裡把 delay 清零讓它立刻能打
                // 若你之後在 KillAura 加 forceAttackCount，可在這裡呼叫
                // 目前用公開方法較安全，見下方 KillAura 修改建議
                ka.forceNextAttacks(2);
            } else {
                damagedAt = System.currentTimeMillis();
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        combo = 0;
        firstHits.clear();
        damagedAt = 0L;
        lastTargetId = -1;
    }

    @Override
    public void onDisabled() {
        combo = 0;
        damagedAt = 0L;
        firstHits.clear();
        lastTargetId = -1;
    }

    @Override
    public String[] getSuffix() {
        if (pause.getValue() <= 0) {
            return new String[]{"off"};
        }
        return new String[]{String.valueOf(pause.getValue())};
    }
}
