package vn.tamtoc.instance;

import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import jakarta.annotation.PostConstruct;
import vn.tamtoc.progression.ProgressStore;

@Repository @DependsOn("flywayInitializer")
public class JdbcActivityStore implements ActivityStore {
    private final JdbcTemplate jdbc;private final ProgressStore progress;
    private final String node;
    public JdbcActivityStore(JdbcTemplate j,ProgressStore p,@Value("${NODE_ID:local}")String node,@Value("${server.port:8080}")int port){jdbc=j;progress=p;this.node=node+":"+port;}
    @PostConstruct void recover(){jdbc.update("UPDATE dungeon_runs SET status='FAILED',ended_at=now(),result='SERVER_RESTART' WHERE status='ACTIVE' AND server_node=?",node);}
    @Override @Transactional public void startDungeon(UUID run,UUID instance,UUID party,Collection<UUID> participants,long started){startDungeon(run,instance,party,participants,started,"HUYEN_MOC");}
    @Override @Transactional public void startDungeon(UUID run,UUID instance,UUID party,Collection<UUID> participants,long started,String dungeon){
        jdbc.update("INSERT INTO dungeon_runs(id,instance_id,dungeon_id,party_id,server_node,status,started_at) VALUES (?,?,?,?,?,'ACTIVE',?)",run,instance,dungeon,party,node,new Timestamp(started));
        for(UUID id:participants)jdbc.update("INSERT INTO dungeon_run_participants(run_id,character_id) VALUES (?,?)",run,id);
    }
    @Override @Transactional public boolean completeDungeon(UUID run,Map<UUID,ProgressStore.Save> snapshots,String rewards){
        String status=jdbc.queryForObject("SELECT status FROM dungeon_runs WHERE id=? FOR UPDATE",String.class,run);
        if("COMPLETED".equals(status))return false;
        if(!"ACTIVE".equals(status))throw new IllegalStateException("Dungeon run is no longer active");
        for(var entry:snapshots.entrySet()){
            int inserted=jdbc.update("INSERT INTO dungeon_reward_grants(run_id,character_id,reward_key) VALUES (?,?,'COMPLETION') ON CONFLICT DO NOTHING",run,entry.getKey());
            if(inserted==1)progress.save(entry.getValue());
            jdbc.update("UPDATE dungeon_run_participants SET eligible=true,result=? WHERE run_id=? AND character_id=?",rewards,run,entry.getKey());
        }
        jdbc.update("UPDATE dungeon_runs SET status='COMPLETED',ended_at=now(),result=? WHERE id=?",rewards,run);return true;
    }
    @Override @Transactional public void finishDungeon(UUID run,String status,String reason){jdbc.update("UPDATE dungeon_runs SET status=?,ended_at=now(),result=? WHERE id=? AND status='ACTIVE'",status,reason,run);}
    @Override @Transactional public void finishDuel(UUID duel,UUID challenger,UUID opponent,UUID winner,String reason,long started,long ended){jdbc.update("INSERT INTO duel_results(id,challenger_id,opponent_id,winner_id,reason,started_at,ended_at) VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",duel,challenger,opponent,winner,reason,new Timestamp(started),new Timestamp(ended));}
}
