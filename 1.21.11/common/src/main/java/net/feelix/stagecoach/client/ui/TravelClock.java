package net.feelix.stagecoach.client.ui;

/** Runs on the real clock, leans toward the server's jumpy tick count and never runs backwards. */
public final class TravelClock {
    private static final double NANOS_PER_TICK = 50_000_000.0;
    private static final double SNAP_TICKS = 40;
    private static final double LEAN = 0.05;

    private boolean started;
    private long anchorNanos;
    private double anchorTicks;
    private double shown;

    public double elapsed(long nowNanos, double serverTicks) {
        if (!started) return snap(nowNanos, serverTicks);
        double local = anchorTicks + (nowNanos - anchorNanos) / NANOS_PER_TICK;
        double drift = serverTicks - local;
        if (Math.abs(drift) > SNAP_TICKS) return snap(nowNanos, serverTicks);
        anchorTicks += drift * LEAN;
        shown = Math.max(shown, anchorTicks + (nowNanos - anchorNanos) / NANOS_PER_TICK);
        return shown;
    }

    private double snap(long nowNanos, double serverTicks) {
        started = true;
        anchorNanos = nowNanos;
        anchorTicks = serverTicks;
        shown = serverTicks;
        return serverTicks;
    }
}
