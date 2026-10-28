package vn.tamtoc.world;

/** Coordinates describe the feet centre; platforms are one-way, solid when landing. */
public final class Movement {
    public static final double HALF_WIDTH = 16, HEIGHT = 44, SPEED = 240, GRAVITY = 1500, JUMP = 570;
    private Movement() {}
    public static void step(PlayerState p, MapCatalog.MapDefinition map, double dt) {
        step(p,map,dt,SPEED,JUMP,HALF_WIDTH,HEIGHT);
    }
    public static void step(MotionBody p,MapCatalog.MapDefinition map,double dt,double speed,double jump,double halfWidth,double height){
        p.x = MapCollision.horizontal(map,p.x,p.y,p.direction*speed*p.movementScale*dt,halfWidth,height);
        if (p.jumpQueued && p.grounded) { p.vy = -jump; p.grounded=false; }
        p.jumpQueued=false;
        double previousY = p.y;
        p.vy += map.gravity() * dt;
        double nextY = p.y + p.vy * dt;
        double floor = Double.POSITIVE_INFINITY;
        if (p.vy >= 0) {
            var surfaces=new java.util.ArrayList<>(map.platforms());surfaces.addAll(MapCollision.solids(map));
            for (var platform : surfaces) {
                if (p.x + halfWidth > platform.x() && p.x - halfWidth < platform.x() + platform.width()
                    && previousY <= platform.y() + 0.01 && nextY >= platform.y()) floor = Math.min(floor, platform.y());
            }
        }else for(var box:MapCollision.solids(map))if(p.x+halfWidth>box.x()&&p.x-halfWidth<box.x()+box.width()&&previousY-height>=box.y()+box.height()&&nextY-height<=box.y()+box.height()){nextY=box.y()+box.height()+height;p.vy=0;}
        p.grounded = p.vy >= 0 && nextY >= floor;
        if (p.grounded) { p.y=floor; p.vy=0; } else p.y=nextY;
    }
}
