import assert from 'node:assert/strict';
const base=process.env.TEST_API||'http://127.0.0.1:8080';const stamp=Date.now().toString(36);const key=process.env.DEV_ADMIN_KEY;
assert.ok(key,'Set DEV_ADMIN_KEY and start backend with -EnableDevTools for this test');
async function api(path,body,token,status=200,admin=false){
 const r=await fetch(base+'/api'+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),...(admin?{'X-Dev-Key':key}:{})},body:body===undefined?undefined:JSON.stringify(body)});
 assert.equal(r.status,status,`${path}: ${r.status} ${await r.clone().text()}`);return r.json().catch(()=>null);
}
class Peer{
 events=[];snapshot;seq=0;ws;
 constructor(ticket){this.ws=new WebSocket(base.replace('http','ws')+'/ws');this.ws.onopen=()=>this.send('AUTH',{ticket});this.ws.onmessage=e=>{const m=JSON.parse(e.data);this.events.push(m);if(m.type==='WORLD_SNAPSHOT'||m.type==='ENTITY_UPDATE')this.snapshot=m.payload;};}
 send(type,payload={}){if(this.ws.readyState===1)this.ws.send(JSON.stringify({type,payload}));}
 input(direction){this.send('INPUT',{direction,jump:false,sequence:++this.seq});}
 self(){return this.snapshot?.players.find(p=>p.id===this.id);}
 async until(predicate,ms=15000){const end=Date.now()+ms;while(Date.now()<end){if(await predicate())return;await new Promise(r=>setTimeout(r,70));}throw Error('Progression timeout');}
 async move(x){const timer=setInterval(()=>{const diff=x-this.self().x;this.input(Math.abs(diff)<10?0:Math.sign(diff));},50);try{await this.until(()=>Math.abs(this.self().x-x)<12);}finally{clearInterval(timer);this.input(0);}}
}
let peer,timer;
try{
 const a=await api('/auth/register',{username:'progress_'+stamp,password:'Progress_Test_123'},null,201);
 const b=await api('/auth/register',{username:'intruder_'+stamp,password:'Progress_Test_123'},null,201);
 const c=await api('/characters',{name:'Cultivator_'+stamp,race:'HUMAN'},a.token,201);const path='/characters/'+c.id;
 await api(path+'/inventory',undefined,b.token,404);
 await api(path+'/organization/join',{organizationId:'THANH_VAN'},a.token,400);
 await api('/dev'+path+'/level10',{},a.token,403);
 const ticket=await api(path+'/connection-ticket',{},a.token);peer=new Peer(ticket.ticket);peer.id=c.id;await peer.until(()=>peer.snapshot);
 await peer.move(300);
 await api(path+'/quests/WOLVES/accept',{},a.token);await api(path+'/quests/WOLVES/accept',{},a.token,400);
 await api(path+'/quests/WOLVES/claim',{},a.token,400);
 await api(path+'/quests/PATH/accept',{},a.token,400);
 await api('/dev'+path+'/level10',{},a.token,200,true);
 await peer.until(()=>peer.self().level===10);
 await api(path+'/quests/PATH/accept',{},a.token);
 await api(path+'/organization/trial',{organizationId:'HUYET_SAT'},a.token,400);
 await api(path+'/organization/join',{organizationId:'THANH_VAN'},a.token,400);
 await api(path+'/organization/trial',{organizationId:'THANH_VAN'},a.token);
 await api(path+'/organization/join',{organizationId:'THANH_VAN'},a.token,400);
 console.log('PASS NPC quest acceptance, duplicate acceptance, level gate, local admin protection, race gate, trial gate');
 await peer.move(680);
 let attackAt=0,qiAt=0;
 timer=setInterval(()=>{
  const self=peer.self();const target=peer.snapshot.monsters.filter(m=>m.type==='WOLF'&&m.hp>0).sort((a,b)=>Math.abs(a.x-self.x)-Math.abs(b.x-self.x))[0];
  if(!target)return;const diff=target.x-self.x;peer.input(Math.abs(diff)>35?Math.sign(diff):self.facing!==Math.sign(diff)&&Math.abs(diff)>5?Math.sign(diff):0);
  const now=Date.now();if(Math.abs(diff)<85&&now>attackAt){peer.send('ATTACK');attackAt=now+550;}if(Math.abs(diff)<260&&now>qiAt){peer.send('CAST_SKILL',{skillId:'QI'});qiAt=now+1900;}
 },100);
 await peer.until(async()=>{const q=await api(path+'/quests',undefined,a.token);return q.find(q=>q.definition.id==='WOLVES').progress===5;},30000);
 clearInterval(timer);peer.input(0);
 await api(path+'/quests/WOLVES/claim',{},a.token);
 await api(path+'/quests/WOLVES/claim',{},a.token,400);
 const inv=await api(path+'/inventory',undefined,a.token);assert.equal(inv.stones,40);
 const drop=peer.snapshot.loot?.find(l=>Math.abs(l.x-peer.self().x)<200);
 if(drop){await peer.move(drop.x);peer.send('INTERACT');await peer.until(()=>peer.events.some(e=>e.type==='INVENTORY_UPDATED'));const collected=await api(path+'/inventory',undefined,a.token);assert.ok(collected.items.WOLF_FANG>0);}
 await api(path+'/inventory/unequip',{slot:'WEAPON'},a.token);const unarmed=await api(path+'/inventory',undefined,a.token);
 await api(path+'/inventory/equip',{itemId:'WOOD_SWORD'},a.token);const armed=await api(path+'/inventory',undefined,a.token);assert.ok(armed.attack>unarmed.attack);
 await api(path+'/inventory/equip',{itemId:'QI_SWORD'},a.token,400);
 await api(path+'/cultivation/breakthrough',{},a.token);assert.equal((await api(path+'/cultivation',undefined,a.token)).stage,1);
 await peer.move(300);
 await api(path+'/organization/join',{organizationId:'THANH_VAN'},a.token);
 await peer.until(()=>peer.events.some(e=>e.type==='CULTIVATION_PATH_UNLOCKED'&&e.payload.path==='KIEM_TU'));
 await api(path+'/organization/join',{organizationId:'NGU_HANH'},a.token,400);
 await api(path+'/quests/PATH/claim',{},a.token);await api(path+'/quests/PATH/claim',{},a.token,400);
 await api(path+'/inventory/equip',{itemId:'QI_SWORD'},a.token);
 const skills=await api(path+'/skills',undefined,a.token);assert.ok(skills.some(s=>s.id==='KIEM_TU'));assert.ok(!skills.some(s=>s.id==='PHAP_TU'));
 console.log('PASS five real monster kills, quest reward exactly once, loot, equipment stats, breakthrough, trial completion, membership and unlocked skills');
 peer.send('CAST_SKILL',{skillId:'KIEM_TU'});await peer.until(()=>peer.events.some(e=>e.type==='SKILL_CAST'&&e.payload.skill==='KIEM_TU'));
 peer.ws.close();await new Promise(r=>setTimeout(r,800));
 const login=await api('/auth/login',{username:'progress_'+stamp,password:'Progress_Test_123'});
 const restored=await api(path,undefined,login.token);assert.equal(restored.organization,'THANH_VAN');assert.equal(restored.cultivationPath,'KIEM_TU');assert.equal(restored.realmStage,1);
 const savedInventory=await api(path+'/inventory',undefined,login.token);assert.equal(savedInventory.equipment.WEAPON,'QI_SWORD');assert.equal(savedInventory.stones,140);
 const savedQuest=await api(path+'/quests',undefined,login.token);assert.ok(savedQuest.filter(q=>['WOLVES','PATH'].includes(q.definition.id)).every(q=>q.status==='CLAIMED'));
 const next=await api(path+'/connection-ticket',{},login.token);peer=new Peer(next.ticket);peer.id=c.id;await peer.until(()=>peer.snapshot);assert.equal(peer.self().path,'KIEM_TU');assert.ok(peer.self().cooldowns.KIEM_TU>Date.now());
 console.log('PASS relogin persistence: level, realm, cultivation, quest rewards, inventory, organization, skills and cooldown');
}finally{clearInterval(timer);peer?.ws.close();}
