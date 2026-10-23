package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KnockbackEvent;
import myau.events.LeftClickMouseEvent;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
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
 * Hit Select：依 hurtTime / combo / 首擊等待等條件暫緩攻擊，
 * 並與 KillAura、手動點擊、AutoClicker 協作。
 * 邏輯移植自 OpenB4 HitSelectModule，適配 AIMyau Event / Property。
 */
public class HitSelect extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double WAIT_RESET_DISTANCE = 4.0;
    // ===== Settings =====
    public final IntProperty pause = new IntProperty("pause", 200, 0, 500);
    public final IntProperty onlyInCombo = new IntProperty("only-in-combo", 0, 0, 10);
    public final IntProperty waitFirstHit = new IntProperty("wait-first-hit", 0, 0, 500, () -> !this.onlyCriticalHits.getValue());
    public final BooleanProperty disableOnLowHealth = new BooleanProperty("disable-on-low-health", false);
    public final BooleanProperty fakeSwing = new BooleanProperty("fake-swing", true);
    public final BooleanProperty forceFirstHit = new BooleanProperty("force-first-hit", false);
    public final BooleanProperty onlyCriticalHits = new BooleanProperty("only-critical-hits", false);
    public final BooleanProperty resetComboOnSwap = new BooleanProperty("reset-combo-on-swap", true);
    public final BooleanProperty resetOnDamage = new BooleanProperty("reset-on-damage", true);
    public final BooleanProperty manual = new BooleanProperty("manual", true);
    public final BooleanProperty autoClicker = new BooleanProperty("auto-clicker", true);
    public final BooleanProperty killAura = new BooleanProperty("kill-aura", true);

    // ===== Runtime state =====
    private int combo;
    private int lastTargetId = -1;
    private long lastAttack;
    private long damagedAt;
    private int hurtLimit;
    private final HashMap<Integer, Long> firstHits = new HashMap<>();

    // wait-first-hit
    private int waitTargetId = -1;
    private long waitStartMs;
    private boolean waitUnlocked;
    private boolean wasBeyond4;

    public HitSelect() {
        super("HitSelect", "Filters unnecessary hits based on hurtTime / combo / first-hit wait", Category.COMBAT, 0, false, false);
    }

    public static boolean on() {
        HitSelect hs = get();
        return hs != null && hs.isEnabled();
    }

    public static HitSelect get() {
        return (HitSelect) Myau.moduleManager.modules.get(HitSelect.class);
    }

    /** 給 KillAura：應 hold 時回傳 true（不應攻擊） */
    public static boolean holdForKillAura(Entity e) {
        HitSelect hs = get();
        if (hs == null || !hs.isEnabled() || !hs.killAura.getValue()) {
            return false;
        }
        return hs.hold(e);
    }

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
        if (damagedAt != 0L && Math.abs(System.currentTimeMillis() - damagedAt) < 170L) {
            return false;
        }

        // wait-first-hit：僅 only-critical-hits 關閉時
        if (!onlyCriticalHits.getValue() && waitFirstHit.getValue() > 0) {
            if (updateWaitFirstHit(entity)) {
                if (fakeSwing.getValue()) {
                    mc.thePlayer.swingItem();
                }
                return true;
            }
        }

        boolean shouldHold = shouldHold((EntityLivingBase) entity);

        if (shouldHold && forceFirstHit.getValue()) {
            long now = System.currentTimeMillis();
            Long t = firstHits.get(entity.getEntityId());
            if (t == null || now - t > 10000L) {
                shouldHold = false;
            }
            firstHits.put(entity.getEntityId(), now);
        }

        if (shouldHold && fakeSwing.getValue()) {
            mc.thePlayer.swingItem();
        }
        return shouldHold;
    }

    /**
     * 更新 wait-first-hit 狀態。
     * @return true = 仍在等待中，應 hold
     */
    private boolean updateWaitFirstHit(Entity entity) {
        int id = entity.getEntityId();
        long now = System.currentTimeMillis();
        double dist = mc.thePlayer.getDistanceToEntity(entity);

        if (id != waitTargetId) {
            waitTargetId = id;
            waitStartMs = now;
            waitUnlocked = false;
            wasBeyond4 = dist > WAIT_RESET_DISTANCE;
        } else if (wasBeyond4 && dist <= WAIT_RESET_DISTANCE) {
            // 同一人從 4 格外重新進入 → 重開等待
            waitStartMs = now;
            waitUnlocked = false;
            wasBeyond4 = false;
        } else if (dist > WAIT_RESET_DISTANCE) {
            wasBeyond4 = true;
        }

        return !waitUnlocked && (now - waitStartMs) < waitFirstHit.getValue();
    }

    private boolean shouldHold(EntityLivingBase e) {
        if (pause.getValue() > 0 && e.hurtTime > hurtLimit) {
            return true;
        }
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

    private void resetWaitState() {
        waitTargetId = -1;
        waitStartMs = 0L;
        waitUnlocked = false;
        wasBeyond4 = false;
    }

    private void resetAllState() {
        combo = 0;
        damagedAt = 0L;
        lastTargetId = -1;
        lastAttack = 0L;
        firstHits.clear();
        resetWaitState();
    }

    // ===== Events =====

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) {
            return;
        }

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
        if (!this.isEnabled() || event.isCancelled()) {
            return;
        }

        AutoClicker ac = (AutoClicker) Myau.moduleManager.modules.get(AutoClicker.class);
        if (!autoClicker.getValue()
                && ac != null
                && ac.isEnabled()
                && mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }
        if (!manual.getValue()) {
            return;
        }
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
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        hurtLimit = 10 - pause.getValue() / 50;

        if (combo > 0 && Math.abs(System.currentTimeMillis() - lastAttack) > 1250L) {
            combo = 0;
        }

        // 範圍外追蹤：供「從 4 格外重進」重開 wait-first-hit
        if (!onlyCriticalHits.getValue()
                && waitFirstHit.getValue() > 0
                && waitTargetId != -1
                && mc.theWorld != null
                && mc.thePlayer != null) {
            Entity e = mc.theWorld.getEntityByID(waitTargetId);
            if (e == null || mc.thePlayer.getDistanceToEntity(e) > WAIT_RESET_DISTANCE) {
                wasBeyond4 = true;
            }
        }
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        combo = 0;

        // wait-first-hit：等待期間被打 → 解鎖
        if (!onlyCriticalHits.getValue() && waitFirstHit.getValue() > 0) {
            waitUnlocked = true;
        }

        if (resetOnDamage.getValue()) {
            KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
            if (ka != null && ka.isEnabled() && ka.getTarget() != null) {
                ka.forceNextAttacks(2);
            } else {
                damagedAt = System.currentTimeMillis();
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        resetAllState();
    }

    @Override
    public void onDisabled() {
        resetAllState();
    }

    @Override
    public String[] getSuffix() {
        if (pause.getValue() <= 0 && waitFirstHit.getValue() <= 0) {
            return new String[]{"off"};
        }
        if (waitFirstHit.getValue() > 0 && !onlyCriticalHits.getValue()) {
            return new String[]{pause.getValue() + "/" + waitFirstHit.getValue()};
        }
        return new String[]{String.valueOf(pause.getValue())};
    }
}
