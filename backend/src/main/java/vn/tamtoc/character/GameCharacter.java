package vn.tamtoc.character;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="characters")
public class GameCharacter {
    @Id public UUID id;
    @Column(name="user_id", nullable=false) public UUID userId;
    public String name;
    @Enumerated(EnumType.STRING) public Race race;
    @Column(name="character_class") public String characterClass = "NOVICE";
    public String organization;
    @Column(name="cultivation_path") public String cultivationPath;
    public int level = 1;
    public long exp;
    public long cultivation;
    public String realm = "LUYEN_KHI";
    @Column(name="realm_stage") public int realmStage;
    @Column(name="map_id") public String mapId;
    public double x = 160;
    public double y = 500;
    protected GameCharacter() {}
    public GameCharacter(UUID owner, String name, Race race) {
        id = UUID.randomUUID(); userId = owner; this.name = name; this.race = race; mapId = race.startMap;
    }
}
