package vn.tamtoc.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import vn.tamtoc.realtime.ProtocolException;

@Component
public class MapCatalog {
    public record Platform(double x,double y,double width,double height) {}
    public record Rect(double x,double y,double width,double height) {
        public boolean contains(double px,double py){return px>=x&&px<=x+width&&py>=y&&py<=y+height;}
    }
    public record SpawnPoint(String id,double x,double y,String race) {}
    public record SafeZone(String id,Rect bounds,String spawnId,boolean heal,boolean protectedArea) {}
    public record Waypoint(String id,double x,double y) {}
    public record NavEdge(String from,String to,String type) {}
    public record SpawnGroup(String id,String definitionId,int maxAlive,double startX,double endX,double y,long respawnMs,Boolean enabled,String placementId,String spawnPolicy,long scheduleIntervalMs,long activeDurationMs,int requiredLevel,List<String> definitionIds) {
        public SpawnGroup {enabled=enabled==null?true:enabled;spawnPolicy=spawnPolicy==null?"AFTER_DEATH":spawnPolicy;requiredLevel=requiredLevel==0?1:requiredLevel;definitionIds=definitionIds==null||definitionIds.isEmpty()?List.of(definitionId):List.copyOf(definitionIds);if(spawnPolicy.equals("SCHEDULED_EVENT")&&(maxAlive!=1||scheduleIntervalMs<14400000||activeDurationMs<=0||activeDurationMs>scheduleIntervalMs))throw new IllegalArgumentException("Invalid scheduled event "+id);}
        public SpawnGroup(String id,String definitionId,int maxAlive,double startX,double endX,double y,long respawnMs){this(id,definitionId,maxAlive,startX,endX,y,respawnMs,true,null,"AFTER_DEATH",0,0,1,null);}
        public String definitionFor(int index){return definitionIds.get(index%definitionIds.size());}
    }
    public record NpcSpawn(String id,String type,String name,double x,double y,String dialogueId) {}
    public record BotSpawn(String id,String definitionId,String spawnPoint,List<String> patrolRoute,double interactionRadius) {}
    public record Portal(double x,String target,String label,String portalId,String sourceMapId,Rect triggerBounds,String targetSpawnId,int requiredLevel,String requiredQuest,boolean enabled) {
        public Portal(double x,String target,String label){this(x,target,label,null,null,null,"entry",1,null,true);}
    }
    public record MapDefinition(String id,String name,int width,int height,double ground,String color,List<Platform> platforms,List<Portal> portals,
        int version,String regionId,String raceTheme,int recommendedLevelMin,int recommendedLevelMax,String mapType,double gravity,int maxPlayers,String pvpPolicy,Rect bounds,
        List<Platform> groundColliders,List<Platform> obstacles,List<SafeZone> safeZones,List<SpawnPoint> spawnPoints,List<Waypoint> waypoints,List<NavEdge> navigation,
        List<SpawnGroup> monsterSpawnGroups,List<NpcSpawn> npcSpawns,List<BotSpawn> botSpawnGroups) {
        public MapDefinition {
            platforms=copy(platforms);portals=copy(portals);version=version==0?1:version;regionId=regionId==null?id:regionId;raceTheme=raceTheme==null?"NEUTRAL":raceTheme;
            recommendedLevelMin=recommendedLevelMin==0?1:recommendedLevelMin;recommendedLevelMax=recommendedLevelMax==0?recommendedLevelMin:recommendedLevelMax;
            mapType=mapType==null?"FIELD":mapType;gravity=gravity==0?1500:gravity;maxPlayers=maxPlayers==0?100:maxPlayers;pvpPolicy=pvpPolicy==null?"DISABLED":pvpPolicy;
            bounds=bounds==null?new Rect(0,0,width,height):bounds;
            groundColliders=groundColliders==null?List.of(new Platform(0,ground,width,height-ground)):copy(groundColliders);
            obstacles=copy(obstacles);safeZones=copy(safeZones);spawnPoints=spawnPoints==null?List.of(new SpawnPoint("home",180,ground,null),new SpawnPoint("entry",650,ground,null)):copy(spawnPoints);
            waypoints=copy(waypoints);navigation=copy(navigation);monsterSpawnGroups=copy(monsterSpawnGroups);npcSpawns=copy(npcSpawns);botSpawnGroups=copy(botSpawnGroups);
        }
        public MapDefinition(String id,String name,int width,int height,double ground,String color,List<Platform> platforms,List<Portal> portals){this(id,name,width,height,ground,color,platforms,portals,1,id,"NEUTRAL",1,10,"FIELD",1500,100,"DISABLED",null,null,null,null,null,null,null,null,null,null);}
        public SpawnPoint spawn(String id){return spawnPoints.stream().filter(s->s.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown spawn "+this.id+":"+id));}
        public boolean safe(double x,double y){return safeZones.stream().anyMatch(z->z.protectedArea&&z.bounds.contains(x,y));}
        public boolean heals(double x,double y){return safeZones.stream().anyMatch(z->z.heal&&z.bounds.contains(x,y));}
    }
    private static <T> List<T> copy(List<T> value){return value==null?List.of():List.copyOf(value);}
    private final Map<String, MapDefinition> maps = new LinkedHashMap<>();
    private final Map<String,String> aliases=new HashMap<>();
    @Autowired public MapCatalog(ObjectMapper mapper) throws IOException {
        try (var in = new ClassPathResource("maps.json").getInputStream()) {
            initialize(Arrays.asList(mapper.readValue(in,MapDefinition[].class)));
        }
        try(var in=new ClassPathResource("world15-bindings.json").getInputStream()){mapper.readTree(in).path("mapIds").fields().forEachRemaining(e->aliases.put(e.getKey(),e.getValue().asText()));}
    }
    public MapCatalog(List<MapDefinition> definitions){initialize(definitions);}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalArgumentException("Invalid world data: "+reason);}
    private static void unique(Collection<String> ids,String kind){require(ids.stream().allMatch(s->s!=null&&s.matches("[a-zA-Z0-9_-]{1,80}"))&&new HashSet<>(ids).size()==ids.size(),"duplicate/invalid "+kind);}
    private void initialize(List<MapDefinition> definitions){
        unique(definitions.stream().map(MapDefinition::id).toList(),"mapId");definitions.forEach(m->maps.put(m.id,m));
        Set<String> portals=new HashSet<>(),groups=new HashSet<>();
        for(var m:maps.values()){
            require(m.width>100&&m.height>100&&m.ground>44&&m.ground<m.height&&m.gravity>0&&m.gravity<=5000&&m.version>0,"dimensions/gravity "+m.id);
            require(Set.of("TOWN","FIELD","DUNGEON","ARENA").contains(m.mapType)&&Set.of("DISABLED","CONSENT_ONLY").contains(m.pvpPolicy),"map policy "+m.id);
            require(m.recommendedLevelMin>=1&&m.recommendedLevelMax>=m.recommendedLevelMin&&m.maxPlayers>0,"levels/capacity "+m.id);
            require(m.bounds.x==0&&m.bounds.y==0&&m.bounds.width==m.width&&m.bounds.height==m.height,"bounds must match dimensions "+m.id);
            for(var box:java.util.stream.Stream.of(m.platforms,m.groundColliders,m.obstacles).flatMap(Collection::stream).toList())require(box.width>0&&box.height>0&&box.x>=0&&box.y>=0&&box.x+box.width<=m.width&&box.y+box.height<=m.height,"collision box "+m.id);
            unique(m.spawnPoints.stream().map(SpawnPoint::id).toList(),"spawn point "+m.id);require(!m.spawnPoints.isEmpty(),"missing spawn "+m.id);
            for(var s:m.spawnPoints)require(validPosition(m,s.x,s.y),"unsafe spawn "+m.id+":"+s.id);
            unique(m.waypoints.stream().map(Waypoint::id).toList(),"waypoint "+m.id);
            for(var w:m.waypoints)require(validPosition(m,w.x,w.y),"waypoint in solid/bounds "+m.id+":"+w.id);
            for(var edge:m.navigation){require(Set.of("WALK","JUMP","DROP").contains(edge.type),"nav edge type");require(m.waypoints.stream().anyMatch(w->w.id.equals(edge.from))&&m.waypoints.stream().anyMatch(w->w.id.equals(edge.to)),"nav edge reference "+m.id);}
            unique(m.safeZones.stream().map(SafeZone::id).toList(),"safe zone "+m.id);
            for(var z:m.safeZones){require(z.bounds!=null&&z.bounds.width>0&&z.bounds.height>0&&m.bounds.contains(z.bounds.x,z.bounds.y)&&m.bounds.contains(z.bounds.x+z.bounds.width,z.bounds.y+z.bounds.height),"safe zone bounds "+m.id);if(z.spawnId!=null)require(z.bounds.contains(m.spawn(z.spawnId).x,m.spawn(z.spawnId).y),"checkpoint spawn outside zone");}
            unique(m.portals.stream().map(Portal::portalId).toList(),"portal "+m.id);
            for(var p:m.portals){require(portals.add(p.portalId)&&m.id.equals(p.sourceMapId)&&p.triggerBounds!=null&&p.triggerBounds.width>0&&p.triggerBounds.height>0&&m.bounds.contains(p.x,m.ground)&&m.bounds.contains(p.triggerBounds.x,p.triggerBounds.y)&&m.bounds.contains(p.triggerBounds.x+p.triggerBounds.width,p.triggerBounds.y+p.triggerBounds.height),"portal identity/bounds "+m.id);require(maps.containsKey(p.target)&&p.requiredLevel>=1,"portal target/level "+p.portalId);get(p.target).spawn(p.targetSpawnId);}
            unique(m.monsterSpawnGroups.stream().map(SpawnGroup::id).toList(),"spawn group "+m.id);
            for(var g:m.monsterSpawnGroups){require(groups.add(g.id)&&g.maxAlive>0&&g.maxAlive<=100&&g.startX<=g.endX&&validPosition(m,g.startX,g.y)&&validPosition(m,g.endX,g.y)&&g.respawnMs>=0,"spawn group "+g.id);for(int i=0;i<g.maxAlive;i++){double x=g.maxAlive==1?g.startX:g.startX+i*(g.endX-g.startX)/(g.maxAlive-1);require(validPosition(m,x,g.y),"spawn slot in solid/bounds "+g.id+":"+i);}}
            unique(m.npcSpawns.stream().map(NpcSpawn::id).toList(),"NPC "+m.id);for(var npc:m.npcSpawns)require(validPosition(m,npc.x,npc.y),"NPC position "+npc.id);
            unique(m.botSpawnGroups.stream().map(BotSpawn::id).toList(),"bot spawn "+m.id);for(var bot:m.botSpawnGroups){m.spawn(bot.spawnPoint);require(bot.interactionRadius>0&&bot.interactionRadius<=200,"bot interaction radius");for(var point:bot.patrolRoute)require(m.waypoints.stream().anyMatch(w->w.id.equals(point)),"bot waypoint reference");}
        }
    }
    public static boolean validPosition(MapDefinition m,double x,double y){return Double.isFinite(x)&&Double.isFinite(y)&&x>=Movement.HALF_WIDTH&&x<=m.width-Movement.HALF_WIDTH&&y>=Movement.HEIGHT&&y<=m.height&&java.util.stream.Stream.of(m.obstacles,m.groundColliders).flatMap(Collection::stream).noneMatch(b->x+16>b.x&&x-16<b.x+b.width&&y>b.y+.01&&y-44<b.y+b.height);}
    public List<MapDefinition> all(){return List.copyOf(maps.values());}
    public boolean contains(String id){return maps.containsKey(aliases.getOrDefault(id,id));}
    public MapDefinition get(String id){var map=maps.get(aliases.getOrDefault(id,id));if(map==null)throw new ProtocolException("MAP_NOT_FOUND","Bản đồ không tồn tại.");return map;}
    public SpawnPoint respawn(PlayerState p){var m=get(p.mapId);String checkpoint=p.progress.checkpoints.get(p.mapId);return m.spawnPoints.stream().filter(s->s.id.equals(checkpoint)).findFirst().orElse(m.spawnPoints.get(0));}
    public void sanitize(PlayerState p){
        p.mapId=aliases.getOrDefault(p.mapId,p.mapId);
        if(!contains(p.mapId)||Set.of("DUNGEON","ARENA").contains(get(p.mapId).mapType)){p.mapId=vn.tamtoc.character.Race.valueOf(p.race).startMap;p.x=Double.NaN;}
        var m=get(p.mapId);boolean upgradedDanger=m.mapType.equals("FIELD")&&p.progress.mapVersions.getOrDefault(m.id,0)<m.version&&p.level<m.recommendedLevelMin;if(!validPosition(m,p.x,p.y)||upgradedDanger){var s=respawn(p);p.x=s.x;p.y=s.y;p.vy=0;}p.progress.mapVersions.put(m.id,m.version);
    }
    public Portal portal(PlayerState p,String id){return get(p.mapId).portals.stream().filter(v->id==null?v.triggerBounds.contains(p.x,p.y):v.portalId.equals(id)).findFirst().orElseThrow(()->new ProtocolException("PORTAL_NOT_FOUND","Hãy đến đúng cổng dịch chuyển."));}
    public SpawnPoint validatePortal(PlayerState p,Portal portal){
        if(p.instanceId!=null||p.progress.hp<=0)throw new ProtocolException("PORTAL_ACTIVITY_LOCKED","Không thể chuyển bản đồ trong hoạt động riêng hoặc khi đã chết.");
        if(!p.mapId.equals(portal.sourceMapId)||!portal.triggerBounds.contains(p.x,p.y))throw new ProtocolException("PORTAL_TOO_FAR","Hãy đứng trong vùng cổng dịch chuyển.");
        if(!portal.enabled)throw new ProtocolException("PORTAL_DISABLED","Cổng đang đóng.");if(p.level<portal.requiredLevel)throw new ProtocolException("PORTAL_LEVEL_REQUIRED","Chưa đủ cấp sử dụng cổng.");
        if(portal.requiredQuest!=null&&!"CLAIMED".equals(p.progress.quests.get(portal.requiredQuest)))throw new ProtocolException("PORTAL_QUEST_REQUIRED","Cần hoàn thành nhiệm vụ trước khi qua cổng.");return get(portal.target).spawn(portal.targetSpawnId);
    }
}
