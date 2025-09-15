package play.model.components;

/**
 * Marker component: this system is a Saboteur system.
 * - Prioritizes sending packets to incompatible ports.
 * - Adds 1 unit of noise to non-protected packets that have no noise when they arrive.
 * - Does not affect protected packets.
 */
public class Saboteur { }