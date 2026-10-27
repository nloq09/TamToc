package vn.tamtoc.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import vn.tamtoc.character.*;

class MovementTest {
    private final MapCatalog.MapDefinition map=new MapCatalog.MapDefinition("test","Test",1000,640,540,"#000",List.of(new MapCatalog.Platform(200,450,200,20)),List.of());
    private PlayerState player() { var p=new PlayerState(new GameCharacter(UUID.randomUUID(),"Tester",Race.HUMAN)); p.y=540; p.grounded=true; return p; }
    @Test void speedAndMapBoundsAreServerControlled() {
        var p=player(); p.direction=1; for(int i=0;i<20;i++) Movement.step(p,map,.05);
        assertEquals(400,p.x,.001);
        for(int i=0;i<100;i++) Movement.step(p,map,.05);
        assertEquals(984,p.x,.001); assertEquals(540,p.y,.001);
    }
    @Test void jumpLandsOnPlatform() {
        var p=player(); p.x=250; p.jumpQueued=true;
        boolean above=false;
        for(int i=0;i<30;i++){ Movement.step(p,map,.05); above |= p.y<450; }
        assertTrue(above); assertEquals(450,p.y,.001); assertTrue(p.grounded);
    }
    @Test void cannotJumpInMidair() {
        var p=player(); p.grounded=false;p.y=200;p.vy=10;p.jumpQueued=true;
        Movement.step(p,map,.05);assertTrue(p.vy>0);
    }
}
