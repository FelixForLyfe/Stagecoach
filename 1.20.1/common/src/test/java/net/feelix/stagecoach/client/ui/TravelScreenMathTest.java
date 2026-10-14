package net.feelix.stagecoach.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelScreenMathTest {
    @Test
    void bothStopsFitInsideTheMarginsWithNorthUp() {
        RoutePlot plot = new RoutePlot(-3000, 500, 1000, -700, 128, 0.2, 0);
        double ox = plot.x(0), oz = plot.z(0), dx = plot.x(1), dz = plot.z(1);
        assertTrue(plot.inside(ox, oz, 128 * 0.2 - 1e-9) && plot.inside(dx, dz, 128 * 0.2 - 1e-9));
        assertEquals(128 * 0.2, ox, 1e-9);        // the widest side touches the margin
        assertEquals(128 * 0.8, dx, 1e-9);
        assertTrue(dz < oz, "the destination is further north, so higher on the map");
        assertEquals(64, (ox + dx) / 2, 1e-9);     // centred
    }

    @Test
    void theRoadIsWalkedAtConstantSpeedEvenWhenItBends() {
        RoutePlot plot = new RoutePlot(0, 0, 4000, 0, 256, 0.2, 1);
        double step = plot.length() / 20;
        double lastX = plot.x(plot.paramAt(0)), lastZ = plot.z(plot.paramAt(0));
        for (int i = 1; i <= 20; i++) {
            double t = plot.paramAt(i / 20.0);
            double x = plot.x(t), z = plot.z(t);
            assertEquals(step, Math.hypot(x - lastX, z - lastZ), step * 0.02);
            lastX = x;
            lastZ = z;
        }
        assertEquals(0, plot.paramAt(0), 1e-12);
        assertEquals(1, plot.paramAt(1), 1e-12);
    }

    @Test
    void headingIsDegreesClockwiseFromNorth() {
        assertEquals(90, new RoutePlot(0, 0, 1000, 0, 128, 0.2, 0).heading(0.5), 1e-9);   // east
        assertEquals(180, new RoutePlot(0, 0, 0, 1000, 128, 0.2, 0).heading(0.5), 1e-9);  // south
        assertEquals(0, new RoutePlot(0, 0, 0, -1000, 128, 0.2, 0).heading(0.5), 1e-9);   // north
    }

    @Test
    void stopsOnTopOfEachOtherDoNotBreakTheMap() {
        RoutePlot plot = new RoutePlot(50, 50, 50, 50, 128, 0.2, 1);
        assertEquals(64, plot.x(0.5), 1e-9);
        assertEquals(0, plot.length(), 1e-9);
        assertEquals(0, plot.paramAt(0.5), 1e-9);
    }

    @Test
    void theClockMovesSmoothlyLeansToTheServerAndNeverGoesBack() {
        TravelClock clock = new TravelClock();
        long ms = 1_000_000;
        assertEquals(100, clock.elapsed(0, 100), 1e-9);
        // 16 ms later the server still says the same tick, but the clock moved a third of a tick
        double a = clock.elapsed(16 * ms, 100);
        assertTrue(a > 100 && a < 100.4, "moved " + a);
        // The server jumps back a little (a resync): the clock doesn't go backwards.
        double b = clock.elapsed(32 * ms, 99.5);
        assertTrue(b >= a, b + " < " + a);
        // A real jump (reopened screen, lag spike) snaps straight to the server's time.
        assertEquals(400, clock.elapsed(48 * ms, 400), 1e-9);
    }
}
