package play.model.components;

/** Optional background sprite for a system (view consumes this). */
public class BackgroundImage {
        public final String resourcePath; // e.g. "/img/vpn_system.png"
        public BackgroundImage(String resourcePath) {
                this.resourcePath = resourcePath;
        }
}
