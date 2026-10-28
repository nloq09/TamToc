import assert from 'node:assert/strict';
import {Client,api,until} from './helpers/clients.mjs';
const clients=[],timers=[];const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function portal(c,id,map){c.events=c.events.filter(e=>e.type!=='WORLD_SNAPSHOT');c.send('USE_PORTAL',{portalId:id});return (await c.wait('WORLD_SNAPSHOT',p=>p.mapId===map)).payload;}
try{
 for(const [name,race,version]of [['aoiA','HUMAN',3],['aoiB','HUMAN',3],['aoiD','DEMON',3],['aoiY','BEAST',2]])clients.push(await Client.create(name,race,version));
 const [a,b,d,y]=clients;const metrics=await api('/network/metrics',undefined,a.account.token);
 assert.equal(a.auth.protocolVersion,3);assert.equal(y.auth.protocolVersion,2);assert.ok(metrics.deltaClients>=3);assert.equal(metrics.monsterSlots,64);assert.equal(metrics.npcEntities,21);
 await Promise.all([portal(d,'city-to-huyet-vu','huyet-vu-coc'),portal(y,'city-to-linh-thu','linh-thu-son-lo')]);
 await until(()=>a.snapshot.players.some(p=>p.id===b.id));assert.ok(a.snapshot.interestVersion>0);assert.ok(!a.snapshot.players.some(p=>[d.id,y.id].includes(p.id)));
 const initialBot=b.snapshot.bots.find(p=>p.botType==='CHALLENGER');const initialVersion=b.snapshot.interestVersion;const healing=new Map();
 timers.push(setInterval(()=>{for(const c of[a,b]){const p=c.self();if(p.hp<p.maxHp-20&&Date.now()-(healing.get(c.id)||0)>10100){healing.set(c.id,Date.now());c.send('CAST_SKILL',{skillId:'HUMAN_POWER'});}}},200));
 b.events=[];await b.move(1320);await until(()=>!a.snapshot.players.some(p=>p.id===b.id)&&!b.snapshot.players.some(p=>p.id===a.id));
 assert.ok(!b.snapshot.bots.some(p=>p.id===initialBot.id));assert.ok(b.events.some(e=>e.type==='ENTITY_DELTA'&&e.payload.bots.remove.includes(initialBot.id)));assert.ok(a.events.some(e=>e.type==='ENTITY_DELTA'&&e.payload.players.remove.includes(b.id)));
 assert.ok(b.snapshot.interestVersion>initialVersion);assert.equal(b.snapshot.players.filter(p=>p.id===b.id).length,1);
 b.send('CHAT',{message:'aoi-map-wide-chat'});await a.wait('CHAT',p=>p.message==='aoi-map-wide-chat');await sleep(150);assert.ok(!d.events.some(e=>e.type==='CHAT'&&e.payload.message==='aoi-map-wide-chat'));
 const old=b.snapshot.interestVersion;b.send('RESUME_WORLD');const full=(await b.wait('WORLD_SNAPSHOT')).payload;assert.ok(full.interestVersion>old);await until(()=>b.snapshot.interestVersion>full.interestVersion);
 b.events=[];await b.move(180);await until(()=>a.snapshot.players.some(p=>p.id===b.id)&&b.snapshot.bots.some(p=>p.id===initialBot.id));assert.equal(a.snapshot.players.filter(p=>p.id===b.id).length,1);assert.ok(b.events.some(e=>e.type==='ENTITY_DELTA'&&e.payload.bots.upsert.some(p=>p.id===initialBot.id)));
 console.log('PASS four real clients, three race fields, protocol 2 compatibility, v3 delta bases, player/NPC AOI removal/reentry, no duplicates, full resync and scoped map chat');
 b.close(false);await sleep(2000);await b.open();assert.equal(b.auth.resumed,true);assert.equal(b.snapshot.mapId,'thanh-van');await until(()=>b.snapshot.interestVersion>=2);
 const practice=a.snapshot.bots.find(p=>p.botType==='CHALLENGER');a.command('START_TRAINING',{botEntityId:practice.id,difficulty:'EASY'});const run=(await a.wait('TRAINING_STARTED')).payload;await until(()=>a.snapshot.instanceId===run.instanceId);assert.equal(a.snapshot.players.length,1);assert.equal(a.snapshot.bots.length,1);assert.ok(!b.snapshot.bots.some(p=>p.id===run.botId));await a.ack(a.command('CANCEL_TRAINING'));await until(()=>a.snapshot.instanceId===null&&a.snapshot.players.some(p=>p.id===b.id));
 await y.wait('ENTITY_UPDATE');assert.ok(!y.events.some(e=>e.type==='ENTITY_DELTA'));assert.ok(!a.events.some(e=>e.type==='ENTITY_UPDATE'));
 const current=await api('/network/metrics',undefined,a.account.token);assert.equal(current.interestConnections,metrics.interestConnections);assert.equal(current.trainingNpcEntities,0);
 await Promise.all([portal(a,'city-to-ngoai-son','thanh-van-ngoai-son'),portal(b,'city-to-ngoai-son','thanh-van-ngoai-son')]);await until(()=>a.snapshot.players.some(p=>p.id===b.id));
 console.log('PASS v3 reconnect replaces AOI baseline without leaking cache, isolated training transfer/return, legacy full updates and runtime NPC cleanup');
}catch(e){console.error(clients.map(c=>({map:c.snapshot?.mapId,self:c.self(),errors:c.events.filter(e=>e.type.endsWith('ERROR')).slice(-3)})));throw e;}finally{timers.forEach(clearInterval);clients.forEach(c=>c.close());}
