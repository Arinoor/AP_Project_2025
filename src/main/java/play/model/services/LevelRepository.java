package play.model.services;

import java.util.List;

public class LevelRepository {
        private static final List<String> ORDER = List.of(
                "/levels/level1.json",
                "/levels/level2.json"
        );
        public List<String> all() { return ORDER; }
        public String next(String current) {
                int i = ORDER.indexOf(current);
                return (i >= 0 && i + 1 < ORDER.size()) ? ORDER.get(i + 1) : null;
        }
}
