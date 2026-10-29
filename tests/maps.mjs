import assert from 'node:assert/strict';
import {Client,api,until} from './helpers/clients.mjs';
const clients=[],timers=[];
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function portal(c,id,map){c.send('USE_PORTAL',{portalId:id});const snapshot=(await c.wait('WORLD_SNAPSHOT',p=>p.map.id===map)).payload;await until(()=>c.snapshot.mapId===map);return snapshot;}
try{
 for(const [name,race]of [['mapA','HUMAN'],['mapB','HUMAN'],['mapD','DEMON'],['mapY','BEAST']])clients.push(await Client.create(name,race));
 const [a,b,d,y]=clients;assert.equal(a.snapshot.mapId,'thanh-van');assert.equal(d.snapshot.mapId,'u-minh');assert.equal(y.snapshot.mapId,'van-yeu');
 const maps=await api('/world/maps',undefined,a.account.token);assert.ok(maps.some(m=>m.id==='thanh-van-ngoai-son'&&m.mapType==='FIELD'));
 const field=await api('/world/maps/thanh-van-ngoai-son',undefined,a.account.token);assert.equal(field.obstacles[0].x,1450);assert.equal(field.gravity,1500);assert.ok(field.safeZones.some(z=>z.spawnId==='camp'));
 const healing=new Map();timers.push(setInterval(()=>{for(const c of [a,b]){const p=c.self();if(p&&p.hp<p.maxHp-20&&Date.now()-(healing.get(c.id)||0)>10050){healing.set(c.id,Date.now());c.send('CAST_SKILL',{skillId:'HUMAN_POWER'});}}},250));
 await a.move(500);a.send('USE_PORTAL',{portalId:'city-to-ngoai-son',x:190,mapId:'thanh-van-ngoai-son'});assert.equal((await a.wait('PORTAL_REJECTED')).payload.code,'PORTAL_TOO_FAR');assert.equal(a.snapshot.mapId,'thanh-van');await a.move(190);
 const entered=await Promise.all([portal(a,'city-to-ngoai-son','thanh-van-ngoai-son'),portal(b,'city-to-ngoai-son','thanh-van-ngoai-son')]);
 await until(()=>a.snapshot.players.some(p=>p.id===b.id)&&b.snapshot.players.some(p=>p.id===a.id));
 assert.deepEqual(a.snapshot.monsters.map(m=>m.id).sort(),b.snapshot.monsters.map(m=>m.id).sort());assert.equal(entered[0].map.obstacles[0].x,field.obstacles[0].x);
 b.send('USE_PORTAL',{portalId:'city-to-ngoai-son'});assert.ok(['PORTAL_COOLDOWN','PORTAL_NOT_FOUND'].includes((await b.wait('PORTAL_REJECTED')).payload.code));assert.equal(b.snapshot.players.filter(p=>p.id===b.id).length,1);
 await Promise.all([d,y].map(async c=>{await c.commonMap();await c.move(350);}));assert.ok(!d.snapshot.players.some(p=>p.id===a.id));
 a.send('CHAT',{message:'field-only-map-test'});await b.wait('CHAT',p=>p.message==='field-only-map-test');await sleep(200);assert.ok(!d.events.some(e=>e.type==='CHAT'&&e.payload.message==='field-only-map-test'));
 console.log('PASS three race spawn maps, metadata/collision from one source, out-of-range portal rejected, two-player field visibility and map-scoped chat');
 await b.move(1400);b.input(1);await sleep(850);b.input(0);assert.ok(b.self().x<=1434.1);b.send('CAST_SKILL',{skillId:'DASH'});await sleep(180);assert.ok(b.self().x<=1434.1);
 const jump=setInterval(()=>b.input(1,true),50);try{await until(()=>b.self().x>1525,4000);}finally{clearInterval(jump);b.input(0,false);}
 await b.move(1970);await b.wait('CHECKPOINT_REACHED',p=>p.spawnId==='camp');
 await b.move(3030);b.send('USE_PORTAL',{portalId:'ngoai-son-to-center'});assert.equal((await b.wait('PORTAL_REJECTED')).payload.code,'PORTAL_LEVEL_REQUIRED');
 const lastCheckpoint=field.safeZones.filter(z=>z.spawnId&&b.self().x>=z.bounds.x&&b.self().x<=z.bounds.x+z.bounds.width).at(-1)?.spawnId||'camp';
 const ids=b.snapshot.monsters.map(m=>m.id).sort();b.close(false);await sleep(5000);await b.open();assert.equal(b.auth.resumed,true);assert.equal(b.snapshot.mapId,'thanh-van-ngoai-son');assert.deepEqual(b.snapshot.monsters.map(m=>m.id).sort(),ids);assert.equal(b.snapshot.players.filter(p=>p.id===b.id).length,1);
 b.close();await sleep(350);await b.open();assert.equal(b.auth.resumed,false);assert.equal(b.snapshot.mapId,'thanh-van-ngoai-son');assert.ok(Math.abs(b.self().x-field.spawnPoints.find(s=>s.id===lastCheckpoint).x)<20);
 console.log('PASS walking/dash stop at solid wall, server jump clears it, persisted checkpoint, level gate, five-second field resume and safe fallback at checkpoint');
}catch(e){console.error(clients.map(c=>({name:c.character.name,map:c.snapshot?.mapId,self:c.self(),errors:c.events.filter(e=>e.type.endsWith('ERROR')).slice(-3)})));throw e;}finally{timers.forEach(clearInterval);clients.forEach(c=>c.close());}
