package net.feelix.stagecoach.client.ui;

public final class RoutePlot {
    private static final int SAMPLES = 64;

    private final double centreX, centreZ, blocksPerPx;
    private final int size;
    private final double ax, az, bx, bz, cx, cz;
    /** Lets {@link #at} move at the same speed along the whole road. */
    private final double[] lengths = new double[SAMPLES + 1];

    /** {@code bend} (about -1..1) bows the road so it doesn't look ruled; 0 is straight. */
    public RoutePlot(int originX, int originZ, int destX, int destZ, int size, double margin, double bend) {
        this.size = size;
        centreX = (originX + destX) / 2.0;
        centreZ = (originZ + destZ) / 2.0;
        double span = Math.max(Math.abs(destX - originX), Math.abs(destZ - originZ));
        double usable = size * (1 - 2 * margin);
        blocksPerPx = Math.max(span, 1) / usable;
        ax = px(originX);
        az = pz(originZ);
        bx = px(destX);
        bz = pz(destZ);
        double dx = bx - ax, dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        double off = bend * 0.18 * len;
        cx = (ax + bx) / 2 - (len == 0 ? 0 : dz / len * off);
        cz = (az + bz) / 2 + (len == 0 ? 0 : dx / len * off);
        double total = 0, lastX = ax, lastZ = az;
        for (int i = 1; i <= SAMPLES; i++) {
            double t = (double) i / SAMPLES;
            double x = bezier(ax, cx, bx, t), z = bezier(az, cz, bz, t);
            total += Math.hypot(x - lastX, z - lastZ);
            lengths[i] = total;
            lastX = x;
            lastZ = z;
        }
    }

    public double px(double worldX) {
        return size / 2.0 + (worldX - centreX) / blocksPerPx;
    }

    public double pz(double worldZ) {
        return size / 2.0 + (worldZ - centreZ) / blocksPerPx;
    }

    public boolean inside(double x, double z, double pad) {
        return x >= pad && x <= size - pad && z >= pad && z <= size - pad;
    }

    public double length() {
        return lengths[SAMPLES];
    }

    /** Equal steps of {@code progress} cover equal lengths of road. */
    public double paramAt(double progress) {
        double target = Math.max(0, Math.min(1, progress)) * length();
        int i = 1;
        while (i < SAMPLES && lengths[i] < target) i++;
        double seg = lengths[i] - lengths[i - 1];
        double f = seg <= 0 ? 0 : (target - lengths[i - 1]) / seg;
        return (i - 1 + f) / SAMPLES;
    }

    public double x(double t) {
        return bezier(ax, cx, bx, t);
    }

    public double z(double t) {
        return bezier(az, cz, bz, t);
    }

    /** Degrees clockwise from north (map up). */
    public double heading(double t) {
        double dx = 2 * (1 - t) * (cx - ax) + 2 * t * (bx - cx);
        double dz = 2 * (1 - t) * (cz - az) + 2 * t * (bz - cz);
        if (dx == 0 && dz == 0) return 0;
        return Math.toDegrees(Math.atan2(dx, -dz));
    }

    private static double bezier(double p0, double p1, double p2, double t) {
        double u = 1 - t;
        return u * u * p0 + 2 * u * t * p1 + t * t * p2;
    }
}
