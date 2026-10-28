package vn.tamtoc.bot;

import java.util.*;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Component;
import vn.tamtoc.world.*;
import vn.tamtoc.combat.*;
import vn.tamtoc.realtime.ProtocolException;

@Component
public class BotRegistry {
    private final Map<UUID,BotState> bots=new java.util.concurrent.ConcurrentHashMap<>();private final MapCatalog maps;private final BotCatalog catalog;private final BotBrain brain;
    public BotRegistry(MapCatalog maps,BotCatalog catalog,BotBrain brain){this.maps=maps;this.catalog=catalog;this.brain=brain;for(var map:maps.all())for(var spawn:map.botSpawnGroups()){var bot=create(map,spawn);bots.put(bot.actor.id,bot);}}
    private BotState create(MapCatalog.MapDefinition map,MapCatalog.BotSpawn spawn){var def=catalog.definitions.get(spawn.definitionId());return new BotState(def,catalog.profiles.get(def.behaviorProfileId()),map,spawn);}
    public List<BotState> all(){return List.copyOf(bots.values());}
    public List<PlayerState> guards(){return bots.values().stream().filter(b->!b.training&&b.definition.botType().equals("GUARD")&&b.actor.progress.hp>0).map(b->b.actor).toList();}
    public List<BotState.View> views(PlayerState p){return bots.values().stream().filter(b->b.actor.mapId.equals(p.mapId)&&Objects.equals(b.actor.instanceId,p.instanceId)).map(BotState::view).toList();}
    public BotState interactable(PlayerState p,UUID id){var bot=bots.get(id);if(bot==null||bot.training||!bot.definition.interactable()||bot.actor.progress.hp<=0)throw new ProtocolException("NPC_NOT_AVAILABLE","NPC không thể tương tác.");if(!bot.actor.mapId.equals(p.mapId)||!Objects.equals(bot.actor.instanceId,p.instanceId)||p.progress.hp<=0||Math.hypot(p.x-bot.actor.x,p.y-bot.actor.y)>bot.interactionRadius)throw new ProtocolException("NPC_TOO_FAR","Hãy tới gần NPC trước khi tương tác.");return bot;}
    public Optional<BotState> nearest(PlayerState p){return bots.values().stream().filter(b->!b.training&&b.definition.interactable()&&b.actor.progress.hp>0&&b.actor.mapId.equals(p.mapId)&&Objects.equals(b.actor.instanceId,p.instanceId)&&Math.hypot(p.x-b.actor.x,p.y-b.actor.y)<=b.interactionRadius).min(Comparator.comparingDouble(b->Math.hypot(p.x-b.actor.x,p.y-b.actor.y)));}
    public Object dialogue(PlayerState p,UUID id,long now){var bot=interactable(p,id);bot.talkUntil=now+2200;bot.nextThinkAt=0;bot.state=BotState.State.TALK;bot.actor.navigation=null;var lines=catalog.dialogues.get(bot.definition.dialogueId()).lines();String line=lines.get(bot.dialogueIndex++%lines.size());return Map.of("entityId",bot.actor.id,"name",bot.definition.name(),"isAI",true,"botType",bot.definition.botType(),"lines",List.of(line),"canTrain",bot.definition.botType().equals("CHALLENGER"));}
    public BotState training(BotState source,UUID instance,String difficulty){var map=maps.get("training-arena");var spawn=new MapCatalog.BotSpawn("training-opponent",source.definition.id(),"opponent",List.of(),100);var bot=create(map,spawn);bot.training=true;bot.actor.instanceId=instance;var level=catalog.difficulties.get(difficulty);if(level==null)throw new ProtocolException("TRAINING_DIFFICULTY_INVALID","Chọn EASY hoặc HARD.");bot.apply(level);bots.put(bot.actor.id,bot);return bot;}
    public void remove(UUID id){bots.remove(id);}
    public List<String> script(String id){var script=catalog.dialogues.get(id);if(script==null)throw new IllegalArgumentException("Unknown dialogue "+id);return script.lines();}
    public void step(List<MonsterState> monsters,double dt,long now,TriEvent event,BiConsumer<PlayerState,MonsterState> defeated){for(var bot:List.copyOf(bots.values()))if(!bot.training){var perceived=monsters.stream().filter(m->m.mapId.equals(bot.actor.mapId)&&Objects.equals(m.instanceId,bot.actor.instanceId)).toList();brain.step(bot,maps.get(bot.actor.mapId),perceived,dt,now,(t,p)->event.accept(bot,t,p),defeated);}}
    @FunctionalInterface public interface TriEvent {void accept(BotState bot,String type,Object payload);}
}
