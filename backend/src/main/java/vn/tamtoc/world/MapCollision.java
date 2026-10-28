package vn.tamtoc.world;

import java.util.*;
import vn.tamtoc.world.MapCatalog.*;

/** Server feet-centre coordinates. Shared rectangles are exported verbatim to Phaser. */
public final class MapCollision {
    private MapCollision(){}
    public static List<Platform> solids(MapDefinition m){var boxes=new ArrayList<>(m.obstacles());boxes.addAll(m.groundColliders());return boxes;}
    public static double horizontal(MapDefinition m,double x,double y,double dx,double halfWidth,double height){
        double target=Math.clamp(x+dx,halfWidth,m.width()-halfWidth);
        for(var b:solids(m))if(y>b.y()+.01&&y-height<b.y()+b.height()-.01){
            if(dx>0&&x+halfWidth<=b.x()+.01&&target+halfWidth>b.x())target=Math.min(target,b.x()-halfWidth);
            if(dx<0&&x-halfWidth>=b.x()+b.width()-.01&&target-halfWidth<b.x()+b.width())target=Math.max(target,b.x()+b.width()+halfWidth);
        }return target;
    }
    public static boolean lineOfSight(MapDefinition m,double x,double y,double tx,double ty){
        for(var b:m.obstacles())if(segmentHits(x,y,tx,ty,b))return false;return true;
    }
    private static boolean segmentHits(double x,double y,double tx,double ty,Platform b){
        double low=0,high=1;double[] origin={x,y},delta={tx-x,ty-y},min={b.x(),b.y()},max={b.x()+b.width(),b.y()+b.height()};
        for(int a=0;a<2;a++){if(Math.abs(delta[a])<1e-9){if(origin[a]<min[a]||origin[a]>max[a])return false;}else{double t1=(min[a]-origin[a])/delta[a],t2=(max[a]-origin[a])/delta[a];low=Math.max(low,Math.min(t1,t2));high=Math.min(high,Math.max(t1,t2));if(low>high)return false;}}return high>=0&&low<=1;
    }
    public static boolean supported(MapDefinition m,double x,double feet){return java.util.stream.Stream.concat(java.util.stream.Stream.concat(m.groundColliders().stream(),m.platforms().stream()),m.obstacles().stream()).anyMatch(b->x>=b.x()&&x<=b.x()+b.width()&&Math.abs(feet-b.y())<3);}
    public static boolean walkable(MapDefinition m,double x,double y,double target,double halfWidth,double height){
        double next=horizontal(m,x,y,target-x,halfWidth,height);return Math.abs(next-target)<.1&&supported(m,target,y);
    }
    public static boolean walkLine(MapDefinition m,double x,double y,double target,double halfWidth,double height){
        int steps=Math.max(1,(int)Math.ceil(Math.abs(target-x)/12));double previous=x;
        for(int i=1;i<=steps;i++){double next=x+(target-x)*i/steps;if(!walkable(m,previous,y,next,halfWidth,height))return false;previous=next;}return true;
    }
}
