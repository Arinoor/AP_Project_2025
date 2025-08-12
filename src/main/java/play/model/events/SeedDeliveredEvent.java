package play.model.events;

import play.model.components.Seed;

public record SeedDeliveredEvent(Seed.Type type, int deliveredCount) {}
