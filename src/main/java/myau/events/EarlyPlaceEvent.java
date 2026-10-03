package myau.events;

import myau.event.events.callables.EventCancellable;

/**
 * Early place event — posted just before onUpdateWalkingPlayer
 * (aligned with correct Leader port timing).
 */
public class EarlyPlaceEvent extends EventCancellable {
    private final float yaw;
    private final float pitch;
    private boolean placed;

    public EarlyPlaceEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.placed = false;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public boolean placed() {
        return this.placed;
    }

    public void markPlaced() {
        this.placed = true;
    }
}
