import {test} from 'node:test';
import assert from 'node:assert/strict';
import {GameConnection} from '../frontend/src/network.ts';

const pause=ms=>new Promise(r=>setTimeout(r,ms));
class Socket {
 static OPEN=1;static instances=[];
 readyState=0;sent=[];
 constructor(){Socket.instances.push(this);queueMicrotask(()=>{if(this.readyState===0){this.readyState=1;this.onopen?.();}});}
 send(text){this.sent.push(JSON.parse(text));}
 close(code=1000){this.readyState=3;queueMicrotask(()=>this.onclose?.({code}));}
 packet(type,payload,version=1,epoch='epoch-a'){this.onmessage?.({data:JSON.stringify({type,payload,stateVersion:version,worldEpoch:epoch})});}
}
function setup(){
 Socket.instances=[];globalThis.WebSocket=Socket;
 const events=[],statuses=[],values=new Map();const storage={getItem:k=>values.get(k)||null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
 const options={characterId:'test',url:'ws://test',ticket:async()=> 'one-time-ticket',storage,baseDelayMs:1,maxDelayMs:1,maxAttempts:2};
 const make=()=>new GameConnection((type,payload)=>events.push({type,payload}),s=>statuses.push(s),options);
 return {events,statuses,values,make,options};
}
function authenticate(s){s.packet('AUTH_RESULT',{resumeToken:'private-proof',worldEpoch:'epoch-a'},1);}
test('waits for a full snapshot and rejects stale versions or foreign world epochs',async()=>{
 const {make,events}=setup();const connection=make();connection.connect();await pause(0);
 try{
  const s=Socket.instances[0];authenticate(s);s.packet('ENTITY_UPDATE',{old:true},999);
  assert.equal(connection.send('ATTACK'),false);
  s.packet('WORLD_SNAPSHOT',{selfId:'test'},2);s.packet('ENTITY_UPDATE',{value:1},3);s.packet('ENTITY_UPDATE',{value:'stale'},2);s.packet('ENTITY_UPDATE',{value:'foreign'},4,'epoch-b');
  assert.deepEqual(events.map(e=>e.type),['AUTH_RESULT','WORLD_SNAPSHOT','ENTITY_UPDATE']);assert.equal(events.at(-1).payload.value,1);
 }finally{connection.close();}
});
test('reconnect never replays combat commands and ignores late frames from an obsolete socket',async()=>{
 const {make,events}=setup();const connection=make();connection.connect();await pause(0);
 try{
  const first=Socket.instances[0];authenticate(first);first.packet('WORLD_SNAPSHOT',{},2);assert.equal(connection.send('ATTACK'),true);
  connection.simulateDisconnect(0);assert.equal(connection.send('ATTACK'),false);await pause(20);
  const second=Socket.instances[1];assert.ok(second);first.packet('ENTITY_UPDATE',{obsolete:true},100);
  authenticate(second);second.packet('WORLD_SNAPSHOT',{reconnected:true},2);
  assert.equal(second.sent[0].payload.resumeToken,'private-proof');assert.ok(!second.sent.some(m=>m.type==='ATTACK'));assert.ok(!events.some(e=>e.payload?.obsolete));
 }finally{connection.close();}
});
test('duplicate-controller auth rejection stops retries',async()=>{
 const {make,statuses}=setup();const connection=make();connection.connect();await pause(0);
 try{Socket.instances[0].packet('ERROR',{code:'CHARACTER_ALREADY_CONNECTED',message:'Already controlled'},1);await pause(15);assert.equal(Socket.instances.length,1);assert.equal(statuses.at(-1).state,'failed');}
 finally{connection.close();}
});
test('suspend preserves the proof for refresh while intentional leave removes it',async()=>{
 const {make,values}=setup();const first=make();first.connect();await pause(0);authenticate(Socket.instances[0]);first.suspend();
 assert.equal(values.get('tamtoc-resume:test'),'private-proof');const next=make();next.connect();await pause(0);
 try{assert.equal(Socket.instances[1].sent[0].payload.resumeToken,'private-proof');authenticate(Socket.instances[1]);}
 finally{next.close();}assert.equal(values.has('tamtoc-resume:test'),false);
});
test('leaving during a pending ticket request aborts it and never opens a socket',async()=>{
 const {options}=setup();let aborted=false;
 options.ticket=signal=>new Promise((_resolve,reject)=>signal.addEventListener('abort',()=>{aborted=true;reject(Error('Aborted'));}));
 const connection=new GameConnection(()=>{},()=>{},options);connection.connect();connection.close();await pause(0);
 assert.ok(aborted);assert.equal(Socket.instances.length,0);
});
