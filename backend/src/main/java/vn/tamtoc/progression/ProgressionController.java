package vn.tamtoc.progression;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.tamtoc.world.*;
import vn.tamtoc.combat.CombatCatalog;
import vn.tamtoc.security.RateLimits;

@RestController @RequestMapping("/api")
public class ProgressionController {
    private final GameWorld world;private final ProgressionCatalog catalog;private final ProgressionRules rules;private final CombatCatalog skills;private final RateLimits limits;
    public ProgressionController(GameWorld w,ProgressionCatalog c,ProgressionRules r,CombatCatalog s,RateLimits l){world=w;catalog=c;rules=r;skills=s;limits=l;}
    public record ItemRequest(@NotBlank String itemId) {}
    public record SlotRequest(@NotBlank String slot) {}
    public record OrganizationRequest(@NotBlank String organizationId) {}
    private <T>T read(Jwt j,UUID id,java.util.function.Function<PlayerState,T> action){return world.access(UUID.fromString(j.getSubject()),id,action,false);}
    private Map<String,String> mutate(Jwt j,UUID id,java.util.function.Consumer<PlayerState> action){limits.check("mutate:"+j.getSubject(),90);return world.access(UUID.fromString(j.getSubject()),id,p->{action.accept(p);return Map.of("status","ok");},true);}
    @GetMapping("/organizations") public Object organizations(){return catalog.data.organizations();}
    @GetMapping("/characters/{id}/available-organizations") public Object available(@AuthenticationPrincipal Jwt j,@PathVariable UUID id){return read(j,id,p->{var out=new LinkedHashMap<String,Object>();out.put("requiredLevel",catalog.data.organizationLevel());out.put("level",p.level);out.put("currentOrganization",p.organization);out.put("trialOrganization",p.progress.trialOrganization);out.put("trialProgress",Math.max(0,p.progress.totalKills-p.progress.trialKillStart));out.put("trialRequired",catalog.data.trialKills());out.put("guideMet",p.progress.guideMet);out.put("pathQuestAccepted","ACTIVE".equals(p.progress.quests.get("PATH")));out.put("organizations",catalog.data.organizations().stream().filter(o->o.race().equals(p.race)).toList());return out;});}
    @GetMapping("/characters/{id}/organization") public Object organization(@AuthenticationPrincipal Jwt j,@PathVariable UUID id){return read(j,id,p->{var out=new LinkedHashMap<String,Object>();out.put("organization",p.organization==null?null:catalog.organizations.get(p.organization));out.put("path",p.path);return out;});}
    @PostMapping("/characters/{id}/organization/trial") public Object trial(@AuthenticationPrincipal Jwt j,@PathVariable UUID id,@Valid @RequestBody OrganizationRequest r){return mutate(j,id,p->rules.trial(p,r.organizationId()));}
    @PostMapping("/characters/{id}/organization/join") public Object join(@AuthenticationPrincipal Jwt j,@PathVariable UUID id,@Valid @RequestBody OrganizationRequest r){return mutate(j,id,p->rules.join(p,r.organizationId()));}
    @GetMapping("/characters/{id}/skills") public Object skills(@AuthenticationPrincipal Jwt j,@PathVariable UUID id){return read(j,id,skills::unlocked);}
    @GetMapping("/characters/{id}/cultivation") public Object cultivation(@AuthenticationPrincipal Jwt j,@PathVariable UUID id){return read(j,id,p->Map.of("realm",p.realm,"stage",p.realmStage,"points",p.cultivation,"requiredPoints",rules.breakthroughCost(p),"requiredLevel",rules.breakthroughLevel(p),"level",p.level,"atLimit",p.realm.equals("TRUC_CO")&&p.realmStage==3));}
    @PostMapping("/characters/{id}/cultivation/breakthrough") public Object breakthrough(@AuthenticationPrincipal Jwt j,@PathVariable UUID id){return mutate(j,id,rules::breakthrough);}
    @GetMapping("/characters/{id}/inventory") public Object inventory(@AuthenticationPrincipal Jwt j,@PathVariable UUID id){return read(j,id,p->Map.of("items",p.progress.inventory,"equipment",p.progress.equipment,"definitions",catalog.data.items(),"stones",p.progress.stones,"attack",p.attack(),"defense",p.defense()));}
    @PostMapping("/characters/{id}/inventory/equip") public Object equip(@AuthenticationPrincipal Jwt j,@PathVariable UUID id,@Valid @RequestBody ItemRequest r){return mutate(j,id,p->rules.equip(p,r.itemId()));}
    @PostMapping("/characters/{id}/inventory/unequip") public Object unequip(@AuthenticationPrincipal Jwt j,@PathVariable UUID id,@Valid @RequestBody SlotRequest r){return mutate(j,id,p->rules.unequip(p,r.slot()));}
    @PostMapping("/characters/{id}/inventory/use") public Object use(@AuthenticationPrincipal Jwt j,@PathVariable UUID id,@Valid @RequestBody ItemRequest r){return mutate(j,id,p->rules.use(p,r.itemId()));}
    @GetMapping("/characters/{id}/quests") public Object quests(@AuthenticationPrincipal Jwt j,@PathVariable UUID id){return read(j,id,p->catalog.data.quests().stream().map(q->Map.of("definition",q,"status",p.progress.quests.getOrDefault(q.id(),"AVAILABLE"),"progress",rules.questProgress(p,q.id()),"requiredLevel",q.id().equals("PATH")?catalog.data.organizationLevel():q.requiredLevel())).toList());}
    @PostMapping("/characters/{id}/quests/{quest}/accept") public Object accept(@AuthenticationPrincipal Jwt j,@PathVariable UUID id,@PathVariable String quest){return mutate(j,id,p->rules.accept(p,quest));}
    @PostMapping("/characters/{id}/quests/{quest}/claim") public Object claim(@AuthenticationPrincipal Jwt j,@PathVariable UUID id,@PathVariable String quest){return mutate(j,id,p->rules.claim(p,quest));}
}
