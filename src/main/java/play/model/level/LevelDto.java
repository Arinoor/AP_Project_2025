package play.model.level;

import java.util.ArrayList;
import java.util.List;

public final class LevelDto {
        public double totalWire = 3000;
        public int timeLimitSeconds = 120;
        public final List<DeviceDto> devices = new ArrayList<>();
        public final List<PortDto> ports = new ArrayList<>();

        public static final class DeviceDto {
                public String id;
                public double x;
                public double y;
                public boolean reference = false;

                // Producer (optional)
                public ProducerDto producer; // null if absent
        }

        public static final class ProducerDto {
                public double interval = 1.0;
                public Integer quotaSquare;   // null => unlimited
                public Integer quotaTriangle; // null => unlimited
        }

        public static final class PortDto {
                public String id;
                public String deviceId;
                public double x;
                public double y;
                public String shape = "square"; // "square" or "triangle"
                public String io = "in";        // "in" or "out"
                public boolean reference = false; // optional, rare
                public boolean producer = false;  // optional, rare
                public Double interval;           // if producer=true and no device-level producer
        }
}
