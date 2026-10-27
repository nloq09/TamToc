import assert from 'node:assert/strict';
const base=process.env.TEST_API || 'http://127.0.0.1:8080';
const wsBase=base.replace(/^http/,'ws');
const origin='http://localhost:5173';
const stamp=Date.now().toString(36);
async function request(path,body,token,status=200){
 const r=await fetch(base+'/api'+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});
 assert.equal(r.status,status,`${path}: ${r.status} ${await r.clone().text()}`);return r.json().catch(()=>null);
}
class Peer {
 messages=[]; socket;
 constructor(){this.socket=new WebSocket(wsBase+'/ws');this.socket.onmessage=e=>this.messages.push(JSON.parse(e.data));}
 async open(ticket){await new Promise((resolve,reject)=>{this.socket.onopen=resolve;this.socket.onerror=reject;});this.send('AUTH',{ticket});return this.wait('WORLD_SNAPSHOT');}
 send(type,payload={}){this.socket.send(JSON.stringify({type,payload}));}
 async wait(type,predicate=()=>true,ms=5000){const end=Date.now()+ms;while(Date.now()<end){const i=this.messages.findIndex(m=>m.type===type&&predicate(m.payload));if(i>=0)return this.messages.splice(i,1)[0].payload;await new Promise(r=>setTimeout(r,20));}throw Error('Timeout: '+type);}
 close(){this.socket.close();}
}
const peers=[];
try {
 const a=await request('/auth/register',{username:'a_'+stamp,password:'Password_Test_123'},null,201);
 const b=await request('/auth/register',{username:'b_'+stamp,password:'Password_Test_123'},null,201);
 const login=await request('/auth/login',{username:'a_'+stamp,password:'Password_Test_123'});assert.ok(login.token);
 await request('/auth/login',{username:'a_'+stamp,password:'Wrong_Password_123'},null,401);
 const ca=await request('/characters',{name:'Human_'+stamp,race:'HUMAN'},a.token,201);
 const cb=await request('/characters',{name:'Demon_'+stamp,race:'DEMON'},b.token,201);
 assert.equal(ca.characterClass,'NOVICE');assert.equal(ca.organization,null);assert.equal(ca.cultivationPath,null);assert.equal(ca.level,1);
 assert.equal(ca.mapId,'thanh-van');assert.equal(cb.mapId,'u-minh');
 await request('/characters/'+ca.id,undefined,b.token,404);
 await request('/characters/'+ca.id+'/connection-ticket',{},b.token,404);
 await request('/characters',{name:'Cheat_'+stamp,race:'HUMAN',level:999},a.token,400);
 await request('/characters',{name:'Bad_'+stamp,race:'DRAGON'},a.token,400);
 const ta=await request('/characters/'+ca.id+'/connection-ticket',{},a.token);
 const tb=await request('/characters/'+cb.id+'/connection-ticket',{},b.token);
 const pa=new Peer();peers.push(pa);const sa=await pa.open(ta.ticket);
 const pb=new Peer();peers.push(pb);await pb.open(tb.ticket);
 assert.equal(sa.players.length,1);
 const replay=new Peer();peers.push(replay);await new Promise(r=>replay.socket.onopen=r);replay.send('AUTH',{ticket:ta.ticket});await replay.wait('ERROR');
 const duplicateTicket=await request('/characters/'+ca.id+'/connection-ticket',{},a.token);
 const duplicate=new Peer();peers.push(duplicate);await new Promise(r=>duplicate.socket.onopen=r);duplicate.send('AUTH',{ticket:duplicateTicket.ticket});await duplicate.wait('ERROR',p=>p.message.includes('kết nối khác'));
 console.log('PASS auth, ownership, creation defaults, unknown fields, ticket replay, duplicate session');
 // Race-specific starting maps connect to the common map through genuine movement and portals.
 let sequence=0;
 const timer=setInterval(()=>{pa.send('INPUT',{direction:1,jump:false,sequence:++sequence});pb.send('INPUT',{direction:1,jump:false,sequence});},50);
 await Promise.all([pa.wait('ENTITY_UPDATE',p=>p.players.some(v=>v.id===ca.id&&v.x>=2150),12000),pb.wait('ENTITY_UPDATE',p=>p.players.some(v=>v.id===cb.id&&v.x>=2150),12000)]);
 clearInterval(timer);pa.send('INPUT',{direction:0,jump:false,sequence:++sequence});pb.send('INPUT',{direction:0,jump:false,sequence});
 pa.send('INTERACT');pb.send('INTERACT');
 await pa.wait('WORLD_SNAPSHOT',p=>p.map.id==='trung-chau');await pb.wait('WORLD_SNAPSHOT',p=>p.map.id==='trung-chau');
 await pa.wait('ENTITY_UPDATE',p=>p.players.length===2);await pb.wait('ENTITY_UPDATE',p=>p.players.length===2);
 pa.messages=[];pb.messages=[];
 for(let i=0;i<8;i++){pa.send('INPUT',{direction:1,jump:false,sequence:++sequence});await new Promise(r=>setTimeout(r,50));}
 pa.send('INPUT',{direction:0,jump:false,sequence:++sequence});
 await pb.wait('ENTITY_UPDATE',p=>p.players.some(v=>v.id===ca.id&&v.x>700));
 pa.messages=[];
 for(let i=0;i<8;i++){pb.send('INPUT',{direction:-1,jump:false,sequence:++sequence});await new Promise(r=>setTimeout(r,50));}
 pb.send('INPUT',{direction:0,jump:false,sequence:++sequence});
 await pa.wait('ENTITY_UPDATE',p=>p.players.some(v=>v.id===cb.id&&v.x<610));
 pa.send('INPUT',{direction:0,jump:false,sequence:++sequence,x:99999});await pa.wait('ERROR');
 pa.send('CHAT',{message:'Xin chào Tam Tộc'});await pb.wait('CHAT',p=>p.message==='Xin chào Tam Tộc');
 pb.close();await pa.wait('PLAYER_LEFT',p=>p.id===cb.id);
 console.log('PASS portals, two-player visibility, movement both ways, coordinate tampering, shared chat, disconnect');
 pa.close();await new Promise(r=>setTimeout(r,700));
 const relogin=await request('/auth/login',{username:'a_'+stamp,password:'Password_Test_123'});
 const saved=await request('/characters/'+ca.id,undefined,relogin.token);assert.equal(saved.mapId,'trung-chau');
 const next=await request('/characters/'+ca.id+'/connection-ticket',{},relogin.token);const pc=new Peer();peers.push(pc);const restored=await pc.open(next.ticket);
 assert.ok(restored.players.find(v=>v.id===ca.id).x>700);
 console.log('PASS PostgreSQL persistence after disconnect and login');
}finally{for(const p of peers) p.close();}
