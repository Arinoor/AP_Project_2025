package play.model.components;

/**
 * Marker component: this system is an Anti-Trojan system.
 * Behavior implemented in QueueSystem:
 *  - Scans nearby seeds and removes their trojan flag.
 *  - After a successful removal, the system is disabled for a cooldown period.
 */
public class Antitrojan { }