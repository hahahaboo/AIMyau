package myau.events;

import myau.event.events.callables.EventCancellable;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * 對應原本 Souvenir EarlyPlaceEvent。
 * 在 onLivingUpdate 早期發送，用於 GRIM 等需要「極早放置」的 Scaffold 模式。
 */
public class EarlyPlaceEvent extends EventCancellable {
    private final EntityPlayerSP player;
    private final float yaw;
    private final float pitch;
    private boolean placed;

    public EarlyPlaceEvent(EntityPlayerSP player, float yaw, float pitch) {
        this.player = player;
        this.yaw = yaw;
        this.pitch = pitch;
        this.placed = false;
    }

    public EntityPlayerSP player() {
        return this.player;
    }

    public float yaw() {
        return this.yaw;
    }

    public float pitch() {
        return this.pitch;
    }

    public boolean placed() {
        return this.placed;
    }

    public void placed(boolean value) {
        this.placed = value;
    }

    public void markPlaced() {
        this.placed = true;
    }
}
