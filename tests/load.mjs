import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {Client,api,until} from './helpers/clients.mjs';
const exec=promisify(execFile),clients=[],timers=[];
const duration=Number(process.env.LOAD_DURATION_MS||300000),samples=[];
assert.ok(duration>=300000,'The Phase 6 acceptance run must last at least five minutes');
let errors=0,unexpectedDisconnects=0,receivedMessages=0,receivedBytes=0,cleaning=false;
const stamp=Date.now().toString(36);
async function resources(){const {stdout}=await exec('docker',['compose','ps','-q','backend'],{timeout:10000});const result=await exec('docker',['stats','--no-stream','--format','{{json .}}',stdout.trim()],{timeout:15000});return JSON.parse(result.stdout.trim());}
try{
 // Four accounts × five distinct characters avoid bypassing the registration limit.
 for(let group=0;group<4;group++){
  const account=await api('/auth/register',{username:'load'+group+'_'+stamp,password:'Load_Test_123'},undefined,201);
  for(let i=0;i<5;i++){
   const character=await api('/characters',{name:'Load'+group+i+'_'+stamp,race:'HUMAN'},account.token,201);const client=new Client(account,character);await client.open();clients.push(client);
   const original=client.ws.onmessage;client.ws.onmessage=e=>{receivedMessages++;receivedBytes+=Buffer.byteLength(e.data);const event=JSON.parse(e.data);if(event.type.endsWith('ERROR'))errors++;original(e);};client.ws.addEventListener('close',()=>{if(!cleaning)unexpectedDisconnects++;});
  }
 }
 for(let i=0;i<20;i+=4)for(let j=1;j<4;j++){const invite=await clients[i].invite(clients[i+j]);await clients[i+j].ack(clients[i+j].command('PARTY_ACCEPT',{invitationId:invite.id}));}
 await until(()=>clients.every(c=>c.snapshot.players.filter(p=>clients.some(x=>x.id===p.id)).length===20));
 const before=await api('/network/metrics',undefined,clients[0].account.token);const start=Date.now();
 timers.push(setInterval(()=>{for(let i=0;i<clients.length;i++){const c=clients[i],p=c.self();const target=Math.floor((Date.now()-start)/4000)%2?330:180;c.input(Math.abs(p.x-target)<10?0:Math.sign(target-p.x));}},50));
 timers.push(setInterval(()=>{for(const c of clients){c.send('CHAT',{message:'Load test '+Math.floor((Date.now()-start)/1000)});c.send('CAST_SKILL',{skillId:'QI'});}},10000));
 console.log('START 20 simultaneous players, five parties, 20 Hz movement, heartbeat, area chat and skills for '+duration+' ms');
 while(Date.now()-start<duration){
  const metrics=await api('/network/metrics',undefined,clients[0].account.token),resource=await resources();samples.push({elapsedMs:Date.now()-start,metrics,resource});
  assert.equal(metrics.onlinePlayers,before.onlinePlayers,'Online count changed during load');assert.ok(clients.every(c=>c.ws.readyState===1));
  console.log(JSON.stringify({seconds:Math.round((Date.now()-start)/1000),online:metrics.onlinePlayers,cpu:resource.CPUPerc,ram:resource.MemUsage,maxTickMs:metrics.maxTickMs,slowTicks:metrics.slowTicks,errors,unexpectedDisconnects}));
  await new Promise(r=>setTimeout(r,Math.min(30000,Math.max(0,duration-(Date.now()-start)))));
 }
 timers.forEach(clearInterval);const after=await api('/network/metrics',undefined,clients[0].account.token);const elapsedMs=Date.now()-start;
 cleaning=true;clients.forEach(c=>c.close());await until(async()=>{const m=await api('/network/metrics',undefined,clients[0].account.token);return m.onlinePlayers===before.onlinePlayers-20&&m.activeParties===before.activeParties-5;});
 const cleanup=await api('/network/metrics',undefined,clients[0].account.token);
 const result={startedAt:new Date(start).toISOString(),elapsedMs,clients:20,parties:5,scenario:'20 Hz safe-area movement, PING every 2s, area chat + QI every 10s; no active dungeon/duel during this load run',errors,unexpectedDisconnects,receivedMessages,receivedBytes,before,after,cleanup,samples};
 await mkdir('.runtime',{recursive:true});await writeFile('.runtime/phase6-load.json',JSON.stringify(result,null,2));
 assert.equal(errors,0);assert.equal(unexpectedDisconnects,0);assert.equal(after.exceptions,before.exceptions);assert.equal(after.activeInstances,before.activeInstances);assert.equal(cleanup.gracePlayers,0);
 console.log('PASS 20 clients for '+elapsedMs+' ms; zero client errors/disconnects; party/session cleanup; report .runtime/phase6-load.json');
}finally{cleaning=true;timers.forEach(clearInterval);clients.forEach(c=>c.close());}
