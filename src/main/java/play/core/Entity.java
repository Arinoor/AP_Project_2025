package play.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Entity {
        private final String id = UUID.randomUUID().toString();
        private final Map<Class<? extends Component>, Component> components = new HashMap<>();
        public String id() { return id; }
        public <T extends Component> void add(T c) { components.put(c.getClass(), c); }
        @SuppressWarnings("unchecked")
        public <T extends Component> T get(Class<T> cls) { return (T) components.get(cls); }
        public boolean has(Class<? extends Component> cls) { return components.containsKey(cls); }
}
