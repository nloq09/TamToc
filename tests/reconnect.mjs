import assert from 'node:assert/strict';
import {GameConnection} from '../frontend/src/network.ts';
const base=process.env.TEST_API||'http://127.0.0.1:8080',stamp=Date.now().toString(36);
async function api(path,body,token,status=200){const r=await fetch(base+'/api'+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});assert.equal(r.status,status,`${path}: ${r.status} ${await r.clone().text()}`);return r.json().catch(()=>null);}
async function until(predicate,ms=6000){const end=Date.now()+ms;while(Date.now()<end){if(await predicate())return;await new Promise(r=>setTimeout(r,35));}throw Error('Reconnect test timeout');}
class Peer{
 messages=[];latest;heartbeat;ws;
 async open(ticket,extra={}){this.ws=new WebSocket(base.replace(/^http/,'ws')+'/ws');this.ws.onmessage=e=>{const m=JSON.parse(e.data);this.messages.push(m);if(['WORLD_SNAPSHOT','ENTITY_UPDATE'].includes(m.type))this.latest=m.payload;};await new Promise((resolve,reject)=>{this.ws.onopen=resolve;this.ws.onerror=reject;});this.send('AUTH',{ticket,...extra});this.heartbeat=setInterval(()=>this.send('PING'),2000);}
 send(type,payload={}){if(this.ws?.readyState===1)this.ws.send(JSON.stringify({type,payload}));}
 async wait(type,predicate=()=>true,ms=6000){let found;await until(()=>{const i=this.messages.findIndex(m=>m.type===type&&predicate(m.payload));if(i<0)return false;found=this.messages.splice(i,1)[0];return true;},ms);return found;}
 close(intentional=true){clearInterval(this.heartbeat);if(intentional)this.send('LEAVE_WORLD');this.ws?.close(intentional?1000:4000);}
}
const peers=[];const transports=[];let movement;
try{
 const a=await api('/auth/register',{username:'resumea_'+stamp,password:'Reconnect_Test_123'},null,201);
 const b=await api('/auth/register',{username:'resumeb_'+stamp,password:'Reconnect_Test_123'},null,201);
 const ca=await api('/characters',{name:'ResumeA_'+stamp,race:'HUMAN'},a.token,201);
 const cb=await api('/characters',{name:'ResumeB_'+stamp,race:'HUMAN'},b.token,201);
 const ticket=async(c,token)=>(await api('/characters/'+c.id+'/connection-ticket',{},token)).ticket;
 const observer=new Peer();peers.push(observer);await observer.open(await ticket(cb,b.token));await observer.wait('WORLD_SNAPSHOT');
 const values=new Map(),storage={getItem:k=>values.get(k)||null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
 const events=[],statuses=[];let latest;
 const makeTransport=()=>new GameConnection((type,payload)=>{events.push({type,payload});if(['WORLD_SNAPSHOT','ENTITY_UPDATE'].includes(type))latest=payload;},status=>statuses.push(status),{characterId:ca.id,url:base.replace(/^http/,'ws')+'/ws',ticket:()=>ticket(ca,a.token),storage,baseDelayMs:100,maxDelayMs:300});
 const connection=makeTransport();transports.push(connection);connection.connect();await until(()=>latest?.selfId===ca.id);
 const originalMobs=latest.monsters.map(m=>m.id).sort();
 const duplicate=new Peer();peers.push(duplicate);await duplicate.open(await ticket(ca,a.token),{protocolVersion:2});assert.equal((await duplicate.wait('ERROR')).payload.code,'CHARACTER_ALREADY_CONNECTED');duplicate.close();
 let sequence=0;movement=setInterval(()=>connection.send('INPUT',{direction:1,jump:false,sequence:++sequence}),50);
 await until(()=>latest.players.find(p=>p.id===ca.id).x>=740,6000);clearInterval(movement);connection.send('INPUT',{direction:0,jump:false,sequence:++sequence});
 observer.messages=[];connection.simulateDisconnect(7000);
 const grace=await observer.wait('PLAYER_CONNECTION_STATE',p=>p.id===ca.id);assert.equal(grace.payload.status,'DISCONNECTED_GRACE');
 await observer.wait('ENTITY_UPDATE',p=>p.players.some(v=>v.id===ca.id&&v.connectionStatus==='DISCONNECTED_GRACE'&&v.hp<v.maxHp),7000);
 await until(()=>events.filter(e=>e.type==='WORLD_SNAPSHOT').length>=2,10000);
 assert.equal(events.filter(e=>e.type==='AUTH_RESULT').at(-1).payload.resumed,true);
 assert.deepEqual(latest.monsters.map(m=>m.id).sort(),originalMobs);assert.equal(latest.players.filter(p=>p.id===ca.id).length,1);
 assert.ok(statuses.some(s=>s.state==='reconnecting'));
 connection.send('CHAT',{message:'Đã nối lại'});await observer.wait('CHAT',p=>p.message==='Đã nối lại');
 const beforeX=latest.players.find(p=>p.id===ca.id).x;
 movement=setInterval(()=>connection.send('INPUT',{direction:-1,jump:false,sequence:++sequence}),50);
 await until(()=>latest.players.find(p=>p.id===ca.id).x<beforeX-20);clearInterval(movement);connection.send('INPUT',{direction:0,jump:false,sequence:++sequence});
 console.log('PASS real frontend transport: fresh tickets, 7-second reconnect/backoff, HP damage during grace, shared monster IDs, unique player, chat and input after resume');
 const takeover=new Peer();peers.push(takeover);await takeover.open(await ticket(ca,a.token),{protocolVersion:2,resumeToken:values.get('tamtoc-resume:'+ca.id)});
 assert.equal((await takeover.wait('AUTH_RESULT')).payload.resumed,true);await takeover.wait('WORLD_SNAPSHOT');
 await until(()=>statuses.at(-1).state==='failed');assert.ok(statuses.at(-1).message.includes('kết nối khác'));
 takeover.send('INPUT',{direction:0,jump:false,sequence:1});await observer.wait('ENTITY_UPDATE',p=>p.players.filter(v=>v.id===ca.id&&v.connectionStatus==='ONLINE').length===1);
 takeover.send('RESUME_WORLD',{instanceId:'forged'});assert.equal((await takeover.wait('ERROR')).payload.code,'INVALID_RESUME_REQUEST');
 console.log('PASS authorized half-open takeover, stale close cannot remove new controller, old client stops retrying, forged resume scope rejected');
 observer.messages=[];takeover.close(false);await observer.wait('PLAYER_CONNECTION_STATE',p=>p.id===ca.id);
 const departed=await observer.wait('PLAYER_LEFT',p=>p.id===ca.id,23000);assert.ok(departed);
 const priorSnapshots=events.filter(e=>e.type==='WORLD_SNAPSHOT').length;const fresh=makeTransport();transports.push(fresh);fresh.connect();
 await until(()=>events.filter(e=>e.type==='WORLD_SNAPSHOT').length>priorSnapshots);
 assert.equal(events.filter(e=>e.type==='AUTH_RESULT').at(-1).payload.resumed,false);assert.equal(latest.players.find(p=>p.id===ca.id).x,180);
 console.log('PASS grace expiration cleans registry exactly once; stale resume proof falls back to safe fresh world join');
 const metrics=await api('/network/metrics',undefined,a.token);assert.ok(metrics.resumes>=2);console.log('Network metrics:',JSON.stringify(metrics));
 fresh.close();await observer.wait('PLAYER_LEFT',p=>p.id===ca.id);
}finally{clearInterval(movement);transports.forEach(t=>t.close());peers.forEach(p=>p.close());}
