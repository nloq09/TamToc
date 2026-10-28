package vn.tamtoc.world;
import java.util.UUID;
public record LootState(UUID id,String itemId,String mapId,double x,double y,long expiresAt,UUID ownerId,UUID instanceId,int quantity) {
    public LootState(UUID id,String itemId,String mapId,double x,double y,long expiresAt,UUID ownerId,UUID instanceId){this(id,itemId,mapId,x,y,expiresAt,ownerId,instanceId,1);}
}
