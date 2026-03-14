package myau.util.backtrack;

import net.minecraft.network.Packet;

public class QueueData {

    public Packet<?> packet;
    public long time;

    public QueueData(Packet<?> packet, long time) {
        this.packet = packet;
        this.time = time;
    }

}
