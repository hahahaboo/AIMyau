package myau.event.events;

import myau.event.Event;           
import myau.event.TransferOrigin;
import net.minecraft.network.Packet;

public class PacketEvent extends Event {

    private final Packet<?> packet;
    private final TransferOrigin origin;
    private boolean cancelled = false;

    public PacketEvent(Packet<?> packet, TransferOrigin origin) {
        this.packet = packet;
        this.origin = origin;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public TransferOrigin getOrigin() {
        return origin;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}