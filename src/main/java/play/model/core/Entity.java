package play.model.core;

import java.util.HashMap;
import java.util.Map;

public class Entity {
        private final Map<Class<?>, Object> comps = new HashMap<>();

        public <T> boolean has(Class<T> type) {
                return comps.containsKey(type);
        }

        public <T> T get(Class<T> type) {
                Object v = comps.get(type);
                return type.cast(v);
        }

        public <T> Entity add(T comp) {
                comps.put(comp.getClass(), comp);
                return this;
        }

        public <T> void remove(Class<T> type) {
                comps.remove(type);
        }
}
