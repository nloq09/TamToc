package vn.tamtoc.progression;

import java.util.*;
import org.springframework.stereotype.Component;
import vn.tamtoc.world.PlayerState;

@Component
public class ProgressionRules {
    private final ProgressionCatalog catalog;
    private final vn.tamtoc.world.MapCatalog maps;
    public ProgressionRules(ProgressionCatalog c){this(c,null);}
    @org.springframework.beans.factory.annotation.Autowired public ProgressionRules(ProgressionCatalog c,vn.tamtoc.world.MapCatalog maps){catalog=c;this.maps=maps;}
    public void validateWorldData(vn.tamtoc.world.MapCatalog maps,vn.tamtoc.combat.CombatCatalog combat){for(var q:catalog.quests.values())if(q.type().equals("KILL")&&(!combat.monsters.containsKey(q.target())||(q.mapId()!=null&&!maps.contains(q.mapId()))))throw new IllegalArgumentException("Invalid quest target/map "+q.id());for(var m:maps.all())for(var portal:m.portals())if(portal.requiredQuest()!=null&&!catalog.quests.containsKey(portal.requiredQuest()))throw new IllegalArgumentException("Unknown portal quest "+portal.portalId());}
    private boolean nearGuide(PlayerState p){return p.progress.hp>0&&p.instanceId==null&&(maps==null?Math.abs(p.x-300)<100:maps.get(p.mapId).npcSpawns().stream().anyMatch(n->n.type().equals("GUIDE")&&Math.hypot(p.x-n.x(),p.y-n.y())<100));}
    private static String killKey(ProgressionCatalog.Quest q){return q.mapId()==null?q.target():q.mapId()+":"+q.target();}
    public int organizationLevel(){return catalog.data.organizationLevel();}
    public boolean hasItem(String id){return catalog.items.containsKey(id);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    public void accept(PlayerState p,String id){
        var q=catalog.quests.get(id);require(q!=null,"Nhiệm vụ không tồn tại.");
        require(p.level>=(id.equals("PATH")?catalog.data.organizationLevel():q.requiredLevel()),"Chưa đủ cấp nhận nhiệm vụ.");
        require(!p.progress.quests.containsKey(id),"Đã nhận hoặc hoàn thành nhiệm vụ.");
        require(nearGuide(p),"Hãy gặp Tiếp Dẫn Sứ để nhận nhiệm vụ.");
        p.progress.quests.put(id,"ACTIVE");if(id.equals("WOLVES"))p.progress.questWolfStart=p.progress.wolfKills;
        if(q.type().equals("KILL")&&!id.equals("WOLVES"))p.progress.questKillStarts.put(id,p.progress.killsByDefinition.getOrDefault(killKey(q),0));
        if(id.equals("PATH"))p.progress.guideMet=true;
    }
    public int questProgress(PlayerState p,String id){
        var q=catalog.quests.get(id);if(q==null)return 0;
        if(id.equals("WOLVES"))return Math.min(q.count(),Math.max(0,p.progress.wolfKills-p.progress.questWolfStart));
        if(q.type().equals("KILL"))return Math.min(q.count(),Math.max(0,p.progress.killsByDefinition.getOrDefault(killKey(q),0)-p.progress.questKillStarts.getOrDefault(id,0)));
        return p.organization==null?0:1;
    }
    public void claim(PlayerState p,String id){
        var q=catalog.quests.get(id);require(q!=null,"Nhiệm vụ không tồn tại.");
        require("ACTIVE".equals(p.progress.quests.get(id)),"Nhiệm vụ chưa nhận hoặc đã lĩnh thưởng.");
        require(questProgress(p,id)>=q.count(),"Chưa hoàn thành mục tiêu nhiệm vụ.");
        p.progress.quests.put(id,"CLAIMED");addExp(p,q.exp());p.cultivation+=q.cultivation();p.progress.stones+=q.stones();
    }
    public void trial(PlayerState p,String id){
        var o=catalog.organizations.get(id);require(o!=null,"Thế lực không tồn tại.");
        require(p.organization==null,"Đã gia nhập một thế lực chính.");
        require(p.level>=catalog.data.organizationLevel(),"Chưa đủ cấp gia nhập thế lực.");
        require(o.race().equals(p.race),"Thế lực không thuộc chủng tộc của ngươi.");
        require(p.progress.guideMet&&"ACTIVE".equals(p.progress.quests.get("PATH")),"Hãy nhận nhiệm vụ Con Đường Tu Luyện tại NPC.");
        require(nearGuide(p),"Hãy gặp Tiếp Dẫn Sứ để bắt đầu khảo hạch.");
        require(p.progress.trialOrganization==null,"Đã chọn khảo hạch. Hoàn thành thử thách đang nhận.");
        p.progress.trialOrganization=id;p.progress.trialKillStart=p.progress.totalKills;
    }
    public void join(PlayerState p,String id){
        var o=catalog.organizations.get(id);require(o!=null,"Thế lực không tồn tại.");
        require(p.organization==null,"Mỗi nhân vật chỉ có một thế lực chính.");
        require(p.level>=catalog.data.organizationLevel(),"Chưa đủ cấp gia nhập thế lực.");
        require(o.race().equals(p.race),"Không thể gia nhập thế lực của tộc khác.");
        require(id.equals(p.progress.trialOrganization)&&p.progress.guideMet,"Chưa nhận khảo hạch của thế lực này.");
        require(p.progress.totalKills-p.progress.trialKillStart>=catalog.data.trialKills(),"Chưa hoàn thành khảo hạch.");
        require(nearGuide(p),"Hãy trở lại Tiếp Dẫn Sứ để xác nhận gia nhập.");
        p.organization=id;p.path=o.path();
        if(p.path.equals("KIEM_TU"))p.progress.inventory.put("QI_SWORD",1);
    }
    public long breakthroughCost(PlayerState p){return (p.realmStage+1)*100L*(p.realm.equals("TRUC_CO")?2:1);}
    public int breakthroughLevel(PlayerState p){return (p.realmStage+1)*2+(p.realm.equals("TRUC_CO")?8:0);}
    public void breakthrough(PlayerState p){
        require(!(p.realm.equals("TRUC_CO")&&p.realmStage==3),"Đã đạt giới hạn cảnh giới của MVP.");
        require(p.progress.hp>0,"Không thể đột phá khi tử trận.");
        require(p.level>=breakthroughLevel(p),"Chưa đủ cấp để đột phá.");
        require(p.cultivation>=breakthroughCost(p),"Chưa đủ tu vi.");
        p.cultivation-=breakthroughCost(p);
        if(p.realmStage==3){p.realm="TRUC_CO";p.realmStage=0;}else p.realmStage++;
        p.progress.hp=p.maxHp();
    }
    public void equip(PlayerState p,String itemId){
        var item=catalog.items.get(itemId);require(item!=null&&item.slot()!=null,"Vật phẩm không phải trang bị.");
        require(p.progress.inventory.getOrDefault(itemId,0)>0,"Không sở hữu vật phẩm.");
        require(p.level>=item.level(),"Chưa đủ cấp trang bị.");
        require(item.path()==null||item.path().equals(p.path),"Trang bị không thuộc hệ tu luyện.");
        require(item.realm()==null||item.realm().equals(p.realm)||p.realm.equals("TRUC_CO"),"Chưa đủ cảnh giới.");
        p.progress.equipment.put(item.slot(),itemId);
        recalculate(p);
    }
    public void unequip(PlayerState p,String slot){require(p.progress.equipment.containsKey(slot),"Ô trang bị đang trống.");p.progress.equipment.remove(slot);recalculate(p);}
    public void recalculate(PlayerState p){p.equipmentAttack=0;p.equipmentDefense=0;for(String id:p.progress.equipment.values()){var item=catalog.items.get(id);if(item!=null){p.equipmentAttack+=item.attack();p.equipmentDefense+=item.defense();}}}
    public void use(PlayerState p,String id){
        require(id.equals("POTION")&&p.progress.inventory.getOrDefault(id,0)>0,"Không thể sử dụng vật phẩm.");
        require(p.progress.hp>0,"Đang chờ hồi sinh.");
        require(p.progress.hp<p.maxHp()||p.progress.mp<p.maxMp(),"HP và MP đã đầy.");
        p.progress.inventory.compute(id,(k,v)->v-1);p.progress.hp=Math.min(p.maxHp(),p.progress.hp+55);p.progress.mp=Math.min(p.maxMp(),p.progress.mp+30);
    }
    public void addItem(PlayerState p,String id){addItems(p,id,1);}
    public void addItems(PlayerState p,String id,int quantity){var item=catalog.items.get(id);require(item!=null,"Vật phẩm không tồn tại.");int count=p.progress.inventory.getOrDefault(id,0);require(quantity>0&&quantity<=item.stack()&&count<=item.stack()-quantity,"Chồng vật phẩm đã đầy.");p.progress.inventory.put(id,count+quantity);}
    public static boolean addExp(PlayerState p,long amount){int before=p.level;p.exp+=amount;while(p.exp>=p.level*100L){p.exp-=p.level*100L;p.level++;p.progress.hp=p.maxHp();p.progress.mp=p.maxMp();}return before!=p.level;}
}
