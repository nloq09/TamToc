import assert from 'node:assert/strict';
const base='http://127.0.0.1:8080', stamp=Date.now().toString(36);
async function api(path,body,token){const r=await fetch(base+'/api'+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});assert.ok(r.ok,await r.clone().text());return r.json();}
class Peer{
 events=[];snapshot;seq=0;
 constructor(ticket){this.ws=new WebSocket(base.replace('http','ws')+'/ws');this.ws.onopen=()=>this.send('AUTH',{ticket});this.ws.onmessage=e=>{const m=JSON.parse(e.data);this.events.push(m);if(m.type==='WORLD_SNAPSHOT'||m.type==='ENTITY_UPDATE')this.snapshot=m.payload;};}
 send(type,payload={}){if(this.ws.readyState===1)this.ws.send(JSON.stringify({type,payload}));}
 input(direction){this.send('INPUT',{direction,jump:false,sequence:++this.seq});}
 async until(predicate,ms=15000){const end=Date.now()+ms;while(Date.now()<end){if(predicate())return;await new Promise(r=>setTimeout(r,40));}throw Error('Combat timeout');}
}
const peers=[];let timer;
try{
 const accounts=[];
 for(let i=0;i<2;i++){
  const a=await api('/auth/register',{username:'combat'+i+'_'+stamp,password:'Combat_Test_123'});
  const c=await api('/characters',{name:'Fighter'+i+'_'+stamp,race:'HUMAN'},a.token);
  const t=await api('/characters/'+c.id+'/connection-ticket',{},a.token);
  const peer=new Peer(t.ticket);peer.id=c.id;peers.push(peer);accounts.push(a);await peer.until(()=>peer.snapshot);
 }
 timer=setInterval(()=>peers.forEach(p=>p.input(1)),50);
 await peers[0].until(()=>peers.every(p=>p.snapshot.players.find(x=>x.id===p.id).x>=720));
 clearInterval(timer);peers.forEach(p=>p.input(0));
 const target=peers[0].snapshot.monsters.find(m=>m.type==='WOLF');
 peers[0].send('CAST_SKILL',{skillId:'KIEM_TU'});await peers[0].until(()=>peers[0].events.some(e=>e.type==='ERROR'&&e.payload.message.includes('chưa mở khóa')));
 peers.forEach(p=>p.send('CAST_SKILL',{skillId:'QI'}));peers[0].send('CAST_SKILL',{skillId:'QI'});
 await peers[0].until(()=>peers[0].events.some(e=>e.type==='ERROR'&&e.payload.message.includes('hồi chiêu')));
 timer=setInterval(()=>peers.forEach(p=>{p.send('ATTACK');}),550);
 await peers[0].until(()=>peers[0].snapshot.monsters.find(m=>m.id===target.id).hp===0);
 await peers[1].until(()=>peers[1].snapshot.monsters.find(m=>m.id===target.id).hp===0);
 clearInterval(timer);
 assert.ok(peers.every(p=>p.snapshot.players.find(x=>x.id===p.id).exp>0));
 console.log('PASS shared monster HP, server damage, contribution EXP, cooldown, locked skills');
 await peers[0].until(()=>peers[0].events.some(e=>e.type==='MONSTER_SPAWN'&&e.payload.id===target.id),9000);
 console.log('PASS server monster respawn');
}finally{clearInterval(timer);peers.forEach(p=>p.ws.close());}
