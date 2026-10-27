package vn.tamtoc.progression;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import vn.tamtoc.character.*;
import vn.tamtoc.world.*;

class ProgressionTest {
    ProgressionCatalog catalog;ProgressionRules rules;PlayerState p;
    @BeforeEach void setup()throws Exception{catalog=new ProgressionCatalog(new ObjectMapper());rules=new ProgressionRules(catalog);p=player(Race.HUMAN);}
    PlayerState player(Race race){var p=new PlayerState(new GameCharacter(UUID.randomUUID(),"Tester",race));p.x=300;p.y=540;return p;}
    @Test void questRequiresNpcAndFiveNewKillsAndRewardsOnlyOnce(){
        p.x=800;assertThrows(IllegalArgumentException.class,()->rules.accept(p,"WOLVES"));p.x=300;p.progress.wolfKills=8;
        rules.accept(p,"WOLVES");assertEquals(0,rules.questProgress(p,"WOLVES"));
        assertThrows(IllegalArgumentException.class,()->rules.claim(p,"WOLVES"));
        p.progress.wolfKills+=5;rules.claim(p,"WOLVES");assertEquals(40,p.progress.stones);
        assertThrows(IllegalArgumentException.class,()->rules.claim(p,"WOLVES"));assertEquals(40,p.progress.stones);
    }
    @Test void validatesLevelRaceTrialAndSingleMembership(){
        assertThrows(IllegalArgumentException.class,()->rules.join(p,"THANH_VAN"));p.level=10;
        assertThrows(IllegalArgumentException.class,()->rules.join(p,"HUYET_SAT"));
        assertThrows(IllegalArgumentException.class,()->rules.join(p,"THANH_VAN"));
        rules.accept(p,"PATH");rules.trial(p,"THANH_VAN");
        assertThrows(IllegalArgumentException.class,()->rules.join(p,"THANH_VAN"));p.progress.totalKills+=3;
        rules.join(p,"THANH_VAN");assertEquals("KIEM_TU",p.path);
        assertThrows(IllegalArgumentException.class,()->rules.join(p,"NGU_HANH"));
    }
    @Test void allNineOrganizationsUseThreeArchetypes(){
        for(var o:catalog.data.organizations()){
            var actor=player(Race.valueOf(o.race()));actor.level=10;rules.accept(actor,"PATH");rules.trial(actor,o.id());actor.progress.totalKills+=3;rules.join(actor,o.id());assertEquals(o.path(),actor.path);
        }
        assertEquals(3,catalog.data.organizations().stream().map(ProgressionCatalog.Organization::archetype).distinct().count());
    }
    @Test void inventoryValidatesOwnershipPathLevelAndRecalculatesStats(){
        assertThrows(IllegalArgumentException.class,()->rules.equip(p,"QI_SWORD"));
        rules.unequip(p,"WEAPON");assertEquals(0,p.equipmentAttack);rules.equip(p,"WOOD_SWORD");assertEquals(4,p.equipmentAttack);
        p.progress.inventory.put("QI_SWORD",1);p.level=10;assertThrows(IllegalArgumentException.class,()->rules.equip(p,"QI_SWORD"));
        p.path="KIEM_TU";rules.equip(p,"QI_SWORD");assertEquals(18,p.equipmentAttack);
        p.progress.hp=1;rules.use(p,"POTION");assertEquals(56,p.progress.hp);assertEquals(2,p.progress.inventory.get("POTION"));
    }
    @Test void levelAndCultivationAreIndependentAndMvpRealmIsCapped(){
        assertThrows(IllegalArgumentException.class,()->rules.breakthrough(p));p.level=20;p.cultivation=10000;
        for(int i=0;i<7;i++)rules.breakthrough(p);
        assertEquals("TRUC_CO",p.realm);assertEquals(3,p.realmStage);assertEquals(20,p.level);
        assertThrows(IllegalArgumentException.class,()->rules.breakthrough(p));
    }
}
