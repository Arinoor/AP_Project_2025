package play.model.components;

/**
 * Marker component: this system collects BITPACKET arrivals and, after a short wait,
 * merges them into a HEAVY packet of size = number of bitpackets received.
 *
 * Behavior implemented in QueueSystem (merge buffering + emit).
 */
public class Merge { }
