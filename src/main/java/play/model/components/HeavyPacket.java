package play.model.components;

public class HeavyPacket {
        public final int size;
        public final String color;
        public final int originalSize; // For loss calculation
        public final String sourceId; // To track which producer created it

        public HeavyPacket(int size, String color, int originalSize, String sourceId) {
                this.size = size;
                this.color = color;
                this.originalSize = originalSize;
                this.sourceId = sourceId;
        }
}
