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
import myau.util.TimerUtil;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class Wtap extends Module {

    public final ModeProperty eventType = new ModeProperty("Event", 0, new String[]{"Attack", "Hurt"});
    public final BooleanProperty onlyPlayers = new BooleanProperty("Only combo players", true);
    public final BooleanProperty onlySword = new BooleanProperty("Only sword", false);

    public final IntProperty waitMin = new IntProperty("Release w min ms", 30, 1, 300);
    public final IntProperty waitMax = new IntProperty("Release w max ms", 40, 1, 300);
    public final IntProperty actionMin = new IntProperty("WTap after min ms", 20, 1, 300);
    public final IntProperty actionMax = new IntProperty("WTap after max ms", 30, 1, 300);
    public final IntProperty hitPerMin = new IntProperty("Once every min hits", 1, 1, 10);
    public final IntProperty hitPerMax = new IntProperty("Once every max hits", 1, 1, 10);

    public final FloatProperty chance = new FloatProperty("Chance %", 100f, 0f, 100f);
    public final FloatProperty range = new FloatProperty("Range", 3f, 1f, 6f);

    public final BooleanProperty dynamic = new BooleanProperty("Dynamic tap time", false);
    public final FloatProperty tapMultiplier = new FloatProperty("wait time sensitivity", 1f, 0f, 5f);

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
        target = event.getTarget();
        if (eventType.getModeString().equals("Attack")) {
            wTap();
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        // Hurt 模式偵測（Render2D 每幀檢查）
        if (eventType.getModeString().equals("Hurt") && target instanceof EntityLivingBase) {
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

        if (!(Math.random() <= chance.getValue() / 100)) {
            hits++;
        }

        if (mc.thePlayer.getDistanceToEntity(target) > range.getValue()
                || (onlyPlayers.getValue() && !(target instanceof EntityPlayer))
                || (onlySword.getValue() && !(mc.thePlayer.getCurrentEquippedItem() != null && mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword))
                || !(rhit >= hits)) {
            return;
        }

        trystartCombo();
    }

    private void trystartCombo() {
        state = WtapState.WAITINGTOTAP;
        double action = ThreadLocalRandom.current().nextDouble((double) actionMin.getValue(), (double) actionMax.getValue() + 0.01);
        currentCooldownMs = (long) action;
        timer.reset();
    }

    private void startCombo() {
        state = WtapState.TAPPING;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);

        double cd = ThreadLocalRandom.current().nextDouble((double) waitMin.getValue(), (double) waitMax.getValue() + 0.01);
        if (dynamic.getValue()) {
            double dist = mc.thePlayer.getDistanceToEntity(target);
            if (dist < 3) {
                cd += (3 - dist) * tapMultiplier.getValue() * 10;
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

        int minHits = hitPerMin.getValue().intValue();
        int maxHits = hitPerMax.getValue().intValue();
        int rangeHits = (maxHits - minHits + 1);
        if (rangeHits < 1) rangeHits = 1;
        rhit = ThreadLocalRandom.current().nextInt(rangeHits) + minHits;
    }
}
