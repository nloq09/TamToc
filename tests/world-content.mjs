import assert from 'node:assert/strict';
import {Client,api,base,until} from './helpers/clients.mjs';
assert.ok(process.env.DEV_ADMIN_KEY,'Use QA local profile and DEV_ADMIN_KEY');
let c,timer;const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function quest(){return (await api('/characters/'+c.id+'/quests',undefined,c.account.token)).find(q=>q.definition.id==='OUTER_WOLVES');}
function fight(){let attackAt=0,qiAt=0,healAt=0;timer=setInterval(()=>{const p=c.self(),target=c.snapshot.monsters.filter(m=>m.type==='WOLF'&&m.hp>0).sort((a,b)=>Math.abs(a.x-p.x)-Math.abs(b.x-p.x))[0];if(!target||p.hp<=0)return;const dx=target.x-p.x,now=Date.now();c.input(Math.abs(dx)>30?Math.sign(dx):Math.abs(dx)>4&&p.facing!==Math.sign(dx)?Math.sign(dx):0);if(Math.abs(dx)<85&&now>attackAt){attackAt=now+600;c.send('ATTACK');}if(Math.abs(dx)<255&&p.mp>=8&&now>qiAt){qiAt=now+2000;c.send('CAST_SKILL',{skillId:'QI'});}if(p.hp<p.maxHp-20&&now>healAt){healAt=now+10500;c.send('CAST_SKILL',{skillId:'HUMAN_POWER'});}},100);}
async function portal(id,map){c.send('USE_PORTAL',{portalId:id});await c.wait('WORLD_SNAPSHOT',p=>p.mapId===map);}
try{
 c=await Client.create('regionQuest','HUMAN',3);await c.move(300);await api('/characters/'+c.id+'/quests/OUTER_WOLVES/accept',{},c.account.token);assert.equal((await quest()).progress,0);
 await c.move(760);fight();await c.wait('MONSTER_DEFEATED',p=>p.monsterDefinitionId==='WOLF',15000);clearInterval(timer);c.input(0);assert.equal((await quest()).progress,0);
 const upgraded=await fetch(base+'/api/dev/characters/'+c.id+'/level10',{method:'POST',headers:{Authorization:'Bearer '+c.account.token,'X-Dev-Key':process.env.DEV_ADMIN_KEY}});assert.equal(upgraded.status,200);await until(()=>c.self().level===10);
 await c.move(190);await portal('city-to-ngoai-son','thanh-van-ngoai-son');await c.move(760);fight();await until(async()=>(await quest()).progress===4,40000);clearInterval(timer);c.input(0);
 const inventory=await api('/characters/'+c.id+'/inventory',undefined,c.account.token),definition=(await quest()).definition;await api('/characters/'+c.id+'/quests/OUTER_WOLVES/claim',{},c.account.token);await api('/characters/'+c.id+'/quests/OUTER_WOLVES/claim',{},c.account.token,400);assert.equal((await api('/characters/'+c.id+'/inventory',undefined,c.account.token)).stones,inventory.stones+definition.stones);
 console.log('PASS map-scoped quest ignores real town kills, counts four field kills, server grants reward exactly once');
 await c.move(100);await portal('ngoai-son-to-city','thanh-van');await sleep(1100);await c.commonMap();await c.move(3080);await portal('center-to-ruins','thien-mon-di-tich');
 const ruins=await api('/world/maps/thien-mon-di-tich',undefined,c.account.token);assert.equal(ruins.recommendedLevelMin,10);assert.ok(ruins.monsterSpawnGroups.some(g=>g.definitionId==='RUIN_SENTINEL'));assert.ok(ruins.monsterSpawnGroups.some(g=>g.definitionId==='BOSS'));await sleep(1100);await c.move(100);await portal('ruins-to-border','tam-gioi-bien-canh');assert.ok(c.self().x>3200);assert.equal(c.snapshot.instanceId,null);
 c.close();await sleep(300);await c.open();assert.equal((await quest()).status,'CLAIMED');assert.equal(c.snapshot.mapId,'tam-gioi-bien-canh');console.log('PASS level-10 ruins portal and border return spawn, designer elite/boss data, regional quest/map persistence after a fresh login');
}finally{clearInterval(timer);c?.close();}
