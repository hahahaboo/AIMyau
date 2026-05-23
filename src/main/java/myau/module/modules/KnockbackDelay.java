package myau.module.modules;

import myau.Myau;
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
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.*;
import net.minecraft.util.MovingObjectPosition;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * KnockbackDelay — delays incoming velocity + transaction packets
 * to manipulate when knockback is applied to the player.
 *
 * 改用 S12PacketEntityVelocity（自己）觸發延遲，並加入 S08 setback 保護
 */
public class KnockbackDelay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final IntProperty airDelay = new IntProperty("AirDelay", 90, 0, 1000);
    private final IntProperty groundDelay = new IntProperty("GroundDelay", 0, 0, 1000);
    private final IntProperty chance = new IntProperty("Chance", 100, 0, 100);
    private final BooleanProperty realtimeDamage = new BooleanProperty("RealtimeDamage", true);
    private final BooleanProperty requireTarget = new BooleanProperty("RequireTarget", false);
    private final BooleanProperty onlySwords = new BooleanProperty("OnlySwords", false);

    private final Queue<TimedPacket> packets = new ConcurrentLinkedQueue<>();
    private boolean blink;

    public KnockbackDelay() {
        super("KnockbackDelay", " ", Category.COMBAT, 0, false, false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{airDelay.getValue() + " - " + groundDelay.getValue()};
    }

    @Override
    public void onDisabled() {
        reset();
        packets.clear();        // 額外確保完全清空佇列
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.isSingleplayer() || mc.thePlayer.ticksExisted < 20) return;

        // 【修復重點】模組被禁用時強制清理，防止卡住
        if (!isEnabled()) {
            reset();
            return;
        }

        if (mc.currentScreen != null) {
            reset();
            return;
        }

        if (!shouldActivate()) {
            reset();
            return;
        }

        int delay = mc.thePlayer.onGround ? groundDelay.getValue() : airDelay.getValue();

        if (!packets.isEmpty()) {
            handle(delay);
        }

        // 【修改後】blink 狀態只由 S12 封包控制，當佇列清空後自動關閉
        if (packets.isEmpty()) {
            blink = false;
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        reset();
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.isSingleplayer() || mc.thePlayer.ticksExisted < 20 || event.isCancelled()) return;

        Packet<?> packet = event.getPacket();

        // Always let these critical/cosmetic packets through immediately
        if (packet instanceof S07PacketRespawn) return;
        if (packet instanceof S03PacketTimeUpdate) return;
        if (packet instanceof S06PacketUpdateHealth) return;
        if (packet instanceof S13PacketDestroyEntities) return;
        if (packet instanceof S02PacketChat) return;
        if (packet instanceof S25PacketBlockBreakAnim) return;
        if (packet instanceof S2FPacketSetSlot) return;

        if (packet instanceof S2BPacketChangeGameState) {
            int state = ((S2BPacketChangeGameState) packet).getGameState();
            if (state == 1 || state == 2 || state == 7 || state == 8) return;
        }

        if (packet instanceof S2CPacketSpawnGlobalEntity) {
            if (((S2CPacketSpawnGlobalEntity) packet).func_149053_g() == 1) return;
        }

        if (packet instanceof S29PacketSoundEffect) {
            if ("ambient.weather.thunder".equalsIgnoreCase(((S29PacketSoundEffect) packet).getSoundName())) return;
        }

        // Let damage status through in realtime so hurt animation plays immediately
        if (realtimeDamage.getValue() && packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus statusPacket = (S19PacketEntityStatus) packet;
            if (statusPacket.getOpCode() == 2 && statusPacket.getEntity(mc.theWorld) == mc.thePlayer) {
                return;
            }
        }

        // 【新增】S08PacketPlayerPosLook 保護 - 收到 Setback 立刻 flush
        if (packet instanceof S08PacketPlayerPosLook) {
            if (blink && !packets.isEmpty()) {
                reset();                    // 立刻釋放所有佇列封包，避免 position corruption & infinite desync
                return;
            }
        }

        // 【新增】S12PacketEntityVelocity 自己受到擊退 → 開啟 blink
        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
            if (vel.getEntityID() == mc.thePlayer.getEntityId()) {
                blink = true;                    // 收到自己的擊退封包 → 開始延遲
            }
        }

        if (blink) {
            event.setCancelled(true);
            packets.add(new TimedPacket(packet, System.currentTimeMillis()));
        }
    }

    private boolean shouldActivate() {
        if (RandomUtil.nextInt(0, 100) > chance.getValue()) return false;

        if (requireTarget.getValue() && findTarget() == null) return false;

        if (onlySwords.getValue() && !ItemUtil.isHoldingSword()) return false;

        return true;
    }

    private void reset() {
        blink = false;
        flush();                    // 無論 blink 狀態都執行 flush
    }

    private void handle(int delay) {
        while (!packets.isEmpty()) {
            TimedPacket wrapper = packets.peek();
            if (wrapper != null && wrapper.elapsed(delay)) {
                packets.poll();
                processPacketSilent(wrapper.packet);
            } else {
                break;
            }
        }
    }

    private void flush() {
        // 【修復重點】強制釋放所有已佇列封包，解決無法正常 disable 的問題
        TimedPacket wrapper;
        while ((wrapper = packets.poll()) != null) {
            processPacketSilent(wrapper.packet);
        }
    }

    @SuppressWarnings("unchecked")
    private void processPacketSilent(Packet<?> packet) {
        try {
            if (mc.getNetHandler() != null) {
                ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class TimedPacket {
        private final Packet<?> packet;
        private final long time;

        public TimedPacket(Packet<?> packet, long time) {
            this.packet = packet;
            this.time = time;
        }

        public boolean elapsed(int delayMs) {
            return System.currentTimeMillis() - time >= delayMs;
        }
    }

    private Entity findTarget() {
        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
    
        // 使用 KillAura 已經提供的 public getter（getTarget()）
        if (ka != null && ka.isEnabled() && ka.getTarget() != null) {
            return ka.getTarget();
        }

        // Fallback: 滑鼠指向的實體
        MovingObjectPosition ray = mc.objectMouseOver;
        if (ray != null && ray.entityHit != null) {
            return ray.entityHit;
        }

        return null;
    }
}
