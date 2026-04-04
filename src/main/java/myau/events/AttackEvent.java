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
}
