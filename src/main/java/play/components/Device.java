package play.components;

import play.core.Component;
import play.core.Entity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** A network device (rectangle). Holds a shared queue across its ports. */
public class Device implements Component {
        public final String id;
        public final int capacity;             // per doc: 5
        public final Deque<Entity> queue = new ArrayDeque<>(5);

        /** Filled by LevelLoader */
        public List<Entity> inputPorts;
        public List<Entity> outputPorts;

        public Device(String id, int capacity) {
                this.id = id;
                this.capacity = capacity;
        }

        public boolean enqueue(Entity seedEntity) {
                if (queue.size() >= capacity) return false;
                queue.addLast(seedEntity);
                return true;
        }

        public Entity peek() { return queue.peekFirst(); }
        public Entity pop()  { return queue.pollFirst(); }
        public boolean isEmpty() { return queue.isEmpty(); }
}
