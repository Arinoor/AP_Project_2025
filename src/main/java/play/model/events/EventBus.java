package play.model.events;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Minimal, synchronous event bus with typed subscriptions. */
public final class EventBus {
        private final Map<Class<?>, List<Consumer<?>>> subs = new ConcurrentHashMap<>();

        public <T> AutoCloseable subscribe(Class<T> type, Consumer<T> handler) {
                subs.computeIfAbsent(type, k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
                return () -> unsubscribe(type, handler);
        }

        public <T> void unsubscribe(Class<T> type, Consumer<T> handler) {
                List<Consumer<?>> list = subs.get(type);
                if (list != null) list.remove(handler);
        }

        @SuppressWarnings("unchecked")
        public <T> void post(T event) {
                List<Consumer<?>> list = subs.get(event.getClass());
                if (list == null) return;
                // copy to avoid CME if a subscriber unsubscribes during callback
                Consumer<?>[] snapshot;
                synchronized (list) { snapshot = list.toArray(new Consumer<?>[0]); }
                for (Consumer<?> c : snapshot) ((Consumer<T>) c).accept(event);
        }
}
