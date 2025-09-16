package play.model.components;

import play.model.core.Entity;

import java.util.ArrayList;
import java.util.List;

public class MergeSystem {
        public List<Entity> storedBitpackets = new ArrayList<>();
        public double timer = 0;
        public static final double WAIT_TIME = 5.0; // C seconds
}