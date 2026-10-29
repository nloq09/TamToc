"""Reviewed data build using Phase 7 geometry, authorized by the user on 28/10/2026.
Never runs automatically at server startup. Original proposal and deployed IDs stay intact.
"""
import copy
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'backend/src/main/resources'
OLD = ROOT / 'docs/content-history/phase7'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
def save(name, value):
    (RES / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

plan = read(RES / 'world-content-15-v1.json')
enable_world_boss = '--enable-world-boss' in sys.argv
bindings = read(RES / 'world15-bindings.json')
aliases = bindings['mapIds']
maps = {m['id']: m for m in read(OLD / 'maps.json')}
combat = read(OLD / 'combat.json')
progress = read(OLD / 'progression.json')
bots = read(OLD / 'bots.json')
species = {m['monsterId']: m for m in plan['monsterDefinitions']}
base_field = copy.deepcopy(maps['thanh-van-ngoai-son'])
base_shared = copy.deepcopy(maps['tam-gioi-bien-canh'])
base_dungeon = copy.deepcopy(maps['huyen-moc-party'])

def spawn(m, id, x):
    m['spawnPoints'] = [p for p in m['spawnPoints'] if p['id'] != id] + [{'id': id, 'x': x, 'y': m['ground']}]

def portal(source, target, id, x, level=1, target_spawn='entry'):
    m=maps[aliases[source]]
    existing=next((p for p in m['portals'] if p['target']==aliases[target] and abs(p['x']-x)<1),None)
    if existing:
        existing['requiredLevel']=max(existing['requiredLevel'],level)
        return existing['portalId']
    p={'x':x,'target':aliases[target],'label':next(v['name'] for v in plan['maps'] if v['mapId']==target),'portalId':id,'sourceMapId':m['id'],'triggerBounds':{'x':x-60,'y':m['ground']-100,'width':120,'height':120},'targetSpawnId':target_spawn,'requiredLevel':level,'requiredQuest':None,'enabled':True}
    m['portals']=[v for v in m['portals'] if v['portalId']!=id]+[p]
    return id

for row in plan['maps']:
    rid=aliases[row['mapId']]
    if rid not in maps:
        m=copy.deepcopy(base_dungeon if row['type']=='DUNGEON' else base_shared if row['mapId']=='vo_chu_hoang_nguyen' else base_field)
        m['id']=rid;m['name']=row['name'];m['portals']=[]
        m['monsterSpawnGroups']=[]
        for b in m['botSpawnGroups']: b['id']=rid+'-'+b['id'].split('-')[-1]
        maps[rid]=m
    m=maps[rid];m['recommendedLevelMin']=row['recommendedLevelMin'];m['recommendedLevelMax']=row['recommendedLevelMax'];m['raceTheme']={'NHAN':'HUMAN','MA':'DEMON','YEU':'BEAST'}.get(row['region'],'NEUTRAL');m['version']+=1
    # Preserve existing spawn/NPC/portal IDs. Move no saved actor or inventory here.
    m['monsterSpawnGroups']=[]
    if row['safe']:
        m['safeZones'].append({'id':'world15-town-safe','bounds':{'x':0,'y':0,'width':m['width'],'height':m['height']},'spawnId':None,'heal':False,'protectedArea':True})
    elif row['type']!='DUNGEON':
        spawn(m,'east-entry',m['width']-170)
        m['safeZones'].append({'id':'world15-east-camp','bounds':{'x':m['width']-260,'y':m['ground']-140,'width':260,'height':150},'spawnId':'east-entry','heal':True,'protectedArea':True})
    if rid in ['truc_anh_lam','u_minh_ngoai_dao','thanh_khau_truc_lam']:
        for b in m['botSpawnGroups']:b['definitionId']={'HUMAN':'human','DEMON':'demon','BEAST':'beast'}[m['raceTheme']]+'-'+('guard' if b['definitionId'].endswith('guard') else 'wanderer')

connections=[('thanh_van_thanh','thanh_van_ngoai_son',190,100,1),('u_minh_thanh','huyet_vu_coc',190,100,1),('van_yeu_lam','linh_thu_son_lo',190,100,1),('thanh_van_ngoai_son','truc_anh_lam',2730,100,4),('huyet_vu_coc','u_minh_ngoai_dao',2730,100,4),('linh_thu_son_lo','thanh_khau_truc_lam',2730,100,4),('truc_anh_lam','tam_gioi_bien_canh',3030,520,6),('u_minh_ngoai_dao','tam_gioi_bien_canh',3030,640,6),('thanh_khau_truc_lam','tam_gioi_bien_canh',3030,760,6),('tam_gioi_bien_canh','trung_chau',3100,760,6),('trung_chau','vo_chu_hoang_nguyen',1080,100,8),('vo_chu_hoang_nguyen','thien_mon_di_tich',3450,240,15)]
for a,b,x,y,level in connections:
    # Keep live Phase 7 connections and add reviewed routes alongside them.
    key=a+'__'+b
    forward=portal(a,b,'world15-'+key,x,level)
    reverse=portal(b,a,'world15-'+b+'__'+a,y,1,'east-entry' if a not in ['thanh_van_thanh','u_minh_thanh','van_yeu_lam','trung_chau'] else 'entry')
    bindings['connectionBindings'][key]={'sourcePortalId':forward,'targetPortalId':reverse,'geometryVerified':True}
for a,b,x in [('vo_chu_hoang_nguyen','huyen_moc_bi_canh',3300),('thien_mon_di_tich','cuu_u_thach_phu',3450)]:
    key=a+'__'+b
    forward=portal(a,b,'world15-'+key,x,10 if b=='huyen_moc_bi_canh' else 18)
    bindings['connectionBindings'][key]={'sourcePortalId':forward,'encounterId':'HUYEN_MOC' if b=='huyen_moc_bi_canh' else 'CUU_U','geometryVerified':True}

legacy_species={'WOLF':'yeu_lang','SOLDIER':'ma_binh','SPIRIT':'linh_thu','BOSS':'huyen_moc_yeu_vuong'}
for m in combat['monsters']:m['speciesId']=legacy_species.get(m['id'],m['id'])
boss_template=next(m for m in combat['monsters'] if m['id']=='BOSS')
def monster(id, species_id, level, tier):
    multiplier={'NORMAL':1,'ELITE':3,'WORLD_BOSS':8,'DUNGEON_BOSS':6}[tier]
    ranged=species[species_id]['behaviorArchetype']=='RANGED' and species_id not in legacy_species.values()
    m={'id':id,'speciesId':species_id,'name':species[species_id]['name'],'level':level,'hp':45+12*level if tier=='NORMAL' else (150+25*level if tier=='ELITE' else 600+40*level),'atk':4+level+(15 if tier in ['DUNGEON_BOSS','WORLD_BOSS'] else 5 if tier=='ELITE' else 0),'def':level//4+(4 if tier!='NORMAL' else 0),'speed':85 if tier=='NORMAL' else 100,'detection':190 if tier=='NORMAL' else 280,'range':190 if ranged else 48,'respawnMs':18000,'exp':(30+12*level)*multiplier,'cultivation':(8+3*level)*multiplier,'tier':tier,'behavior':{'leashRadius':430,'chaseTimeoutMs':15000,'thinkIntervalMs':200,'targetStickMs':1500,'attackWindupMs':400 if ranged else 220,'attackCooldownMs':1400,'threatSwitchRatio':.35,'canJump':False,'jumpVelocity':570,'maxDrop':240,'halfWidth':16,'height':35},'dropTable':[{'itemId':'WOLF_FANG' if species_id=='yeu_lang' else 'ANCIENT_FRAGMENT','chance':1 if species_id=='yeu_lang' else .3,'minQuantity':1,'maxQuantity':1}]}
    if tier in ['WORLD_BOSS','DUNGEON_BOSS']:
        m['phases']=copy.deepcopy(boss_template['phases'])
    combat['monsters'].append(m)
    return id

for group in plan['monsterSpawnGroups']:
    map_id=group['mapId'];m=maps[aliases[map_id]];index=int(group['spawnGroupId'][-2:])-1
    if m['mapType']=='DUNGEON':x1,x2=(650,950)
    elif map_id in ['tam_gioi_bien_canh','vo_chu_hoang_nguyen']:x1,x2=[(850,1320),(2150,2410),(2670,2930)][index]
    elif map_id=='thien_mon_di_tich':x1,x2=[(740,1270),(1700,1840),(2210,2450)][index]
    else:x1,x2=[(650,1220),(1580,1810),(2230,2600)][index]
    definitions=[monster(group['spawnGroupId']+'_lv'+str(level),group['monsterId'],level,'NORMAL') for level in range(group['levelMin'],group['levelMax']+1)]
    definition=definitions[0]
    runtime={'id':group['spawnGroupId'],'definitionId':definition,'definitionIds':definitions,'maxAlive':group['maxAlive'],'startX':x1,'endX':x2,'y':m['ground'],'respawnMs':(group['respawn'].get('seconds') or 0)*1000,'enabled':True,'spawnPolicy':'ENCOUNTER_WAVE' if m['mapType']=='DUNGEON' else 'AFTER_DEATH'}
    m['monsterSpawnGroups'].append(runtime)
    bindings['spawnBindings'][group['spawnGroupId']]={'runtimeMapId':m['id'],'definitionId':definition,'geometryVerified':True,'enabled':True}
for boss in plan['bossPlacements']:
    m=maps[aliases[boss['mapId']]];policy=boss['spawnPolicy']['policy']
    x=900 if m['mapType']=='DUNGEON' else 3180 if boss['tier']=='WORLD_BOSS' else 2790 if m['width']==3200 else 3090
    if boss['mapId']=='thien_mon_di_tich':x=2850
    definition=monster(boss['placementId']+'_variant',boss['monsterId'],boss['level'],boss['tier'])
    runtime={'id':boss['placementId'],'placementId':boss['placementId'],'definitionId':definition,'maxAlive':1,'startX':x,'endX':x,'y':m['ground'],'respawnMs':(boss['spawnPolicy'].get('seconds') or 0)*1000,'enabled':boss['tier']!='WORLD_BOSS' or enable_world_boss,'spawnPolicy':policy,'scheduleIntervalMs':14400000 if policy=='SCHEDULED_EVENT' else 0,'activeDurationMs':1200000 if policy=='SCHEDULED_EVENT' else 0,'requiredLevel':max(8,boss['requiredLevel']) if policy=='SCHEDULED_EVENT' else boss['requiredLevel']}
    m['monsterSpawnGroups'].append(runtime)
    bindings['bossBindings'][boss['placementId']]={'runtimeMapId':m['id'],'definitionId':definition,'geometryVerified':True,'enabled':runtime['enabled'],'schedulerVerified':policy!='SCHEDULED_EVENT' or enable_world_boss,'encounterId':'HUYEN_MOC' if boss['mapId']=='huyen_moc_bi_canh' else 'CUU_U' if boss['mapId']=='cuu_u_thach_phu' else None}

# Existing quests keep IDs, rewards and claimed flags. The obsolete open-world boss moves to its proper encounter.
for q in progress['quests']:
    if q['id']=='RUINS_KING':q['mapId']='huyen-moc-party';q['description']='Hạ Huyền Mộc Yêu Vương trong Huyền Mộc Bí Cảnh. Giữ thưởng/tiến trình cũ.'
race_start={'NHAN':'thanh_van_ngoai_son','MA':'huyet_vu_coc','YEU':'linh_thu_son_lo'}
race_town={'NHAN':'thanh_van_thanh','MA':'u_minh_thanh','YEU':'van_yeu_lam'}
race_mid={'NHAN':'truc_anh_lam','MA':'u_minh_ngoai_dao','YEU':'thanh_khau_truc_lam'}
mid_boss={'NHAN':'truc_anh_lam_boss_elite','MA':'u_minh_ngoai_dao_boss_elite','YEU':'thanh_khau_truc_lam_boss_elite'}
for seed in plan['questSeeds']:
    qid=seed['questId'];cid=seed['mapId'];region=seed['region'];is_boss=seed['category']=='DEFEAT_BOSS_PLACEMENT'
    if qid=='hunt_tam_gioi_bien_canh':bindings['questBindings'][qid]={'questId':'WOLVES','linkedExisting':True};continue
    level=seed['recommendedLevel'];required_race={'NHAN':'HUMAN','MA':'DEMON','YEU':'BEAST'}.get(region)
    prerequisites=[];any_prerequisites=[]
    if qid.startswith('explore_') and region in race_start:
        prerequisites=['guide_'+race_town[region]] if cid==race_start[region] else ['hunt_'+race_start[region],'WOLVES']
    if qid.startswith('hunt_'):prerequisites=['explore_'+cid]
    if is_boss:
        prerequisites=['hunt_'+cid] if cid not in ['tam_gioi_bien_canh','huyen_moc_bi_canh','cuu_u_thach_phu'] else ['explore_'+cid]
        placement=next(b for b in plan['bossPlacements'] if b['placementId']==seed['objectives'][0]['targetId']);level=max(level,placement['level']-2)
    if qid=='explore_tam_gioi_bien_canh':any_prerequisites=['bounty_'+b for b in mid_boss.values()]
    if qid=='guide_trung_chau':prerequisites=['explore_tam_gioi_bien_canh'];level=10
    if qid=='explore_vo_chu_hoang_nguyen':prerequisites=['guide_trung_chau']
    if qid=='explore_huyen_moc_bi_canh':prerequisites=['explore_vo_chu_hoang_nguyen']
    if qid=='explore_thien_mon_di_tich':prerequisites=['hunt_vo_chu_hoang_nguyen']
    if qid=='explore_cuu_u_thach_phu':prerequisites=['explore_thien_mon_di_tich']
    if qid.startswith('clear_wave_'):prerequisites=['explore_'+cid]
    objectives=[]
    for o in seed['objectives']:
        objectives.append({'type':o['type'],'mapId':aliases[cid],'monsterId':o.get('targetId') if o['type']=='KILL_MONSTER' else o.get('monsterId'),'placementId':o.get('targetId') if o['type']=='DEFEAT_BOSS_PLACEMENT' else None,'count':o['count']})
    enabled=seed.get('bossTier')!='WORLD_BOSS' or enable_world_boss
    q={'id':qid,'name':seed['name'],'description':('Thảo phạt đúng vị trí boss; thưởng một lần.' if is_boss else 'Hoàn thành mục tiêu tại '+next(m['name'] for m in plan['maps'] if m['mapId']==cid)+'.'),'requiredLevel':level,'type':'STORY','target':'','count':objectives[0]['count'],'exp':max(120,level*120),'cultivation':level*35,'stones':level*15,'mapId':aliases[cid],'requiredRace':required_race,'prerequisiteQuestIds':prerequisites,'anyPrerequisiteQuestIds':any_prerequisites,'followUpQuestId':'PATH' if qid=='guide_trung_chau' else None,'enabled':enabled,'category':'BOSS' if is_boss else 'DUNGEON' if cid in ['huyen_moc_bi_canh','cuu_u_thach_phu'] else 'MAIN_STORY','objectives':objectives}
    progress['quests'].append(q);bindings['questBindings'][qid]={'questId':qid,'enabled':enabled,'npc':'guide','reviewed':True}
for row in plan['maps']:
    cid=row['mapId'];m=maps[aliases[cid]];notes=[b['notes'] for b in plan['storyBeats'] if b['mapId']==cid]
    dialogue='world15-guide-'+cid
    bots['dialogues'].append({'id':dialogue,'lines':['Đạo hữu đã đến '+row['name']+'.',*(notes or ['Mở Sổ tay để xem nhiệm vụ và bản đồ tiếp theo.']),'Mở Nhiệm vụ để nhận hoặc lĩnh thưởng; Sổ tay chỉ đường cho tuyến tu luyện của ngươi.']})
    for npc in m['npcSpawns']:
        if npc['type']=='GUIDE':npc['dialogueId']=dialogue
bindings['archivedMapStatuses']={id:'PLANNED_DISABLED' for id in plan['archivedPlannedMapIds']}
bindings['verifiedMaps']=list(aliases)
save('bots.json',bots);save('maps.json',list(maps.values()));save('combat.json',combat);save('progression.json',progress);save('world15-bindings.json',bindings)
dungeons=[]
for cid,did,name,minimum,entry,entry_x,cooldown in [('huyen_moc_bi_canh','HUYEN_MOC','Huyền Mộc Bí Cảnh',10,'vo_chu_hoang_nguyen',3300,0),('cuu_u_thach_phu','CUU_U','Cửu U Thạch Phủ',18,'thien_mon_di_tich',3450,900000)]:
    m=maps[aliases[cid]];groups=m['monsterSpawnGroups']
    dungeons.append({'id':did,'name':name,'mapId':m['id'],'minLevel':minimum,'cooldownMs':cooldown,'entries':[{'mapId':aliases[entry],'x':entry_x,'radius':120}]+([{'mapId':'trung-chau','x':350,'radius':150}] if did=='HUYEN_MOC' else []),'waves':[[groups[0]['id']],[groups[1]['id']],[groups[2]['id']]],'rewardExp':500 if did=='HUYEN_MOC' else 1800,'rewardCultivation':150 if did=='HUYEN_MOC' else 600,'rewardStones':100 if did=='HUYEN_MOC' else 250})
save('dungeons.json',dungeons)
print('Generated reviewed geometry/data for 15 maps; arenas retained internally; scheduled world boss enabled: '+str(enable_world_boss))
