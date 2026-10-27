package vn.tamtoc.world;
import java.util.UUID;
public record LootState(UUID id,String itemId,String mapId,double x,double y,long expiresAt) {}
