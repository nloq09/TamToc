package vn.tamtoc.world;

/** Coordinates describe the feet centre; platforms are one-way, solid when landing. */
public final class Movement {
    public static final double HALF_WIDTH = 16, HEIGHT = 44, SPEED = 240, GRAVITY = 1500, JUMP = 570;
    private Movement() {}
    public static void step(PlayerState p, MapCatalog.MapDefinition map, double dt) {
        p.x = Math.clamp(p.x + p.direction * SPEED * dt, HALF_WIDTH, map.width() - HALF_WIDTH);
        if (p.jumpQueued && p.grounded) { p.vy = -JUMP; p.grounded=false; }
        p.jumpQueued=false;
        double previousY = p.y;
        p.vy += GRAVITY * dt;
        double nextY = p.y + p.vy * dt;
        double floor = map.ground();
        if (p.vy >= 0) {
            for (var platform : map.platforms()) {
                if (p.x + HALF_WIDTH > platform.x() && p.x - HALF_WIDTH < platform.x() + platform.width()
                    && previousY <= platform.y() + 0.01 && nextY >= platform.y()) floor = Math.min(floor, platform.y());
            }
        }
        p.grounded = p.vy >= 0 && nextY >= floor;
        if (p.grounded) { p.y=floor; p.vy=0; } else p.y=nextY;
    }
}
