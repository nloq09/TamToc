package vn.tamtoc.realtime;

import java.util.*;
import java.util.concurrent.atomic.*;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

@Component
public class NetworkMetrics {
    public final LongAdder disconnects=new LongAdder(),resumes=new LongAdder(),resumeFailures=new LongAdder(),exceptions=new LongAdder(),messages=new LongAdder(),bytes=new LongAdder(),ticks=new LongAdder(),tickNanos=new LongAdder(),slowTicks=new LongAdder();
    public final AtomicLong maxTickNanos=new AtomicLong();
    public volatile double lastTickMs;
    private static final int SAMPLE_CAPACITY=20000;
    private final long[] sampleIds=new long[SAMPLE_CAPACITY],sampleNanos=new long[SAMPLE_CAPACITY];
    public synchronized void tick(long nanos,long budgetMs){lastTickMs=nanos/1e6;ticks.increment();long sequence=ticks.sum();int index=(int)(sequence%SAMPLE_CAPACITY);sampleIds[index]=sequence;sampleNanos[index]=nanos;tickNanos.add(nanos);maxTickNanos.accumulateAndGet(nanos,Math::max);if(nanos>budgetMs*1_000_000)slowTicks.increment();}
    public synchronized Map<String,Object> percentiles(long sinceTick){long end=ticks.sum();long[] values=new long[SAMPLE_CAPACITY];int count=0;for(long id=sinceTick>=end?end+1:Math.max(1,Math.max(sinceTick+1,end-SAMPLE_CAPACITY+1));id<=end;id++){int index=(int)(id%SAMPLE_CAPACITY);if(sampleIds[index]==id)values[count++]=sampleNanos[index];}Arrays.sort(values,0,count);return Map.of("tickSamples",count,"sampleCapacity",SAMPLE_CAPACITY,"sinceTick",sinceTick,"p50TickMs",quantile(values,count,.5),"p95TickMs",quantile(values,count,.95),"p99TickMs",quantile(values,count,.99),"windowMaxTickMs",count==0?0:values[count-1]/1e6);}
    private static double quantile(long[] values,int count,double q){return count==0?0:values[Math.max(0,(int)Math.ceil(count*q)-1)]/1e6;}
    public Map<String,Object> view(CharacterSessions sessions){
        return Map.ofEntries(Map.entry("onlinePlayers",sessions.online().size()),Map.entry("gracePlayers",sessions.all().size()-sessions.online().size()),
            Map.entry("disconnects",disconnects.sum()),Map.entry("resumes",resumes.sum()),Map.entry("resumeFailures",resumeFailures.sum()),Map.entry("exceptions",exceptions.sum()),
            Map.entry("outgoingMessages",messages.sum()),Map.entry("outgoingBytes",bytes.sum()),Map.entry("ticks",ticks.sum()),Map.entry("meanTickMs",ticks.sum()==0?0:tickNanos.sum()/1e6/ticks.sum()),Map.entry("maxTickMs",maxTickNanos.get()/1e6),Map.entry("slowTicks",slowTicks.sum()));
    }
    @RestController static class Controller {
        private final NetworkMetrics metrics;private final CharacterSessions sessions;private final vn.tamtoc.party.PartyRegistry parties;private final vn.tamtoc.instance.InstanceRegistry instances;private final vn.tamtoc.bot.BotRegistry bots;private final vn.tamtoc.combat.MonsterPopulation population;private final vn.tamtoc.world.WorldInterest interest;
        Controller(NetworkMetrics m,CharacterSessions s,vn.tamtoc.party.PartyRegistry p,vn.tamtoc.instance.InstanceRegistry i,vn.tamtoc.bot.BotRegistry bots,vn.tamtoc.combat.MonsterPopulation population,vn.tamtoc.world.WorldInterest interest){this.interest=interest;metrics=m;sessions=s;parties=p;instances=i;this.bots=bots;this.population=population;}
        @GetMapping("/api/network/metrics") public Object metrics(@RequestParam(defaultValue="0")long sinceTick){var view=new LinkedHashMap<>(metrics.view(sessions));view.putAll(metrics.percentiles(Math.max(0,sinceTick)));Set<String> activeMaps=new HashSet<>();sessions.all().forEach(l->activeMaps.add(l.player.mapId));view.put("activeMaps",activeMaps);view.put("simulatedMonsterSlots",population.all().stream().filter(m->activeMaps.contains(m.mapId)).count());view.put("activeNpcEntities",bots.all().stream().filter(b->activeMaps.contains(b.actor.mapId)).count());view.put("interestConnections",interest.cachedConnections());view.put("deltaClients",sessions.online().stream().filter(l->l.deltaUpdates).count());view.put("activeParties",parties.all().size());view.put("activeInstances",instances.all().stream().filter(i->i.active).count());view.put("allocatedInstances",instances.all().size());view.put("npcEntities",bots.all().size());view.put("trainingNpcEntities",bots.all().stream().filter(b->b.training).count());view.put("monsterSlots",population.all().size());view.put("aliveMonsters",population.all().stream().filter(monster->monster.hp>0).count());return view;}
    }
}
