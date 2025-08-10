package play.components;

import play.core.Component;
import play.core.Entity;

/** Attach to a Port entity to reference its owning Device entity. */
public class DeviceRef implements Component {
        public final Entity device;
        public DeviceRef(Entity device) { this.device = device; }
}
