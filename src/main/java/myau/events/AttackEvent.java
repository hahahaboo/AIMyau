package myau.events;

import lombok.Getter;
import lombok.Setter;
import myau.event.events.Event;
import net.minecraft.entity.Entity;

@Getter
public class AttackEvent implements Event {
    private final Entity target;
    @Setter
    private boolean cancelled;

    public AttackEvent(Entity target) {
        this.target = target;
        this.cancelled = false;
    }

     public Entity getTarget() {
        return this.target;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
