package play.model.components;

public class BitPacket {
        public final String color;
        public final String sourceId; // Track original heavy packet source
        public final int originalHeavyPacketSize;

        public BitPacket(String color, String sourceId, int originalHeavyPacketSize) {
                this.color = color;
                this.sourceId = sourceId;
                this.originalHeavyPacketSize = originalHeavyPacketSize;
        }
}
