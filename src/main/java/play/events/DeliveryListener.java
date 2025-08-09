package play.events;

import play.components.Seed;

public interface DeliveryListener {
        void onSeedDelivered(Seed seed);
}
