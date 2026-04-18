package myau.module.modules;

import myau.event.EventTarget;
import myau.events.AttackEvent;
import myau.events.Render2DEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.TimerUtil;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class Wtap extends Module {

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Attack", "Hurt"});
    public final BooleanProperty onlyCombo = new BooleanProperty("Only-combo", true);
    public final BooleanProperty onlySword = new BooleanProperty("Only-sword", false);

    public final IntProperty durationMin = new IntProperty("min-duration", 30, 1, 500);
    public final IntProperty durationMax = new IntProperty("max-duration", 40, 1, 500);
    public final IntProperty delayMin = new IntProperty("min-delay", 20, 1, 500);
    public final IntProperty delayMax = new IntProperty("max-delay", 30, 1, 500);
    public final IntProperty hitMin = new IntProperty("min-hit", 1, 1, 10);
    public final IntProperty hitMax = new IntProperty("max-hit", 1, 1, 10);

    public final PercentProperty chance = new PercentProperty("Chance", 100);

    public final FloatProperty range = new FloatProperty("Range", 3f, 1f, 6f);

    public final BooleanProperty dynamic = new BooleanProperty("Dynamic", false);
    public final FloatProperty sensitivity = new FloatProperty("Sensitivity", 1f, 0f, 5f, () -> dynamic.getValue());

    private enum WtapState {
        NONE, WAITINGTOTAP, TAPPING
    }

    private WtapState state = WtapState.NONE;
    private final TimerUtil timer = new TimerUtil();
    private long currentCooldownMs = 0L;
    private Entity target;
    private int hits = 0;
    private int rhit = 0;
    private boolean hurtTriggered = false;

    public Wtap() {
        super("WTap", "WTap", Category.COMBAT, 0, false, false);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled()) return;
        target = event.getTarget();
        if (mode.getModeString().equals("Attack")) {
            wTap();
        }
    }

    @Override
    public void onDisabled() {
        state = WtapState.NONE;
        target = null;
        hits = 0;
        rhit = 0;
        hurtTriggered = false;
        timer.reset();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled()) return;
        // Hurt 模式偵測（Render2D 每幀檢查）
        if (mode.getModeString().equals("Hurt") && target instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) target;
            if (living.hurtTime > 0 && living.hurtTime == living.maxHurtTime && !hurtTriggered) {
                hurtTriggered = true;
                wTap();
            }
        } else if (target == null || !(target instanceof EntityLivingBase) || ((EntityLivingBase) target).hurtTime == 0) {
            hurtTriggered = false;
        }

        // 狀態機（與舊版完全一致）
        if (state == WtapState.NONE) return;

        if (state == WtapState.WAITINGTOTAP && timer.hasTimeElapsed(currentCooldownMs)) {
            startCombo();
        } else if (state == WtapState.TAPPING && timer.hasTimeElapsed(currentCooldownMs)) {
            finishCombo();
        }
    }

    private void wTap() {
        if (state != WtapState.NONE) return;

        if (!(Math.random() <= chance.getValue() / 100.0)) {
            hits++;
        }

        if (mc.thePlayer.getDistanceToEntity(target) > range.getValue()
                || (onlyCombo.getValue() && !(target instanceof EntityPlayer))
                || (onlySword.getValue() && !(mc.thePlayer.getCurrentEquippedItem() != null && mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword))
                || !(rhit >= hits)) {
            return;
        }

        trystartCombo();
    }

    private void trystartCombo() {
        state = WtapState.WAITINGTOTAP;
        double action = ThreadLocalRandom.current().nextDouble((double) delayMin.getValue(), (double) delayMax.getValue() + 0.01);
        currentCooldownMs = (long) action;
        timer.reset();
    }

    private void startCombo() {
        state = WtapState.TAPPING;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);

        double cd = ThreadLocalRandom.current().nextDouble((double) durationMin.getValue(), (double) durationMax.getValue() + 0.01);
        if (dynamic.getValue()) {
            double dist = mc.thePlayer.getDistanceToEntity(target);
            if (dist < 3) {
                cd += (3 - dist) * sensitivity.getValue() * 10;
            }
        }

        currentCooldownMs = (long) cd;
        timer.reset();
    }

    private void finishCombo() {
        if (Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
        }
        state = WtapState.NONE;
        hits = 0;

        int minHits = hitMin.getValue().intValue();
        int maxHits = hitMax.getValue().intValue();
        int rangeHits = (maxHits - minHits + 1);
        if (rangeHits < 1) rangeHits = 1;
        rhit = ThreadLocalRandom.current().nextInt(rangeHits) + minHits;
    }
}
