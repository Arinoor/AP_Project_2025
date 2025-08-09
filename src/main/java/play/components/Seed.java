package play.components;

import play.core.Component;
import play.core.Entity;

public class Seed implements Component {
        public enum Type { SQUARE, TRIANGLE }
        public final Type type;
        public Entity currentLink; // entity that represents the link
        public double progress;    // 0..1 along link
        public double speed;       // normalized per second
        public int collisions = 0;
        public final int capacity;
        public double lateral = 0.0; // drift off the center of link
        public Seed(Type t){
                this.type = t;
                this.capacity = (t==Type.SQUARE)?2:3;
                this.speed = (t==Type.SQUARE)? 0.25 : 0.2;
                this.progress = 0;
        }
}
