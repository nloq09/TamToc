import assert from 'node:assert/strict';
import {Client,api,base,until} from './helpers/clients.mjs';
assert.ok(process.env.DEV_ADMIN_KEY,'Run this test on a local-profile backend with DEV_ADMIN_KEY');
const clients=[];const timers=[];
async function level10(client){const response=await fetch(base+'/api/dev/characters/'+client.id+'/level10',{method:'POST',headers:{Authorization:'Bearer '+client.account.token,'X-Dev-Key':process.env.DEV_ADMIN_KEY}});assert.equal(response.status,200,await response.text());await until(()=>client.self().level===10);}
function fight(client){
 let attackAt=0,qiAt=0,powerAt=0,potionAt=0,potionPending=false;
 const timer=setInterval(()=>{
  const self=client.self();if(!self||self.hp<=0||!client.snapshot.instanceId)return;
  const target=client.snapshot.monsters.filter(m=>m.hp>0).sort((a,b)=>Math.abs(a.x-self.x)-Math.abs(b.x-self.x))[0];
  if(!target){client.input(0);return;}
  const diff=target.x-self.x;client.input(Math.abs(diff)>35?Math.sign(diff):Math.abs(diff)>5&&self.facing!==Math.sign(diff)?Math.sign(diff):0);
  const now=Date.now();if(Math.abs(diff)<85&&now>=attackAt){attackAt=now+550;client.send('ATTACK');}if(Math.abs(diff)<260&&now>=qiAt){qiAt=now+1900;client.send('CAST_SKILL',{skillId:'QI'});}
  if(now>=powerAt){powerAt=now+11000;client.send('CAST_SKILL',{skillId:client.character.race+'_POWER'});}
  if(self.hp<self.maxHp-55&&now>potionAt&&!potionPending){potionPending=true;potionAt=now+1800;fetch(base+'/api/characters/'+client.id+'/inventory/use',{method:'POST',headers:{Authorization:'Bearer '+client.account.token,'Content-Type':'application/json'},body:JSON.stringify({itemId:'POTION'})}).finally(()=>potionPending=false);}
 },100);timers.push(timer);return timer;
}
try{
 for(let i=0;i<4;i++){const client=await Client.create('dungeon'+i,i%2?'DEMON':'HUMAN');clients.push(client);await level10(client);}
 const [a,b,c,d]=clients;const outsider=await Client.create('dungeonGuest');clients.push(outsider);
 for(const [leader,member]of [[a,b],[c,d]]){const invite=await leader.invite(member);await member.ack(member.command('PARTY_ACCEPT',{invitationId:invite.id}));}
 await Promise.all(clients.map(async client=>{await client.commonMap();await client.move(350);}));
 a.command('DUNGEON_START_REQUEST',{dungeonId:'HUYEN_MOC'});assert.equal((await a.wait('DUNGEON_ERROR')).payload.code,'DUNGEON_NOT_READY');
 const lateInvite=await a.invite(outsider);const runs=[];
 for(const [leader,member]of [[a,b],[c,d]]){
  leader.send('DUNGEON_READY_SET',{ready:true});member.send('DUNGEON_READY_SET',{ready:true});await leader.wait('DUNGEON_LIST',p=>p.ready.includes(leader.id)&&p.ready.includes(member.id));
  const request=leader.command('DUNGEON_START_REQUEST',{dungeonId:'HUYEN_MOC'});await leader.ack(request);const run=(await leader.wait('DUNGEON_INSTANCE_CREATED')).payload;runs.push(run);
  leader.command('DUNGEON_START_REQUEST',{dungeonId:'HUYEN_MOC'},request);assert.equal((await leader.ack(request)).replayed,true);
  await until(()=>leader.snapshot.instanceId===run.instanceId&&member.snapshot.instanceId===run.instanceId&&leader.snapshot.monsters.length===3);
 }
 assert.notEqual(runs[0].instanceId,runs[1].instanceId);assert.ok(a.snapshot.monsters.every(m=>!c.snapshot.monsters.some(n=>n.id===m.id)));
 outsider.command('PARTY_ACCEPT',{invitationId:lateInvite.id});assert.equal((await outsider.wait('PARTY_ERROR')).payload.code,'PARTY_BUSY');assert.equal(outsider.snapshot.instanceId,null);
 assert.ok(!a.snapshot.players.some(p=>[c.id,d.id].includes(p.id)));assert.ok(!c.snapshot.players.some(p=>[a.id,b.id].includes(p.id)));
 a.send('RESUME_WORLD',{instanceId:runs[1].instanceId});assert.equal((await a.wait('ERROR')).payload.code,'INVALID_RESUME_REQUEST');
 a.send('CAST_SKILL',{skillId:'QI',targetId:c.snapshot.monsters[0].id,instanceId:runs[1].instanceId});await new Promise(r=>setTimeout(r,200));assert.ok(c.snapshot.monsters.every(m=>m.hp===m.maxHp));
 const monsterIds=a.snapshot.monsters.map(m=>m.id).sort();a.close(false);await new Promise(r=>setTimeout(r,5000));await a.open();assert.equal(a.auth.resumed,true);assert.equal(a.snapshot.instanceId,runs[0].instanceId);assert.deepEqual(a.snapshot.monsters.map(m=>m.id).sort(),monsterIds);
 console.log('PASS two isolated instances, READY gate, idempotent start, private entity registries, forged scope blocked, 5-second reconnect into the same instance');
 [a,b,c,d].forEach(fight);
 await Promise.all([[a,b],[c,d]].map(async([leader,member],index)=>{
  const finished=(await leader.wait('DUNGEON_FINISHED',p=>p.run.state==='COMPLETED',150000)).payload;
  assert.equal(finished.run.wave,3);assert.equal(finished.run.reason,'VICTORY');
  for(const client of [leader,member]){
   const history=await api('/characters/'+client.id+'/activities',undefined,client.account.token);const entry=history.dungeons.find(r=>r.id===runs[index].runId);assert.equal(entry.status,'COMPLETED');assert.equal(Number(entry.reward_count),entry.eligible?1:0);
   const before=await api('/characters/'+client.id+'/inventory',undefined,client.account.token);client.send('DUNGEON_LIST_REQUEST');await new Promise(r=>setTimeout(r,250));const after=await api('/characters/'+client.id+'/inventory',undefined,client.account.token);assert.equal(after.stones,before.stones);
   client.send('DUNGEON_LEAVE_REQUEST');await client.wait('DUNGEON_LEFT');await until(()=>client.snapshot.instanceId===null&&client.snapshot.mapId==='trung-chau');
  }
  console.log('PASS party '+(index+1)+': three real waves, shared boss combat, persisted result, unique reward grants, read/retry cannot grant again, safe return');
 }));
}finally{timers.forEach(clearInterval);clients.forEach(c=>c.close());}
