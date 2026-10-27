package vn.tamtoc.progression;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="character_progress")
public class ProgressDocument {
    @Id @Column(name="character_id") public UUID characterId;
    @Column(columnDefinition="text") public String data;
    protected ProgressDocument() {}
    public ProgressDocument(UUID id,String data) {characterId=id;this.data=data;}
}
