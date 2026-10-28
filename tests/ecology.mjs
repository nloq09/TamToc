import assert from 'node:assert/strict';
import {Client,api,until,lifetimeExp} from './helpers/clients.mjs';
const clients=[];
try{
 const a=await Client.create('ecologyA'),b=await Client.create('ecologyB');clients.push(a,b);
 const metadata=await api('/world/monsters',undefined,a.account.token);assert.ok(metadata.some(m=>m.id==='WOLF_ALPHA'&&m.tier==='ELITE'));assert.ok(metadata.some(m=>m.id==='BOSS'&&m.tier==='WORLD_BOSS'));assert.ok(metadata.every(m=>!('dropTable'in m)));
 await Promise.all(clients.map(c=>c.move(800)));const target=a.snapshot.monsters.filter(m=>m.type==='WOLF'&&m.hp>0).sort((m,n)=>Math.abs(m.x-a.self().x)-Math.abs(n.x-a.self().x))[0];
 await until(()=>b.snapshot.monsters.some(m=>m.id===target.id));assert.ok(target.spawnGroupId);assert.equal(target.definitionId,'WOLF');
 const before=clients.map(c=>({exp:lifetimeExp(c.self()),cult:c.self().cultivation}));
 await Promise.all(clients.map(c=>c.move(target.x-25)));for(const c of clients){c.input(Math.sign(c.snapshot.monsters.find(m=>m.id===target.id).x-c.self().x)||1);c.input(0);}a.send('CAST_SKILL',{skillId:'QI'});b.send('ATTACK');await new Promise(r=>setTimeout(r,650));a.input(Math.sign(a.snapshot.monsters.find(m=>m.id===target.id).x-a.self().x)||1);a.input(0);a.send('ATTACK');
 await until(()=>a.snapshot.monsters.find(m=>m.id===target.id)?.hp===0);
 await Promise.all(clients.map(c=>c.wait('MONSTER_DEFEATED',p=>p.entityId===target.id)));
 await until(()=>clients.reduce((sum,c,index)=>sum+lifetimeExp(c.self())-before[index].exp,0)===45);
 assert.equal(clients.reduce((sum,c,index)=>sum+c.self().cultivation-before[index].cult,0),15);
 for(const c of clients)assert.equal(c.events.filter(e=>e.type==='MONSTER_DEFEATED'&&e.payload.entityId===target.id).length,0); // each event was consumed exactly once
 const exp=clients.map(c=>lifetimeExp(c.self()));a.send('ATTACK');b.send('ATTACK');await new Promise(r=>setTimeout(r,700));assert.deepEqual(clients.map(c=>lifetimeExp(c.self())),exp);
 for(const c of clients){const drop=c.snapshot.loot.find(l=>l.itemId==='WOLF_FANG');assert.ok(drop);assert.equal(drop.quantity,1);await c.move(drop.x);c.send('INTERACT');await until(()=>!c.snapshot.loot.some(l=>l.id===drop.id));const inventory=await api('/characters/'+c.id+'/inventory',undefined,c.account.token);assert.equal(inventory.items.WOLF_FANG,1);}
 await until(()=>a.snapshot.monsters.find(m=>m.id===target.id)?.hp===80,9000);assert.equal(a.snapshot.monsters.filter(m=>m.id===target.id).length,1);
 await Promise.all(clients.map(c=>c.move(350)));await until(()=>{const m=a.snapshot.monsters.find(m=>m.id===target.id);return m.hp===80&&m.targetId===null&&['IDLE','PATROL'].includes(m.state);},10000);
 console.log('PASS data-defined species/tiers/groups, shared stable entity IDs, one MonsterDefeated per death, bounded EXP/cultivation, personal loot quantity/pickup, same-slot respawn and aggro reset');
}catch(error){console.error(clients.map(c=>({name:c.character.name,self:c.self(),monsters:c.snapshot?.monsters.slice(0,3)})));throw error;}finally{clients.forEach(c=>c.close());}
