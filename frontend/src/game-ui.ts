import {initializeDuels,duelBlocked} from './duel-ui';
import {type Character,type Player,type Skill} from './types';
import type {GameConnection} from './network';
import {openProgression,setPanelMap} from './progression-ui';
import {api} from './api';
import {initializeSocial} from './social-ui';
import {initializeActivities} from './activity-ui';
let current:Character;
let connection:GameConnection;
let selfId='';
let organizationLevel=10;
async function updateTrackedQuest(){
 const id=selfId;
 try{
  const quests=await api<any[]>('/characters/'+id+'/quests');if(id!==selfId||!document.querySelector('.quest-tracker'))return;
  const active=quests.find(q=>q.status==='ACTIVE');
  document.querySelector('.quest-tracker h3')!.textContent=active?active.definition.name:'Bước đầu tu luyện';
  document.querySelector('.quest-tracker p')!.textContent=active?active.definition.description:'Đến gặp Tiếp Dẫn Sứ, mở Nhiệm vụ để bắt đầu hành trình.';
  document.querySelector('.quest-tracker .objective')!.textContent=active?`${active.progress>=active.definition.count?'✓':'◇'} ${active.progress} / ${active.definition.count} · ${active.progress>=active.definition.count?'Có thể lĩnh thưởng':'Đang thực hiện'}`:'◇ Nhận nhiệm vụ tại Tiếp Dẫn Sứ';
 }catch{/* The main connection state already reports unavailable servers. */}
}
export function enhanceGame(c:Character,transport:GameConnection){
 current=c;connection=transport;selfId=c.id;
 initializeSocial(c,transport,()=>void showPanel('party'));
 initializeActivities(c,transport,()=>void showPanel('dungeon'));
 initializeDuels(c,transport,()=>void showPanel('duel'));
 void api<{requiredLevel:number}>('/characters/'+c.id+'/available-organizations').then(data=>{organizationLevel=data.requiredLevel;}).catch(error);
 document.querySelector('.controls-panel')!.innerHTML='<span class="eyebrow">KỸ NĂNG & TU LUYỆN</span><div id="skill-bar"></div><div class="progress-track"><span id="exp-label"></span><span id="cultivation-label"></span></div><div class="game-actions"><button id="quests-button">Nhiệm vụ [Q]</button><button id="inventory-button">Túi đồ [I]</button><button id="cultivation-button">Tu luyện</button><button id="organization-button">Thế lực</button></div>';
 const objective=document.querySelector('.panel h3');if(objective)objective.textContent='Tu luyện nhập môn';
 const description=document.querySelector('.panel p');if(description)description.innerHTML='Đến gần quái màu cam. Nhấn <kbd>J</kbd> đánh thường, <kbd>K</kbd> Linh Khí Kích. Khu vực bên trái là nơi hồi phục an toàn.';
 const objectiveLine=document.querySelector('.objective');if(objectiveLine)objectiveLine.textContent='◇ Đánh quái để nhận EXP và tu vi';
 for(const kind of ['quests','inventory','cultivation','organizations'])document.querySelector('#'+(kind==='organizations'?'organization':kind)+'-button')!.addEventListener('click',()=>void showPanel(kind));
 document.querySelectorAll<HTMLButtonElement>('[data-panel]').forEach(b=>b.onclick=()=>void showPanel(b.dataset.panel!));
 document.querySelector('#menu-button')!.addEventListener('click',()=>void showPanel('functions'));
 document.querySelector('#drawer-map')!.addEventListener('click',()=>void showPanel('map'));
 document.querySelector('#interact-button')!.addEventListener('click',()=>connection.send('INTERACT'));
 document.querySelector('#toggle-chat')!.addEventListener('click',()=>{const collapsed=document.querySelector('.chat-panel')!.classList.toggle('collapsed');const b=document.querySelector('#toggle-chat')!;b.textContent=collapsed?'+':'−';b.setAttribute('aria-expanded',String(!collapsed));});
 void updateTrackedQuest();
 void api<{stones:number}>('/characters/'+c.id+'/inventory').then(data=>{const wallet=document.querySelector('#wallet-stones');if(wallet&&selfId===c.id)wallet.textContent=String(data.stones);}).catch(()=>{});
}
function error(e:unknown){const el=document.querySelector<HTMLElement>('#error');if(el){el.hidden=false;el.textContent=(e as Error).message;}}
export function combatMessage(type:string,p:any){
 if(type==='SKILLS_UPDATED'){
  const skills=p as Skill[];const bar=document.querySelector('#skill-bar');if(!bar)return;
  bar.innerHTML=skills.map(s=>`<button data-skill="${s.id}" aria-label="${s.name}" title="${s.description} · MP ${s.mpCost}"><kbd>${s.id==='ATTACK'?'J':s.id==='QI'?'K':s.id==='DASH'?'⇧':s.id.endsWith('_POWER')?'L':'U'}</kbd><span class="skill-symbol" aria-hidden="true">${s.id==='ATTACK'?'⚔':s.id==='QI'?'✧':s.id==='DASH'?'彡':s.id.endsWith('_POWER')?'◉':'✥'}</span><span>${s.name}</span><small></small></button>`).join('');
  bar.querySelectorAll<HTMLButtonElement>('[data-skill]').forEach(b=>b.onclick=()=>connection.send('CAST_SKILL',{skillId:b.dataset.skill}));
 }
 if(type==='WORLD_SNAPSHOT'||type==='ENTITY_UPDATE'){
  if(type==='WORLD_SNAPSHOT'){current.mapId=p.map.id;setPanelMap(p.map);}
  const self=(p.players as Player[]).find(v=>v.id===selfId);if(!self)return;
  Object.assign(current,{level:self.level,exp:self.exp,cultivation:self.cultivation,realm:self.realm,realmStage:self.realmStage,organization:self.organization,cultivationPath:self.path});
  const hud=document.querySelector('.player-hud');if(hud){hud.querySelector('strong small')!.textContent='Lv.'+self.level;for(const [type,value,max]of[['hp',self.hp,self.maxHp],['mp',self.mp,self.maxMp]] as const){hud.querySelector<HTMLElement>(`.${type}>span`)!.style.width=value/max*100+'%';hud.querySelector(`.${type} small`)!.textContent=`${type.toUpperCase()} ${value} / ${max}`;}}
  const subtitle=document.querySelector('.player-hud>div>span');if(subtitle)subtitle.textContent=`${self.realm==='TRUC_CO'?'Trúc Cơ':'Luyện Khí'} ${['Sơ Kỳ','Trung Kỳ','Hậu Kỳ','Viên Mãn'][self.realmStage]} · ${self.path||'Nhập môn'}`;
  document.querySelector('#drawer-level')!.textContent=`Cấp ${self.level} · ${self.path||'Nhập môn'}`;
  document.querySelector('#drawer-vitals')!.textContent=`HP ${self.hp}/${self.maxHp} · MP ${self.mp}/${self.maxMp}`;
  document.querySelector('#drawer-realm')!.textContent=subtitle?.textContent||'';
  document.querySelector<HTMLButtonElement>('#interact-button')!.disabled=p.mapId==='duel-arena';
  const target=((p.mapId==='duel-arena'?p.players.filter((v:Player)=>v.id!==selfId):p.monsters) as {name:string;level:number;hp:number;maxHp:number;x:number;y:number}[]).filter(m=>m.hp>0&&(p.mapId==='duel-arena'||Math.abs(m.x-self.x)<350&&Math.abs(m.y-self.y)<120)).sort((a,b)=>Math.abs(a.x-self.x)-Math.abs(b.x-self.x))[0];
  document.querySelector<HTMLElement>('#target-hud')!.hidden=!target;
  document.querySelector('#target-hud>span')!.textContent=p.mapId==='duel-arena'?'ĐỐI THỦ THÁCH ĐẤU':'YÊU VẬT GẦN NHẤT';
  if(target){document.querySelector('#target-name')!.textContent=`${target.name} · Cấp ${target.level}`;document.querySelector('#target-hp')!.textContent=`${target.hp} / ${target.maxHp}`;document.querySelector<HTMLElement>('#target-fill')!.style.width=target.hp/target.maxHp*100+'%';}
  document.querySelector('#exp-label')!.textContent=`Cấp ${self.level} · EXP ${self.exp}/${self.level*100}`;
  document.querySelector('#cultivation-label')!.textContent=`Tu vi ${self.cultivation} · ${self.realm==='TRUC_CO'?'Trúc Cơ':'Luyện Khí'} ${['Sơ Kỳ','Trung Kỳ','Hậu Kỳ','Viên Mãn'][self.realmStage]}`;
  const orgButton=document.querySelector<HTMLButtonElement>('#organization-button')!;orgButton.disabled=self.level<organizationLevel;orgButton.title=orgButton.disabled?`Mở từ cấp ${organizationLevel}`:'Xem thế lực';
  for(const b of document.querySelectorAll<HTMLButtonElement>('[data-skill]')){const remaining=(self.cooldowns[b.dataset.skill!]||0)-p.serverTime;b.disabled=remaining>0||self.hp<=0||!!duelBlocked();b.querySelector('small')!.textContent=remaining>0?(remaining/1000).toFixed(1)+'s':'';}
 }
 if(type==='PLAYER_DIED'&&p.id===selfId)error(new Error('Đã tử trận. Tự hồi sinh tại khu an toàn sau 4 giây.'));
 if(type==='QUEST_UPDATED')void updateTrackedQuest();
 if(type==='NPC_DIALOG')void showPanel('quests');
 if(type==='ORGANIZATION_UNLOCKED')error(new Error('Đã mở khóa thế lực! Gặp Tiếp Dẫn Sứ và nhận Con Đường Tu Luyện.'));
}
export async function showPanel(kind:string){try{await openProgression(kind,current);}catch(e){error(e);}}
window.addEventListener('keydown',e=>{if(!document.querySelector('#world')||e.target instanceof HTMLInputElement||document.querySelector('dialog[open]'))return;if(e.code==='KeyI')void showPanel('inventory');if(e.code==='KeyQ')void showPanel('quests');});
