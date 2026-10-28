package vn.tamtoc.realtime;

import java.util.*;
import java.util.concurrent.atomic.*;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

@Component
public class NetworkMetrics {
    public final LongAdder disconnects=new LongAdder(),resumes=new LongAdder(),resumeFailures=new LongAdder(),exceptions=new LongAdder(),messages=new LongAdder(),bytes=new LongAdder(),ticks=new LongAdder(),tickNanos=new LongAdder(),slowTicks=new LongAdder();
    public final AtomicLong maxTickNanos=new AtomicLong();
    public void tick(long nanos,long budgetMs){ticks.increment();tickNanos.add(nanos);maxTickNanos.accumulateAndGet(nanos,Math::max);if(nanos>budgetMs*1_000_000)slowTicks.increment();}
    public Map<String,Object> view(CharacterSessions sessions){
        return Map.ofEntries(Map.entry("onlinePlayers",sessions.online().size()),Map.entry("gracePlayers",sessions.all().size()-sessions.online().size()),
            Map.entry("disconnects",disconnects.sum()),Map.entry("resumes",resumes.sum()),Map.entry("resumeFailures",resumeFailures.sum()),Map.entry("exceptions",exceptions.sum()),
            Map.entry("outgoingMessages",messages.sum()),Map.entry("outgoingBytes",bytes.sum()),Map.entry("ticks",ticks.sum()),Map.entry("meanTickMs",ticks.sum()==0?0:tickNanos.sum()/1e6/ticks.sum()),Map.entry("maxTickMs",maxTickNanos.get()/1e6),Map.entry("slowTicks",slowTicks.sum()));
    }
    @RestController static class Controller {
        private final NetworkMetrics metrics;private final CharacterSessions sessions;private final vn.tamtoc.party.PartyRegistry parties;private final vn.tamtoc.instance.InstanceRegistry instances;
        Controller(NetworkMetrics m,CharacterSessions s,vn.tamtoc.party.PartyRegistry p,vn.tamtoc.instance.InstanceRegistry i){metrics=m;sessions=s;parties=p;instances=i;}
        @GetMapping("/api/network/metrics") public Object metrics(){var view=new LinkedHashMap<>(metrics.view(sessions));view.put("activeParties",parties.all().size());view.put("activeInstances",instances.all().stream().filter(i->i.active).count());view.put("allocatedInstances",instances.all().size());return view;}
    }
}
