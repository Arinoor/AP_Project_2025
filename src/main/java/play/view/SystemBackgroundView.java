package play.view;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import play.model.components.BackgroundImage;
import play.model.components.PortInfo;
import play.model.components.Transform;
import play.model.core.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders per-system background sprites if present.
 *
 * Compatibility:
 * - Uses world coordinates from Transform (same space as ports/packets).
 * - Does NOT change the GraphicsContext transform; assumes caller already set camera/pan/zoom.
 * - Draw order: call this BEFORE drawing wires/ports/packets so images appear behind them.
 *
 * Sizing:
 * - If the system has ports, scales the sprite to cover the ports' bounding box with padding.
 * - Otherwise, draws the image at its natural size, centered on the system Transform.
 */
public final class SystemBackgroundView {

        private SystemBackgroundView() {}

        // Soft padding around the ports' AABB when scaling the background
        private static final double PAD = 48.0;

        // Resource cache to avoid reloading images
        private static final Map<String, Image> CACHE = new HashMap<>();

        /**
         * Render backgrounds for all systems that have a BackgroundImage component.
         * @param g         Graphics context (already in world space)
         * @param entities  All entities in the scene
         */
        public static void render(GraphicsContext g, Iterable<Entity> entities) {
                // First pass: draw in a single sweep to keep it cheap
                for (Entity sys : entities) {
                        if (!sys.has(Transform.class) || !sys.has(BackgroundImage.class)) continue;

                        Transform t = sys.get(Transform.class);
                        BackgroundImage bg = sys.get(BackgroundImage.class);
                        Image img = load(bg.resourcePath);
                        if (img == null) continue;

                        // Compute port AABB for this system (if any)
                        double[] box = portsAabbForSystem(entities, sys);
                        if (box != null) {
                                // Expand a bit so the image comfortably frames the ports
                                double minX = box[0] - PAD, minY = box[1] - PAD;
                                double maxX = box[2] + PAD, maxY = box[3] + PAD;
                                double targetW = Math.max(1.0, maxX - minX);
                                double targetH = Math.max(1.0, maxY - minY);

                                // Center the scaled image on the system transform
                                double cx = t.x;
                                double cy = t.y;
                                double x = cx - targetW * 0.5;
                                double y = cy - targetH * 0.5;

                                g.drawImage(img, x, y, targetW, targetH);
                        } else {
                                // No ports: draw at natural size centered at system transform
                                double w = img.getWidth();
                                double h = img.getHeight();
                                double x = t.x - w * 0.5;
                                double y = t.y - h * 0.5;
                                g.drawImage(img, x, y);
                        }
                }
        }

        /** Return [minX, minY, maxX, maxY] of all ports belonging to 'system', or null if none. */
        private static double[] portsAabbForSystem(Iterable<Entity> entities, Entity system) {
                boolean any = false;
                double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

                for (Entity e : entities) {
                        if (!e.has(PortInfo.class) || !e.has(Transform.class)) continue;
                        PortInfo pi = e.get(PortInfo.class);
                        if (pi.parentSystem != system) continue;

                        Transform pt = e.get(Transform.class);
                        any = true;
                        if (pt.x < minX) minX = pt.x;
                        if (pt.y < minY) minY = pt.y;
                        if (pt.x > maxX) maxX = pt.x;
                        if (pt.y > maxY) maxY = pt.y;
                }
                return any ? new double[]{minX, minY, maxX, maxY} : null;
        }

        private static Image load(String path) {
                if (path == null || path.isEmpty()) return null;
                Image cached = CACHE.get(path);
                if (cached != null) return cached;
                Image img = null;
                try {
                        // Accept both "/img/foo.png" and "img/foo.png"
                        String normalized = path.startsWith("/") ? path : "/" + path;
                        img = new Image(SystemBackgroundView.class.getResourceAsStream(normalized));
                } catch (Exception ignored) {}
                if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
                        CACHE.put(path, img);
                        return img;
                }
                return null;
        }
}
