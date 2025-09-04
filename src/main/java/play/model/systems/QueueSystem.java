package play.model.systems;

import play.model.core.Entity;
import play.model.components.*;
import play.model.engine.GameEngine;
import play.model.constants.GameBalance;
import play.model.physics.Kinematics;

import java.util.*;

/**
 * Handles arrivals into target systems, coins, buffering, and dispatch.
 */
public class QueueSystem implements System {

        private final GameEngine engine;
        private final List<Entity> entities;

        /** Per-device buffer (queue) of waiting seed entities. */
        private final Map<Entity, Deque<Entity>> deviceQueues = new HashMap<>();

        private final Random rng = new Random();

        public QueueSystem(GameEngine engine, List<Entity> entities) {
                this.engine = engine;
                this.entities = entities;
        }

        @Override
        public void update(double dt) {
                // 0) Decay "Disabled" timers on systems
                for (Entity e : entities) {
                        if (e.has(Disabled.class)) {
                                Disabled d = e.get(Disabled.class);
                                d.remaining -= dt;
                                if (d.remaining <= 0) {
                                        e.remove(Disabled.class);
                                }
                        }
                }

                // 1) Collect arrivals this frame
                List<Entity> arrivals = new ArrayList<>();
                for (Entity e : new ArrayList<>(entities)) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.currentLink == null) continue;
                        if (s.progress < 1.0) continue;
                        arrivals.add(e);
                }

                // 2) Process arrivals (speed trap, became-disabled bounce, normal handling)
                for (Entity seedE : arrivals) {
                        Seed s = seedE.get(Seed.class);
                        Link link = s.currentLink;

                        // Determine actual destination port for this traversal
                        // - Normal travel enters link.toPort
                        // - Returning travel enters link.fromPort (we traverse the same wire backwards)
                        Entity inPort = s.returning ? link.fromPort : link.toPort;
                        PortInfo pTo = inPort.get(PortInfo.class);
                        Entity system = pTo.parentSystem;

                        // Destination port position (used when we *do* finalize the arrival)
                        Transform tIn = inPort.get(Transform.class);
                        Transform st  = seedE.get(Transform.class);

                        // --- Speed trap: disable destination & bounce back (only on first arrival) ---
                        if (!s.returning && s.speed > GameBalance.ENTRY_SPEED_LIMIT) {
                                if (system.has(Disabled.class)) {
                                        system.get(Disabled.class).remaining = GameBalance.SYSTEM_DISABLE_SECONDS;
                                } else {
                                        system.add(new Disabled(GameBalance.SYSTEM_DISABLE_SECONDS));
                                }
                                // Immediately start travelling back along the same wire.
                                s.returning = true;
                                s.progress  = 0.0;   // restart from the (old) destination end
                                // Keep s.currentLink, keep speed/accel continuous; do not enqueue, no coins.
                                continue;            // skip normal arrival handling this frame
                        }

                        // --- NEW: Destination may have become disabled while en route -> bounce back ---
                        if (!s.returning && system.has(Disabled.class)) {
                                s.returning = true;
                                s.progress  = 0.0;   // restart from the destination end
                                // Keep s.currentLink; no enqueue, no coins.
                                continue;
                        }

                        // --- Normal arrival handling (includes finishing a return trip) ---
                        // snap to the IN port position
                        st.x = tIn.x;
                        st.y = tIn.y;

                        // clear travel state (now "inside" the system)
                        s.currentLink = null;
                        s.progress = 0.0;
                        s.lateral = 0.0;

                        // If we just finished a return trip, don't reward coins for the bounce path
                        boolean finishedReturn = s.returning;
                        s.returning = false;

                        // Reference systems consume (do not forward)
                        if (system.has(Reference.class)) {
                                engine.incrementReachedReference();
                                engine.notifySeedDelivered(s);
                                entities.remove(seedE);
                                continue;
                        }

                        // coins per packet entering a system: square=1, triangle=2 (only if not from bounce)
                        if (!finishedReturn) {
                                int reward = (s.type == Seed.Type.SQUARE)
                                        ? GameBalance.COIN_REWARD_SQUARE
                                        : GameBalance.COIN_REWARD_TRIANGLE;
                                engine.incrementCoins(reward);
                        }

                        // Enqueue into the device buffer
                        Deque<Entity> buf = deviceQueues.computeIfAbsent(system, k -> new ArrayDeque<>());
                        if (buf.size() >= GameBalance.DEVICE_CAPACITY) {
                                // storage overflow => packet loss
                                entities.remove(seedE);
                                engine.incrementLost();
                                continue;
                        }
                        buf.addLast(seedE);
                }

                // 3) Try to flush each device's queue to an available OUT link
                for (Map.Entry<Entity, Deque<Entity>> entry : deviceQueues.entrySet()) {
                        Entity system = entry.getKey();
                        Deque<Entity> buf = entry.getValue();
                        if (buf.isEmpty()) continue;

                        // Build lists of free OUT links from this system, grouped by shape
                        List<Entity> freeSquare = new ArrayList<>();
                        List<Entity> freeTriangle = new ArrayList<>();
                        for (Entity e : entities) {
                                if (!e.has(Link.class)) continue;
                                Link l = e.get(Link.class);
                                if (l.fromPort == null) continue;
                                if (!l.fromPort.has(PortInfo.class)) continue;
                                PortInfo pFrom = l.fromPort.get(PortInfo.class);
                                if (pFrom.parentSystem != system) continue;
                                if (!isLinkFree(l)) continue;

                                // Do not choose links whose destination system is disabled
                                if (l.toPort == null || !l.toPort.has(PortInfo.class)) continue;
                                PortInfo destPi = l.toPort.get(PortInfo.class);
                                Entity destSys = destPi.parentSystem;
                                if (destSys != null && destSys.has(Disabled.class)) continue;

                                if (pFrom.shape == PortInfo.Shape.SQUARE) freeSquare.add(e);
                                else freeTriangle.add(e);
                        }

                        // While we can ship something out, do it
                        boolean progressed = true;
                        while (progressed && !buf.isEmpty()) {
                                progressed = false;

                                Entity seedE = buf.peekFirst();
                                if (seedE == null) break;
                                Seed s = seedE.get(Seed.class);

                                // Preferred compatible link
                                Entity chosenLinkE = null;
                                if (s.type == Seed.Type.SQUARE && !freeSquare.isEmpty()) {
                                        chosenLinkE = freeSquare.remove(0);
                                } else if (s.type == Seed.Type.TRIANGLE && !freeTriangle.isEmpty()) {
                                        chosenLinkE = freeTriangle.remove(0);
                                }

                                // If no compatible link free, try any free link (random)
                                if (chosenLinkE == null) {
                                        int total = freeSquare.size() + freeTriangle.size();
                                        if (total > 0) {
                                                int idx = rng.nextInt(total);
                                                chosenLinkE = (idx < freeSquare.size())
                                                        ? freeSquare.remove(idx)
                                                        : freeTriangle.remove(idx - freeSquare.size());
                                        }
                                }

                                // If still nothing, stop trying this device (it's congested)
                                if (chosenLinkE == null) break;

                                // We can dispatch this seed
                                buf.removeFirst();

                                Link l = chosenLinkE.get(Link.class);
                                PortInfo.Shape outShape = l.fromPort.get(PortInfo.class).shape;

                                // per-hop kinematics
                                Kinematics.applyForHop(s, outShape);

                                // place at OUT port and start hop
                                Transform tFrom = l.fromPort.get(Transform.class);
                                Transform st = seedE.get(Transform.class);
                                st.x = tFrom.x;
                                st.y = tFrom.y;

                                s.currentLink = l;
                                s.progress = 0.0;
                                s.returning = false; // leaving a system -> forward travel

                                progressed = true;
                        }
                }
        }



        private boolean isLinkFree(Link link) {
                for (Entity e : entities) {
                        if (!e.has(Seed.class)) continue;
                        if (e.get(Seed.class).currentLink == link) return false; // one seed per wire at a time
                }
                return true;
        }
}
