package play.model.components;

import play.model.core.Entity;

import java.util.ArrayDeque;
import java.util.Deque;

public class Queue {
        private final int capacity;
        private final Deque<Entity> q = new ArrayDeque<>();

        public Queue(int capacity) {
                this.capacity = Math.max(1, capacity);
        }

        public boolean isEmpty() { return q.isEmpty(); }
        public boolean isFull()  { return q.size() >= capacity; }
        public int size()        { return q.size(); }

        public void push(Entity e) { if (!isFull()) q.addLast(e); }
        public Entity pop()        { return q.pollFirst(); }
        public Entity peek()       { return q.peekFirst(); }
}
