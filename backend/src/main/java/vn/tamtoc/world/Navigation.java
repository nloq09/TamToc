package vn.tamtoc.world;

import java.util.*;
import vn.tamtoc.world.MapCatalog.*;

/** Designer-authored platform graph; no steering through walls or unsupported ground. */
public final class Navigation {
    public record Capabilities(double speed,double jumpVelocity,boolean canJump,double maxDrop,double halfWidth,double height) {}
    public record Goal(double x,double y,String type,double speed) {}
    public static class Plan {public final Deque<Goal> goals;boolean started;Plan(List<Goal> goals){this.goals=new ArrayDeque<>(goals);}}
    private Navigation(){}
    public static Plan plan(MotionBody body,MapDefinition map,double tx,double ty,Capabilities cap){
        if(Math.abs(body.y-ty)<3&&MapCollision.walkLine(map,body.x,body.y,tx,cap.halfWidth,cap.height))return new Plan(List.of(new Goal(tx,ty,"WALK",cap.speed)));
        var starts=map.waypoints().stream().filter(w->Math.abs(w.y()-body.y)<3&&MapCollision.walkLine(map,body.x,body.y,w.x(),cap.halfWidth,cap.height)).sorted(Comparator.comparingDouble(w->Math.abs(w.x()-body.x))).toList();
        var ends=map.waypoints().stream().filter(w->Math.abs(w.y()-ty)<3&&MapCollision.walkLine(map,w.x(),ty,tx,cap.halfWidth,cap.height)).map(Waypoint::id).collect(java.util.stream.Collectors.toSet());
        Map<String,Waypoint> nodes=new HashMap<>();map.waypoints().forEach(w->nodes.put(w.id(),w));
        for(var start:starts){Map<String,NavEdge> previous=new HashMap<>();Set<String> seen=new HashSet<>();Deque<String> queue=new ArrayDeque<>();queue.add(start.id());seen.add(start.id());String found=null;
            while(!queue.isEmpty()){String id=queue.remove();if(ends.contains(id)){found=id;break;}for(var edge:map.navigation())if(edge.from().equals(id)&&!seen.contains(edge.to())&&canTraverse(map,nodes.get(edge.from()),nodes.get(edge.to()),edge.type(),cap)){seen.add(edge.to());previous.put(edge.to(),edge);queue.add(edge.to());}}
            if(found==null)continue;
            List<NavEdge> path=new ArrayList<>();for(String id=found;!id.equals(start.id());){var edge=previous.get(id);path.add(edge);id=edge.from();}Collections.reverse(path);
            List<Goal> goals=new ArrayList<>();goals.add(new Goal(start.x(),start.y(),"WALK",cap.speed));
            for(var edge:path){var from=nodes.get(edge.from());var to=nodes.get(edge.to());double speed=edge.type().equals("JUMP")?Math.abs(to.x()-from.x())/flight(map,from.y(),to.y(),cap.jumpVelocity):cap.speed;goals.add(new Goal(to.x(),to.y(),edge.type(),speed));}
            goals.add(new Goal(tx,ty,"WALK",cap.speed));return new Plan(goals);
        }return null;
    }
    private static double flight(MapDefinition m,double y,double ty,double jump){double d=jump*jump+2*m.gravity()*(ty-y);return d<0?Double.NaN:(jump+Math.sqrt(d))/m.gravity();}
    public static boolean canTraverse(MapDefinition m,Waypoint a,Waypoint b,String type,Capabilities c){
        if(a==null||b==null)return false;
        if(type.equals("WALK"))return Math.abs(a.y()-b.y())<3&&MapCollision.walkLine(m,a.x(),a.y(),b.x(),c.halfWidth,c.height);
        if(type.equals("DROP"))return b.y()>a.y()&&b.y()-a.y()<=c.maxDrop&&MapCollision.lineOfSight(m,a.x(),a.y()-c.height/2,b.x(),b.y()-c.height/2);
        if(!type.equals("JUMP")||!c.canJump)return false;double time=flight(m,a.y(),b.y(),c.jumpVelocity);if(!Double.isFinite(time)||time<=0||Math.abs(b.x()-a.x())/time>c.speed)return false;
        double x=a.x(),y=a.y();for(int i=1;i<=40;i++){double t=time*i/40,tx=a.x()+(b.x()-a.x())*i/40,ty=a.y()-c.jumpVelocity*t+.5*m.gravity()*t*t;if(Math.abs(MapCollision.horizontal(m,x,y,tx-x,c.halfWidth,c.height)-tx)>.5)return false;x=tx;y=ty;}return true;
    }
    public static boolean step(MotionBody body,MapDefinition map,Capabilities cap,double dt){
        Plan plan=body.navigation;body.direction=0;
        if(plan!=null){
            while(!plan.goals.isEmpty()){var goal=plan.goals.peek();if(Math.abs(body.x-goal.x)<=3&&Math.abs(body.y-goal.y)<3&&body.grounded){plan.goals.remove();plan.started=false;}else break;}
            if(plan.goals.isEmpty()){body.navigation=null;Movement.step(body,map,dt,cap.speed,cap.jumpVelocity,cap.halfWidth,cap.height);return true;}
            var goal=plan.goals.peek();if(!plan.started&&goal.type.equals("JUMP")){if(!body.grounded)return false;body.jumpQueued=true;}plan.started=true;
            double distance=goal.x-body.x;body.direction=Math.abs(distance)<=1?0:(int)Math.signum(distance);double speed=Math.min(goal.speed,Math.abs(distance)/Math.max(.0001,dt*body.movementScale));
            if(goal.type.equals("WALK")&&body.direction!=0&&!MapCollision.walkable(map,body.x,body.y,body.x+body.direction*Math.min(Math.abs(distance),goal.speed*dt),cap.halfWidth,cap.height)){body.navigation=null;body.direction=0;}
            Movement.step(body,map,dt,speed,cap.jumpVelocity,cap.halfWidth,cap.height);return body.navigation!=null;
        }Movement.step(body,map,dt,cap.speed,cap.jumpVelocity,cap.halfWidth,cap.height);return false;
    }
}
