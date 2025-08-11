package play.components;

import play.core.Entity;
import java.util.ArrayDeque;
import java.util.Deque;

/** Per-device storage; used when no out-link is free. */
public class SystemBuffer {
        public final Deque<Entity> items = new ArrayDeque<>();
        public int capacity = 5; // project rule
}
