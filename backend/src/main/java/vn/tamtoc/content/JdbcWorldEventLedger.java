package vn.tamtoc.content;
import java.sql.Timestamp;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;

@Repository @DependsOn("flywayInitializer")
public class JdbcWorldEventLedger implements WorldEventLedger {
    private final JdbcTemplate jdbc;
    public JdbcWorldEventLedger(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public boolean claim(String event,long cycle,long now){return jdbc.update("INSERT INTO world_event_cycles(event_key,cycle,started_at) VALUES (?,?,?) ON CONFLICT DO NOTHING",event,cycle,new Timestamp(now))==1;}
}
