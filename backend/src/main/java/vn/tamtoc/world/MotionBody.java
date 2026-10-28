package vn.tamtoc.world;

/** Geometry only: shared by player, monsters and clearly labelled AI NPCs. */
public class MotionBody {
    public double x,y,vy,movementScale=1;
    public boolean grounded,jumpHeld,jumpQueued;
    public int direction;
    public Navigation.Plan navigation;
}
