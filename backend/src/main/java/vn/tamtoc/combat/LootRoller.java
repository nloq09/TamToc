package vn.tamtoc.combat;

import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class LootRoller {
    public record ItemDrop(String itemId,int quantity) {}
    private final long privateSeed;
    @Autowired public LootRoller(){this(new java.security.SecureRandom().nextLong());}
    public LootRoller(long seed){privateSeed=seed;}
    public List<ItemDrop> roll(MonsterState monster,UUID owner){
        var random=new SplittableRandom(privateSeed^monster.id.getMostSignificantBits()^monster.id.getLeastSignificantBits()^owner.getMostSignificantBits()^owner.getLeastSignificantBits()^monster.deathOrdinal);var drops=new ArrayList<ItemDrop>();
        for(var drop:monster.definition.dropTable())if(random.nextDouble()<drop.chance())drops.add(new ItemDrop(drop.itemId(),random.nextInt(drop.minQuantity(),drop.maxQuantity()+1)));return drops;
    }
}
