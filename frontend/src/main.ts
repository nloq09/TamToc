import './style.css';
import './progression.css';
import './game-layout.css';
import {gameShell} from './game-shell';
import { api,hasToken,setToken } from './api';
import { races,type Race,type Character,type Player,type Snapshot } from './types';
import { GameConnection } from './network';
import { createGame } from './game';
import { enhanceGame,combatMessage,showPanel } from './game-ui';

const root=document.querySelector<HTMLDivElement>('#app')!;
let connection:GameConnection|null=null;
let activeGame:ReturnType<typeof createGame>|null=null;
let lastSnapshot:Snapshot|null=null;
let players:Player[]=[];
let busy=false;
const esc=(s:string)=>s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
const brand=`<a class="brand" href="/" aria-label="Tam Tộc"><span class="seal">三</span><span>TAM TỘC<small>NGHỊCH THIÊN</small></span></a>`;
const header=()=>`<header>${brand}<div class="header-right"><span class="phase">TAM GIỚI · KHAI THIÊN</span><span class="live-dot"></span><span id="service-status">Đang kiểm tra máy chủ</span></div></header>`;
function error(message:string) { const el=document.querySelector<HTMLElement>('#error'); if(el) {el.textContent=message;el.hidden=false;} }
async function health() { try { await api('/health'); const el=document.querySelector('#service-status');if(el)el.textContent='Máy chủ trực tuyến'; }catch{const el=document.querySelector('#service-status');if(el)el.textContent='Máy chủ chưa kết nối';} }
function shell(content:string) { root.innerHTML=header()+content+`<footer><span>TAM TỘC: NGHỊCH THIÊN</span><span>Thế giới chung · Tiến trình lưu trên máy chủ</span><span>Bản phát triển 0.1.0</span></footer>`; void health(); }
function login(register=false) {
 shell(`<main class="entry"><section class="intro"><div class="eyebrow">MỘT THẾ GIỚI · BA HUYẾT MẠCH</div><h1>Đạo trời vô tận.<br><em>Con đường của ngươi.</em></h1><p>Từ một phàm nhân, viết nên truyền kỳ của chính mình.<br>Chọn chủng tộc, bước vào thế giới và gặp những người đồng hành.</p><div class="landscape" aria-hidden="true"><div class="sun"></div><div class="mountain m1"></div><div class="mountain m2"></div><div class="mountain m3"></div><span class="landmark">THANH VÂN · U MINH · VẠN YÊU</span></div><div class="intro-note"><span>01 /</span> Hành trình bắt đầu từ Luyện Khí Sơ Kỳ.</div></section><section class="entry-card"><div class="eyebrow">CỔNG VÀO TAM GIỚI</div><h2>${register?'Lập tài khoản':'Chào đạo hữu'}</h2><p>${register?'Chuẩn bị cho hành trình đầu tiên.':'Đăng nhập để tiếp tục con đường tu luyện.'}</p><div class="tabs"><button id="tab-login" class="${!register?'active':''}">Đăng nhập</button><button id="tab-register" class="${register?'active':''}">Đăng ký</button></div><form id="auth"><label>Tài khoản<input name="username" autocomplete="username" minlength="3" maxlength="24" pattern="[a-zA-Z0-9_]{3,24}" placeholder="Tên tài khoản" required></label><label>Mật khẩu<input name="password" type="password" autocomplete="${register?'new-password':'current-password'}" minlength="10" maxlength="64" placeholder="Tối thiểu 10 ký tự" required></label><p id="error" class="error" role="alert" hidden></p><button class="primary full" type="submit">${register?'Tạo tài khoản':'Đăng nhập'} <span>→</span></button></form><div class="card-note">Mỗi chủng tộc khởi hành tại vùng đất riêng.<br>Gặp nhau qua cổng dịch chuyển tới Trung Châu.</div></section></main>`);
 document.querySelector('#tab-login')!.addEventListener('click',()=>login());document.querySelector('#tab-register')!.addEventListener('click',()=>login(true));
 document.querySelector<HTMLFormElement>('#auth')!.onsubmit=async event=>{
  event.preventDefault();if(busy)return;busy=true;const button=document.querySelector<HTMLButtonElement>('[type=submit]')!;button.disabled=true;
  const form=new FormData(event.currentTarget as HTMLFormElement);
  try {const result=await api<{token:string}>('/auth/'+(register?'register':'login'),{username:form.get('username'),password:form.get('password')});setToken(result.token);await characters();}catch(e){error((e as Error).message);button.disabled=false;}finally{busy=false;}
 };
}
async function characters() {
 try {
  const list=await api<Character[]>('/characters');
  shell(`<main class="roster"><div class="section-top"><div><div class="eyebrow">KHỞI NGUYÊN</div><h1>Chọn người bước tiếp</h1><p>Mỗi huyết mạch, một hành trình riêng.</p></div><button id="logout" class="quiet">Đăng xuất ↗</button></div><div class="character-grid">${list.map(c=>`<button class="character-card" data-id="${c.id}"><span class="race-figure ${c.race.toLowerCase()}"></span><span class="eyebrow">${races[c.race].name}</span><h2>${esc(c.name)}</h2><p>Cấp ${c.level} · Luyện Khí Sơ Kỳ</p><span class="character-status">Nhập môn · Chưa gia nhập thế lực</span><span class="enter-label">Vào thế giới →</span></button>`).join('')}${list.length<6?'<button class="character-card new-character" id="create"><span class="plus">＋</span><h2>Hành trình mới</h2><p>Tạo nhân vật · Chọn chủng tộc</p></button>':''}</div><p id="error" class="error" role="alert" hidden></p></main>`);
  document.querySelector('#logout')!.addEventListener('click',()=>{setToken('');login();});
  list.forEach(c=>{const card=document.querySelector(`[data-id="${c.id}"]`);if(card){card.querySelector('p')!.textContent=`Cấp ${c.level} · ${c.realm==='TRUC_CO'?'Trúc Cơ':'Luyện Khí'} ${['Sơ Kỳ','Trung Kỳ','Hậu Kỳ','Viên Mãn'][c.realmStage]}`;card.querySelector('.character-status')!.textContent=`${c.cultivationPath||'Nhập môn'} · ${c.organization||'Chưa gia nhập thế lực'}`;}});
  document.querySelector('#create')?.addEventListener('click',creation);
  document.querySelectorAll<HTMLElement>('[data-id]').forEach(el=>el.onclick=()=>{const c=list.find(c=>c.id===el.dataset.id)!;void enter(c);});
 } catch(e) {setToken('');login();error((e as Error).message);}
}
function creation() {
 shell(`<main class="creation"><button id="back" class="quiet">← Danh sách nhân vật</button><div class="eyebrow">CHỌN HUYẾT MẠCH</div><h1>Ngươi đến từ đâu?</h1><p>Chủng tộc là khởi nguyên. Con đường tu luyện sẽ do ngươi lựa chọn từ cấp 10.</p><form id="create-form"><div class="race-grid">${(Object.keys(races) as Race[]).map((r,i)=>`<label class="race-card"><input type="radio" name="race" value="${r}" ${i===0?'checked':''}><span class="race-figure ${r.toLowerCase()}"></span><span class="race-title">${races[r].name}</span><span>${races[r].description}</span><small>Khởi đầu: ${races[r].city}</small></label>`).join('')}</div><div class="creation-bottom"><label>Tên nhân vật<input name="name" minlength="3" maxlength="24" placeholder="Đặt tên cho truyền kỳ của ngươi" required></label><button type="submit" class="primary">Bắt đầu hành trình →</button></div><p class="muted">Cấp 1 · Luyện Khí Sơ Kỳ · Nhập môn · Chưa có thế lực hoặc hệ tu luyện</p><p id="error" class="error" role="alert" hidden></p></form></main>`);
 document.querySelector('#back')!.addEventListener('click',()=>void characters());
 document.querySelector<HTMLFormElement>('#create-form')!.onsubmit=async e=>{
  e.preventDefault();if(busy)return;busy=true;const button=document.querySelector<HTMLButtonElement>('[type=submit]')!;button.disabled=true;
  const data=new FormData(e.currentTarget as HTMLFormElement);
  try{const c=await api<Character>('/characters',{name:data.get('name'),race:data.get('race')});await enter(c);}catch(e){error((e as Error).message);button.disabled=false;}finally{busy=false;}
 };
}
async function enter(c:Character) {
 if(busy&&connection)return;
 try{
  const {ticket}=await api<{ticket:string}>(`/characters/${c.id}/connection-ticket`,{});
  lastSnapshot=null;players=[];
  shell(gameShell(c));
  connection=new GameConnection(onMessage,intentional=>{if(!intentional){document.querySelector('#connection-status')!.textContent='Mất kết nối';error('Kết nối đã đóng. Nhấn Rời thế giới rồi vào lại để kết nối lại.');}});
  activeGame=createGame(connection);enhanceGame(c,connection);connection.connect(ticket);
  document.querySelector('#map-button')!.addEventListener('click',showMap);
  document.querySelector('#character-button')!.addEventListener('click',showCharacter);
  document.querySelector('#close-dialog')!.addEventListener('click',()=>document.querySelector<HTMLDialogElement>('#info-dialog')!.close());
  document.querySelector<HTMLDialogElement>('#info-dialog')!.addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();document.querySelector<HTMLDialogElement>('#info-dialog')!.close();}});
  document.querySelector<HTMLFormElement>('#chat-form')!.onsubmit=e=>{e.preventDefault();const input=document.querySelector<HTMLInputElement>('#chat-input')!;if(input.value.trim()){connection?.send('CHAT',{message:input.value});input.value='';}};
 }catch(e){error((e as Error).message);}
}
function onMessage(type:string,p:any) {
 combatMessage(type,p);
 if(type==='SKILLS_UPDATED')activeGame?.scene.setPathSkill(p.find((s:any)=>s.requiredPath)?.id||'');
 if(type==='COMBAT_EVENT'||type==='SKILL_CAST')activeGame?.scene.effect(p);
 if(type==='WORLD_SNAPSHOT') {
  lastSnapshot=p;players=p.players;
  // AUTH can arrive before Phaser finishes booting.
  const apply=()=>{if(!activeGame)return;if(activeGame.scene.sys.isActive()){activeGame.scene.loadWorld(p);updateHud();}else setTimeout(apply,20);};apply();
 }else if(type==='ENTITY_UPDATE'){players=p.players;if(activeGame?.scene.sys.isActive()){activeGame.scene.sync(players);activeGame.scene.syncMonsters(p.monsters);activeGame.scene.syncLoot(p.loot);}updateHud();}
 else if(type==='PLAYER_LEFT'){activeGame?.scene.remove(p.id);}
 else if(type==='AUTH_RESULT'){document.querySelector('#connection-status')!.textContent='Đã kết nối · WebSocket';}
 else if(type==='CHAT'){const log=document.querySelector('#chat-log')!;const line=document.createElement('p');const name=document.createElement('strong');name.textContent=p.name+': ';line.append(name,document.createTextNode(p.message));log.append(line);while(log.childElementCount>60)log.firstElementChild?.remove();log.scrollTop=log.scrollHeight;}
 else if(type==='ERROR')error(p.message);
}
function updateHud(){
 if(!lastSnapshot)return;
 document.querySelector('#map-name')!.textContent=lastSnapshot.map.name;
 document.querySelector('#population')!.textContent=players.length+' đạo hữu';
 const canvas=document.querySelector<HTMLCanvasElement>('#minimap')!;const ctx=canvas.getContext('2d')!;ctx.clearRect(0,0,260,74);ctx.fillStyle='#273e3a';ctx.fillRect(6,49,248,3);
 lastSnapshot.map.portals.forEach(p=>{ctx.fillStyle='#be98f0';ctx.fillRect(6+p.x/lastSnapshot!.map.width*248,35,4,15);});
 players.forEach(p=>{ctx.fillStyle=p.id===lastSnapshot!.selfId?'#e6ca89':'#89b6c3';ctx.fillRect(6+p.x/lastSnapshot!.map.width*248,15+p.y/lastSnapshot!.map.ground*32,5,5);});
}
function showCharacter(){void showPanel('character');}
function showMap(){void showPanel('map');}
function leave(){connection?.close();connection=null;activeGame?.game.destroy(true);activeGame=null;void characters();}
window.addEventListener('keydown',e=>{if(!connection||e.target instanceof HTMLInputElement||document.querySelector('dialog[open]'))return;if(['Space','ArrowLeft','ArrowRight'].includes(e.code))e.preventDefault();if(e.code==='KeyC')showCharacter();if(e.code==='KeyM')showMap();if(e.code==='Enter'){e.preventDefault();document.querySelector<HTMLInputElement>('#chat-input')?.focus();}});
window.addEventListener('pagehide',()=>connection?.close());
window.addEventListener('game:leave',leave);
if(hasToken())void characters();else login();

