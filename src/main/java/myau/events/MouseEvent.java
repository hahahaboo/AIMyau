package myau.events;

import myau.event.events.callables.EventCancellable;
import lombok.Getter;

@Getter
public class MouseEvent extends EventCancellable {
    private final int dx;
    private final int dy;
    private final int dwheel;
    private final int button;
    private final boolean buttonstate;
    private final int x;
    private final int y;

    public MouseEvent(int dx, int dy, int dwheel, int button, boolean buttonstate, int x, int y) {
        this.dx = dx;
        this.dy = dy;
        this.dwheel = dwheel;
        this.button = button;
        this.buttonstate = buttonstate;
        this.x = x;
        this.y = y;
    }
}
