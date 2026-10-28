import assert from 'node:assert/strict';
import {Client,api,until} from './helpers/clients.mjs';
const clients=[],timers=[];
async function challenge(a,b){const request=a.command('DUEL_CHALLENGE',{targetCharacterId:b.id});await a.ack(request);const invite=(await b.wait('DUEL_CHALLENGE_RECEIVED',p=>p.challengerId===a.id)).payload;return {request,invite};}
async function accept(a,b){const {invite}=await challenge(a,b);const request=b.command('DUEL_ACCEPT',{duelInvitationId:invite.id});await b.ack(request);const run=(await a.wait('DUEL_PREPARE')).payload;await until(()=>a.snapshot.instanceId===run.instanceId&&b.snapshot.instanceId===run.instanceId);return {request,invite,run};}
try{
 for(let i=0;i<3;i++)clients.push(await Client.create('duel'+i));
 const [a,b,c]=clients;
 a.send('CAST_SKILL',{skillId:'QI',targetCharacterId:b.id});assert.equal((await a.wait('ERROR')).payload.code,'PVP_NOT_ALLOWED');
 const declined=await challenge(a,b);b.command('DUEL_DECLINE',{duelInvitationId:declined.invite.id});await a.wait('DUEL_DECLINED');assert.equal(a.snapshot.instanceId,null);assert.equal(b.snapshot.instanceId,null);
 const before=await Promise.all([a,b].map(async client=>({player:client.self(),inventory:await api('/characters/'+client.id+'/inventory',undefined,client.account.token)})));
 const first=await accept(a,b);
 b.command('DUEL_ACCEPT',{duelInvitationId:first.invite.id},first.request);assert.equal((await b.ack(first.request)).replayed,true);
 a.send('ATTACK');assert.equal((await a.wait('ERROR')).payload.code,'DUEL_NOT_ACTIVE');assert.ok(!c.snapshot.players.some(p=>p.id===a.id));assert.equal(a.snapshot.monsters.length,0);
 a.send('RESUME_WORLD',{instanceId:'00000000-0000-0000-0000-000000000000'});assert.equal((await a.wait('ERROR')).payload.code,'INVALID_RESUME_REQUEST');
 await a.wait('DUEL_STARTED');a.send('ATTACK',{targetCharacterId:c.id});assert.equal((await a.wait('ERROR')).payload.code,'INVALID_TARGET');
 await b.move(550);await a.move(500);const hp=b.self().hp;
 a.send('CAST_SKILL',{skillId:'QI'});await b.wait('COMBAT_EVENT',p=>p.source===a.id&&p.target===b.id);await until(()=>b.self().hp<hp);a.send('CAST_SKILL',{skillId:'QI'});assert.equal((await a.wait('ERROR')).payload.code,'SKILL_COOLDOWN');
 // One real fighter, one consenting defender: the server decides damage and knockout.
 const timer=setInterval(()=>{if(a.self()?.instanceId){a.input(0);a.send('ATTACK');}},600);timers.push(timer);
 const finish=(await a.wait('DUEL_FINISHED',p=>p.duelId===first.run.duelId,30000)).payload;clearInterval(timer);assert.equal(finish.reason,'KNOCKOUT');assert.equal(finish.winnerCharacterId,a.id);
 await until(()=>a.snapshot.instanceId===null&&b.snapshot.instanceId===null);
 for(const [index,client]of [a,b].entries()){
  assert.equal(client.self().exp,before[index].player.exp);assert.equal(client.self().cultivation,before[index].player.cultivation);
  assert.deepEqual(await api('/characters/'+client.id+'/inventory',undefined,client.account.token),before[index].inventory);
  const history=await api('/characters/'+client.id+'/activities',undefined,client.account.token);assert.equal(history.duels.filter(r=>r.id===first.run.duelId).length,1);
 }
 console.log('PASS consent/decline, countdown, duplicate accept, scope and explicit-target guards, shared PvP damage/cooldown, knockout, unchanged progression/items and one persisted result');
 const second=await accept(a,b);await a.wait('DUEL_STARTED');a.close(false);await new Promise(r=>setTimeout(r,5000));await a.open();assert.equal(a.auth.resumed,true);assert.equal(a.snapshot.instanceId,second.run.instanceId);a.close(false);
 const disconnected=(await b.wait('DUEL_FINISHED',p=>p.duelId===second.run.duelId,19000)).payload;assert.equal(disconnected.reason,'DISCONNECT');assert.equal(disconnected.winnerCharacterId,b.id);await until(()=>b.snapshot.instanceId===null);await a.open();assert.equal(a.snapshot.instanceId,null);await a.wait('DUEL_FINISHED',p=>p.duelId===second.run.duelId);
 console.log('PASS five-second arena resume, fifteen-second disconnect loss, safe return and result on reconnect');
 const third=await accept(a,b);await b.ack(b.command('DUEL_FORFEIT'));assert.equal((await a.wait('DUEL_FINISHED',p=>p.duelId===third.run.duelId)).payload.winnerCharacterId,a.id);
 console.log('PASS explicit forfeit during countdown and no orphan arena');
}catch(error){console.error(clients.map(c=>({name:c.character.name,map:c.snapshot?.mapId,self:c.self(),errors:c.events.filter(e=>e.type.endsWith('ERROR')).slice(-5)})));throw error;}finally{timers.forEach(clearInterval);clients.forEach(c=>c.close());}
