import {InterestState} from '../../frontend/src/network.ts';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
export const base=process.env.TEST_API||'http://127.0.0.1:8080';
export async function api(path,body,token,status=200){const response=await fetch(base+'/api'+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});assert.equal(response.status,status,`${path}: ${response.status} ${await response.clone().text()}`);return response.json().catch(()=>null);}
export async function until(predicate,ms=10000){const end=Date.now()+ms;while(Date.now()<end){if(await predicate())return;await new Promise(r=>setTimeout(r,30));}throw Error('Timed out waiting for client condition');}
export class Client {
 events=[];snapshot;party;ws;timer;sequence=0;resumeToken;auth;
 constructor(account,character,protocolVersion=2){this.protocolVersion=protocolVersion;this.interest=new InterestState();this.account=account;this.character=character;this.id=character.id;}
 static async create(prefix,race='HUMAN',protocolVersion=2){
  const suffix=Date.now().toString(36)+Math.random().toString(36).slice(2,5);const name=(prefix+'_'+suffix).slice(0,24);
  const account=await api('/auth/register',{username:name,password:'Integration_Test_123'},undefined,201);
  const character=await api('/characters',{name,race},account.token,201);const client=new Client(account,character,protocolVersion);await client.open();return client;
 }
 async open(){
  this.events=this.events.filter(e=>!['AUTH_RESULT','WORLD_SNAPSHOT'].includes(e.type));this.auth=null;
  const {ticket}=await api('/characters/'+this.id+'/connection-ticket',{},this.account.token);
  this.interest=new InterestState();
  this.ws=new WebSocket(base.replace(/^http/,'ws')+'/ws');this.snapshot=null;
  const socket=this.ws;
  this.ws.onmessage=e=>{if(this.ws!==socket)return;const event=JSON.parse(e.data);this.events.push(event);if(this.events.length>2000)this.events.splice(0,500);if(event.type==='WORLD_SNAPSHOT'){this.map=event.payload.map;this.snapshot=event.payload;this.interest.reset(event.payload);}if(event.type==='ENTITY_UPDATE')this.snapshot=event.payload;if(event.type==='ENTITY_DELTA'){const state=this.interest.apply(event.payload);if(state)this.snapshot=state;else this.send('RESUME_WORLD');}if(event.type==='AUTH_RESULT'){this.auth=event.payload;this.resumeToken=event.payload.resumeToken;}if(event.type==='PARTY_UPDATED')this.party=event.payload;if(event.type==='PARTY_DISBANDED')this.party={inParty:false};};
  await new Promise((resolve,reject)=>{this.ws.onopen=resolve;this.ws.onerror=reject;});
  this.send('AUTH',{ticket,protocolVersion:this.protocolVersion,...(this.resumeToken?{resumeToken:this.resumeToken}:{})});this.timer=setInterval(()=>this.send('PING'),2000);await this.wait('WORLD_SNAPSHOT');
 }
 send(type,payload={},requestId){if(this.ws?.readyState===1)this.ws.send(JSON.stringify({type,payload,...(requestId?{requestId}:{})}));}
 command(type,payload={},requestId=randomUUID()){this.send(type,payload,requestId);return requestId;}
 async ack(id){return (await this.wait('COMMAND_RESULT',p=>p.requestId===id)).payload;}
 async wait(type,predicate=()=>true,ms=10000){let result;await until(()=>{const index=this.events.findIndex(e=>e.type===type&&predicate(e.payload));if(index<0)return false;result=this.events.splice(index,1)[0];return true;},ms);return result;}
 self(){return this.snapshot?.players.find(p=>p.id===this.id);}
 input(direction,jump=false){this.send('INPUT',{direction,jump,sequence:++this.sequence});}
 async move(x){let jumpAt=0;const timer=setInterval(()=>{const p=this.self(),diff=x-p.x,now=Date.now(),wall=this.map?.obstacles?.some(r=>r.height<=110&&r.x>Math.min(p.x,x)&&r.x<Math.max(p.x,x)&&Math.abs(p.x-(diff>0?r.x:r.x+r.width))<110);const jump=wall&&now>jumpAt;if(jump)jumpAt=now+1100;this.input(Math.abs(diff)<10?0:Math.sign(diff),!!jump);},50);try{await until(()=>Math.abs(this.self().x-x)<15,20000);}finally{clearInterval(timer);this.input(0);}}
 async commonMap(){if(this.snapshot.mapId==='trung-chau')return;await this.move(2160);this.send('INTERACT');await this.wait('WORLD_SNAPSHOT',p=>p.map.id==='trung-chau');}
 close(intentional=true){clearInterval(this.timer);if(intentional)this.send('LEAVE_WORLD');this.ws?.close(intentional?1000:4000);}
 async invite(other){const request=this.command('PARTY_INVITE',{targetCharacterId:other.id});await this.ack(request);return (await other.wait('PARTY_INVITATION_RECEIVED',p=>p.inviterId===this.id)).payload;}
}
export function lifetimeExp(player){return player.level*(player.level-1)*50+player.exp;}
