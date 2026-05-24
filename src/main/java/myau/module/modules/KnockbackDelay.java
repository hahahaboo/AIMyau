package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.module.Category;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.ItemUtil;
import myau.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.MovingObjectPosition;

public class KnockbackDelay extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private final IntProperty airDelay = new IntProperty("AirDelay", 90, 0, 1000);
    private final IntProperty groundDelay = new IntProperty("GroundDelay", 0, 0, 1000);
    private final IntProperty chance = new IntProperty("Chance", 100, 0, 100);
    private final BooleanProperty realtimeDamage = new BooleanProperty("RealtimeDamage", true);
    private final BooleanProperty requireTarget = new BooleanProperty("RequireTarget", false);
    private final BooleanProperty onlySwords = new BooleanProperty("OnlySwords", false);

    public KnockbackDelay() {
        super("KnockbackDelay", "延遲擊退封包", Category.COMBAT, 0, false, false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{airDelay.getValue() + "ms / " + groundDelay.getValue() + "ms"};
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
    }

    @Override
    public void onDisabled() {
        reset();
        super.onDisabled();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (!isEnabled() || mc.isSingleplayer() || mc.thePlayer.ticksExisted < 20) {
            reset();
            return;
        }

        // 檢查是否需要自動關閉 blink（使用 ticksExisted 作為計時器）
        if (Myau.blinkManager.getBlinkingModule() == BlinkModules.KNOCKBACK_DELAY) {
            boolean isOnGround = mc.thePlayer.onGround;
            int delay = isOnGround ? groundDelay.getValue() : airDelay.getValue();

            // 使用玩家 ticksExisted 計算延遲時間（每 tick ≈ 50ms）
            if (delay <= 0 || (mc.thePlayer.ticksExisted - startBlinkTick) * 50 >= delay) {
                reset();
            }
        }
    }

    private int startBlinkTick = 0; // 用來記錄開始 blink 的 tick

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        reset();
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.isSingleplayer() || mc.thePlayer.ticksExisted < 20) return;

        Packet<?> packet = event.getPacket();

        // 重要封包直接放行
        if (packet instanceof S07PacketRespawn ||
            packet instanceof S03PacketTimeUpdate ||
            packet instanceof S06PacketUpdateHealth ||
            packet instanceof S13PacketDestroyEntities ||
            packet instanceof S02PacketChat ||
            packet instanceof S25PacketBlockBreakAnim ||
            packet instanceof S2FPacketSetSlot) {
            return;
        }

        if (packet instanceof S2BPacketChangeGameState) {
            int state = ((S2BPacketChangeGameState) packet).getGameState();
            if (state == 1 || state == 2 || state == 7 || state == 8) return;
        }

        if (packet instanceof S2CPacketSpawnGlobalEntity) {
            if (((S2CPacketSpawnGlobalEntity) packet).func_149053_g() == 1) return; // 雷
        }

        if (packet instanceof S29PacketSoundEffect) {
            if ("ambient.weather.thunder".equalsIgnoreCase(((S29PacketSoundEffect) packet).getSoundName())) return;
        }

        // Realtime Damage（即時傷害顯示）
        if (realtimeDamage.getValue() && packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus status = (S19PacketEntityStatus) packet;
            if (status.getOpCode() == 2 && status.getEntity(mc.theWorld) == mc.thePlayer) {
                return;
            }
        }

        // S08 Setback 保護
        if (packet instanceof S08PacketPlayerPosLook) {
            if (Myau.blinkManager.getBlinkingModule() == BlinkModules.KNOCKBACK_DELAY) {
                reset();
                return;
            }
        }

        // ==================== 核心邏輯 ====================
        // 收到自己的擊退封包 → 啟動 Blink
        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
            if (vel.getEntityID() == mc.thePlayer.getEntityId() && shouldActivate()) {
                Myau.blinkManager.setBlinkState(true, BlinkModules.KNOCKBACK_DELAY);
                startBlinkTick = mc.thePlayer.ticksExisted; // 記錄開始時間
            }
        }

        // 如果正在 KnockbackDelay Blink 中，delay 此封包（client + server）
        if (Myau.blinkManager.getBlinkingModule() == BlinkModules.KNOCKBACK_DELAY) {
            if (event.getType() == EventType.RECEIVE || event.getType() == EventType.SEND) {
                event.setCancelled(true);
                Myau.blinkManager.offerPacket(packet);
            }
        }
    }

    private boolean shouldActivate() {
        if (RandomUtil.nextInt(0, 100) > chance.getValue()) return false;
        if (requireTarget.getValue() && findTarget() == null) return false;
        if (onlySwords.getValue() && !ItemUtil.isHoldingSword()) return false;
        return true;
    }

    private void reset() {
        if (Myau.blinkManager.getBlinkingModule() == BlinkModules.KNOCKBACK_DELAY) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.KNOCKBACK_DELAY);
        }
        startBlinkTick = 0;
    }

    private Entity findTarget() {
        // 使用 modules map 直接取（正確方式）
        Module kaModule = Myau.moduleManager.modules.get(KillAura.class);
        if (kaModule instanceof KillAura) {
            KillAura ka = (KillAura) kaModule;
            if (ka.isEnabled() && ka.getTarget() != null) {
                return ka.getTarget();
            }
        }

        // 滑鼠指向目標
        MovingObjectPosition ray = mc.objectMouseOver;
        if (ray != null && ray.entityHit != null) {
            return ray.entityHit;
        }
        return null;
    }
}
