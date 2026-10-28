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

test('AOI deltas merge entities and remove despawned players, NPCs, monsters and loot',async()=>{
 const {make,events}=setup(),connection=make();connection.connect();await pause(0);
 try{const s=Socket.instances[0];authenticate(s);s.packet('WORLD_SNAPSHOT',{mapId:'field',instanceId:null,interestVersion:1,players:[{id:'self',x:1}],monsters:[{id:'wolf'}],bots:[{id:'guard',x:2}],loot:[{id:'fang'}]},2);
  s.packet('ENTITY_DELTA',{mapId:'field',instanceId:null,baseInterestVersion:1,interestVersion:2,players:{upsert:[{id:'self',x:3}],remove:[]},monsters:{upsert:[{id:'spirit'}],remove:['wolf']},bots:{upsert:[{id:'guard',x:5}],remove:[]},loot:{upsert:[],remove:['fang']}},3);
  const state=events.at(-1);assert.equal(state.type,'ENTITY_UPDATE');assert.deepEqual(state.payload.players,[{id:'self',x:3}]);assert.deepEqual(state.payload.monsters,[{id:'spirit'}]);assert.deepEqual(state.payload.bots,[{id:'guard',x:5}]);assert.deepEqual(state.payload.loot,[]);
 }finally{connection.close();}
});

test('missing AOI base requests exactly one full snapshot and gates gameplay until it arrives',async()=>{
 const {make,events}=setup(),connection=make();connection.connect();await pause(0);
 try{const s=Socket.instances[0];authenticate(s);s.packet('WORLD_SNAPSHOT',{mapId:'field',interestVersion:1,players:[],monsters:[],bots:[],loot:[]},2);const invalid={mapId:'field',baseInterestVersion:9,interestVersion:10};s.packet('ENTITY_DELTA',invalid,3);s.packet('ENTITY_DELTA',invalid,4);assert.equal(s.sent.filter(p=>p.type==='RESUME_WORLD').length,1);assert.equal(connection.send('ATTACK'),false);assert.equal(events.at(-1).type,'WORLD_SNAPSHOT');s.packet('WORLD_SNAPSHOT',{mapId:'field',interestVersion:12,players:[],monsters:[],bots:[],loot:[]},5);assert.equal(connection.send('ATTACK'),true);
 }finally{connection.close();}
});
