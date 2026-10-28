package vn.tamtoc.progression;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tamtoc.character.CharacterRepository;
import vn.tamtoc.world.PlayerState;
import java.util.UUID;

@Service
public class ProgressStore {
    private final ProgressRepository docs; private final CharacterRepository chars; private final ObjectMapper json;
    public ProgressStore(ProgressRepository d,CharacterRepository c,ObjectMapper j){docs=d;chars=c;json=j;}
    public ProgressData load(UUID id) {return docs.findById(id).map(d->{try{return json.readValue(d.data,ProgressData.class);}catch(Exception e){throw new IllegalStateException("Corrupt character progress",e);}}).orElseGet(ProgressData::new);}
    public record Save(UUID id,String map,double x,double y,int level,long exp,long cultivation,String realm,int stage,String organization,String path,String data) {}
    public Save freeze(PlayerState p) {
        try{return new Save(p.id,p.returnMapId==null?p.mapId:p.returnMapId,p.returnMapId==null?p.x:p.returnX,p.returnMapId==null?p.y:p.returnY,p.level,p.exp,p.cultivation,p.realm,p.realmStage,p.organization,p.path,json.writeValueAsString(p.progress));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    @Transactional public void save(Save s) {
        var c=chars.findById(s.id()).orElseThrow();
        c.mapId=s.map();c.x=s.x();c.y=s.y();c.level=s.level();c.exp=s.exp();c.cultivation=s.cultivation();c.realm=s.realm();c.realmStage=s.stage();c.organization=s.organization();c.cultivationPath=s.path();
        docs.save(new ProgressDocument(s.id(),s.data()));
    }
}
