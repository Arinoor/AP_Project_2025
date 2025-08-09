package play.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tiny entity container for components */
public class Entity {
        private final String id = UUID.randomUUID().toString();
        private final Map<Class<? extends Component>, Component> comps = new HashMap<>();
        public String id() { return id; }
        public <T extends Component> void add(T c) { comps.put(c.getClass(), c); }
        @SuppressWarnings("unchecked")
        public <T extends Component> T get(Class<T> cls) { return (T) comps.get(cls); }
        public boolean has(Class<? extends Component> cls) { return comps.containsKey(cls); }
}
