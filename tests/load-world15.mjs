import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {Client,api,until,lifetimeExp,base} from './helpers/clients.mjs';
const exec=promisify(execFile),clients=[],timers=[],samples=[];
assert.equal(base,'http://127.0.0.1:8080','Measure the Docker backend on 8080');
const duration=Number(process.env.LOAD_DURATION_MS||300000);
assert.ok(duration>=300000,'Acceptance requires at least five minutes');
const stamp=Date.now().toString(36),races=['HUMAN','DEMON','BEAST'];
let measuring=false,cleaning=false,receivedBytes=0,receivedMessages=0,unexpectedDisconnects=0,deaths=0;
const counts={},errors={},lastCast=new Map(),targets=new Map(),previousHp=new Map();
async function resources(){const {stdout}=await exec('docker',['compose','ps','-q','backend'],{timeout:10000});const result=await exec('docker',['stats','--no-stream','--format','{{json .}}',stdout.trim()],{timeout:15000});return JSON.parse(result.stdout.trim());}
function cast(c,id,cooldown,cost=0){const p=c.self(),now=Date.now(),key=c.id+':'+id;if(p.hp<=0||p.mp<cost||(p.cooldowns[id]||0)>now-80||(lastCast.get(key)||0)>now)return;lastCast.set(key,now+cooldown+150);c.send(id==='ATTACK'?'ATTACK':'CAST_SKILL',id==='ATTACK'?{}:{skillId:id});}
try{
 const initial=await fetch('http://127.0.0.1:8080/api/health');assert.equal(initial.status,200);
 for(let group=0;group<4;group++){
  const account=await api('/auth/register',{username:'worldLoad'+group+'_'+stamp,password:'Load_Test_123'},undefined,201);
  for(let n=0;n<5;n++){
   const i=group*5+n,race=races[i%3],character=await api('/characters',{name:'World'+i+'_'+stamp,race},account.token,201),c=new Client(account,character,3);await c.open();clients.push(c);
   const original=c.ws.onmessage;c.ws.onmessage=e=>{const event=JSON.parse(e.data);if(measuring){receivedMessages++;receivedBytes+=Buffer.byteLength(e.data);counts[event.type]=(counts[event.type]||0)+1;if(event.type.endsWith('ERROR'))errors[event.payload.code]=(errors[event.payload.code]||0)+1;}original(e);};c.ws.addEventListener('close',()=>{if(!cleaning)unexpectedDisconnects++;});
  }
 }
 // Six parties (4+3, 4+3, 4+2) share the three active racial fields.
 let parties=0;
 for(const race of races){const cohort=clients.filter(c=>c.character.race===race);for(let i=0;i<cohort.length;i+=4){parties++;for(const member of cohort.slice(i+1,i+4)){const invite=await cohort[i].invite(member);await member.ack(member.command('PARTY_ACCEPT',{invitationId:invite.id}));}}}
 await Promise.all(clients.map(async c=>{const [portal,map]=c.character.race==='HUMAN'?['city-to-ngoai-son','thanh-van-ngoai-son']:c.character.race==='DEMON'?['city-to-huyet-vu','huyet-vu-coc']:['city-to-linh-thu','linh-thu-son-lo'];c.send('USE_PORTAL',{portalId:portal});await c.wait('WORLD_SNAPSHOT',p=>p.mapId===map);}));
 const before=await api('/network/metrics',undefined,clients[0].account.token),expBefore=clients.map(c=>lifetimeExp(c.self())),start=Date.now();
 assert.equal(before.monsterSlots,145);assert.equal(before.npcEntities,29);assert.ok(before.deltaClients>=20);measuring=true;
 timers.push(setInterval(()=>{for(const [i,c]of clients.entries()){const p=c.self();if(!p)continue;if(p.hp===0&&(previousHp.get(c.id)||0)>0)deaths++;previousHp.set(c.id,p.hp);if(p.hp<=0){c.input(0);continue;}const rest=(Date.now()-start)%45000>35000;const target=c.snapshot.monsters.filter(m=>m.hp>0&&m.x>470&&m.x<1380&&m.tier==='NORMAL').sort((a,b)=>Math.abs(a.x-p.x)-Math.abs(b.x-p.x))[0];targets.set(c.id,rest?null:target);const x=rest?320:target?target.x-35:650+(i%4)*100,dx=x-p.x;c.input(Math.abs(dx)>12?Math.sign(dx):target&&Math.abs(target.x-p.x)>4&&p.facing!==Math.sign(target.x-p.x)?Math.sign(target.x-p.x):0);}},50));
 timers.push(setInterval(()=>{for(const c of clients){const p=c.self(),target=targets.get(c.id);const protectedArea=c.map?.safeZones?.some(z=>z.protectedArea&&p&&p.x>=z.bounds.x-40&&p.x<=z.bounds.x+z.bounds.width+40&&p.y>=z.bounds.y&&p.y<=z.bounds.y+z.bounds.height);if(!p||p.hp<=0)continue;if(c.character.race==='HUMAN'&&(p.hp<p.maxHp-20||p.mp<p.maxMp-20))cast(c,'HUMAN_POWER',10000);if(!target||protectedArea)continue;const distance=Math.abs(target.x-p.x);if(distance<85)cast(c,'ATTACK',500);if(distance<255)cast(c,'QI',1800,8);if(distance<255&&p.mp>=24&&c.character.race!=='HUMAN')cast(c,c.character.race+'_POWER',10000,10);}},250));
 timers.push(setInterval(()=>clients.forEach(c=>c.send('CHAT',{message:'World15 '+Math.floor((Date.now()-start)/1000)})),10000));
 console.log('START 20 real v3 clients; 3 combat fields; 6 parties; 145 monster slots/29 NPCs; 20 Hz input, skills, chat; five minutes');
 while(Date.now()-start<duration){const metrics=await api('/network/metrics?sinceTick='+before.ticks,undefined,clients[0].account.token),resource=await resources();samples.push({elapsedMs:Date.now()-start,metrics,resource});assert.ok(clients.every(c=>c.ws.readyState===1),'Load client disconnected');assert.ok(metrics.deltaClients>=20);console.log(JSON.stringify({seconds:Math.round((Date.now()-start)/1000),online:metrics.onlinePlayers,npcs:metrics.npcEntities,alive:metrics.aliveMonsters,cpu:resource.CPUPerc,ram:resource.MemUsage,meanTickMs:metrics.meanTickMs,p95TickMs:metrics.p95TickMs,activeNpcs:metrics.activeNpcEntities,simulatedMonsters:metrics.simulatedMonsterSlots,maxTickMs:metrics.maxTickMs,slowTicks:metrics.slowTicks,errors,unexpectedDisconnects}));await new Promise(r=>setTimeout(r,Math.min(30000,Math.max(0,duration-(Date.now()-start)))));}
 timers.forEach(clearInterval);measuring=false;const after=await api('/network/metrics?sinceTick='+before.ticks,undefined,clients[0].account.token),elapsedMs=Date.now()-start,expGained=clients.map((c,i)=>lifetimeExp(c.self())-expBefore[i]);
 // Persist the measured interval before cleanup so a cleanup failure cannot erase evidence.
 const result={startedAt:new Date(start).toISOString(),elapsedMs,clients:20,parties,scenario:'3 racial combat fields, 145 open-world monster slots, 29 registered NPCs; only populated maps simulate AI, 20 Hz input, shared PvE/loot/party rewards, race skills, periodic safe-camp recovery, PING and map chat; no private dungeon/duel',receivedMessages,receivedBytes,counts,errors,unexpectedDisconnects,deaths,expGained,before,after,samples};await mkdir('.runtime',{recursive:true});await writeFile('.runtime/world15-load.json',JSON.stringify(result,null,2));
 // Other human players may join during the run. Acknowledge each owned party leave,
 // then check closed test sockets and cache cleanup rather than demanding zero users.
 for(const c of clients){await c.ack(c.command('PARTY_LEAVE'));await until(()=>c.party?.inParty===false);}
 // Let the server acknowledge LEAVE_WORLD by closing the transport. Sending a
 // second close frame immediately can leave Node's native WebSocket CLOSING.
 cleaning=true;clients.forEach(c=>{clearInterval(c.timer);c.send('LEAVE_WORLD');});
 const peakOnline=Math.max(after.onlinePlayers,...samples.map(s=>s.metrics.onlinePlayers));
 let lastCleanup='';
 try{await until(async()=>{const m=await api('/network/metrics',undefined,clients[0].account.token);result.cleanupPending={socketStates:clients.map(c=>c.ws.readyState),metrics:m};const detail=JSON.stringify({closed:clients.filter(c=>c.ws.readyState===3).length,online:m.onlinePlayers,parties:m.activeParties,interest:m.interestConnections});if(detail!==lastCleanup){lastCleanup=detail;console.log('CLEANUP '+detail);}return clients.every(c=>c.ws.readyState===3)&&m.onlinePlayers<=peakOnline-20&&m.activeParties<=after.activeParties-parties&&m.interestConnections<=m.onlinePlayers&&m.deltaClients<=m.onlinePlayers;},45000);}catch(error){await writeFile('.runtime/world15-load.json',JSON.stringify(result,null,2));throw error;}
 const cleanup=await api('/network/metrics',undefined,clients[0].account.token);result.cleanup=cleanup;delete result.cleanupPending;await writeFile('.runtime/world15-load.json',JSON.stringify(result,null,2));
 assert.equal(unexpectedDisconnects,0);assert.deepEqual(errors,{});assert.equal(after.exceptions,before.exceptions);assert.ok(after.tickSamples>=6000&&after.p95TickMs>0);assert.ok(Math.abs(after.tickSamples-(after.ticks-before.ticks))<=2);assert.ok(expGained.every(n=>n>0),'Every simulated player must participate in real kills');assert.ok(counts.ENTITY_DELTA>1000&&counts.COMBAT_EVENT>0&&counts.MONSTER_DEFEATED>0);assert.equal(cleanup.trainingNpcEntities,0);
 console.log('PASS '+elapsedMs+' ms, 20 simultaneous clients, actual combat/XP, zero client errors/disconnects, party/AOI cleanup; .runtime/world15-load.json');
}finally{cleaning=true;measuring=false;timers.forEach(clearInterval);clients.forEach(c=>c.close());}
