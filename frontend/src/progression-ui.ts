import {duelMarkup,bindDuelPanel,requestDuel} from './duel-ui';
import { api } from './api';
import {races,type Character,type MapDefinition,type Skill} from './types';
import {partyMarkup,bindPartyPanel,requestParty} from './social-ui';
import {dungeonMarkup,bindDungeonPanel,requestDungeon} from './activity-ui';
const esc=(s:unknown)=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
const slots:Record<string,string>={WEAPON:'Vũ khí',ARMOR:'Áo',LEGS:'Quần',FEET:'Giày',ARTIFACT:'Pháp bảo',ACCESSORY:'Phụ kiện'};
const status:Record<string,string>={AVAILABLE:'Chưa nhận',ACTIVE:'Đang thực hiện',CLAIMED:'Đã lĩnh thưởng'};
let worldMap:MapDefinition|null=null;
let renderVersion=0;
export function setPanelMap(map:MapDefinition){worldMap=map;}
export async function openProgression(kind:string,c:Character){
 const version=++renderVersion;
 const dialog=document.querySelector<HTMLDialogElement>('#info-dialog');if(!dialog)return;
 dialog.dataset.panel=kind;
 if(!dialog.open)dialog.show();
 document.querySelector('#dialog-content')!.innerHTML='<p>Đang tải…</p>';
 const path='/characters/'+c.id;
 let content='';
 const actions:Record<string,{url:string;body:unknown}>={};
 const button=(key:string,label:string,url:string,body:unknown={},disabled=false)=>{actions[key]={url,body};return `<button data-action="${key}" ${disabled?'disabled':''}>${label}</button>`;};
 if(kind==='quests'){
  const quests=await api<any[]>(path+'/quests');
  content=`<div class="eyebrow">NHẬN TẠI TIẾP DẪN SỨ · X = 300</div><h2>Nhiệm vụ</h2><div class="dialog-list">${quests.map(q=>`<section class="dialog-row"><h3>${esc(q.definition.name)}</h3><p>${esc(q.definition.description)}</p><p>Cấp ${q.requiredLevel} · ${status[q.status]} · ${q.progress}/${q.definition.count}</p><p>Thưởng: ${q.definition.exp} EXP · ${q.definition.cultivation} tu vi · ${q.definition.stones} linh thạch</p>${q.status==='AVAILABLE'?button(q.definition.id,'Nhận nhiệm vụ',path+'/quests/'+q.definition.id+'/accept',{},c.level<q.requiredLevel):q.status==='ACTIVE'?button(q.definition.id,'Lĩnh thưởng',path+'/quests/'+q.definition.id+'/claim',{},q.progress<q.definition.count):'<strong>Đã hoàn thành</strong>'}</section>`).join('')}</div>`;
 }else if(kind==='inventory'){
  const data=await api<any>(path+'/inventory');
  const symbols:Record<string,string>={WEAPON:'⚔',ARMOR:'▣',LEGS:'Π',FEET:'⌁',ARTIFACT:'✧',ACCESSORY:'◉'};
  const row=(i:any,equipped:boolean)=>`<div class="dialog-row item-line"><span class="item-icon" aria-hidden="true">${symbols[i.slot]|| (i.id==='POTION'?'◈':'◇')}<small>${data.items[i.id]}</small></span><div><strong>${esc(i.name)}</strong><p>${i.slot?`${slots[i.slot]} · Công +${i.attack} · Thủ +${i.defense} · Cấp ${i.level}${i.path?' · '+esc(i.path):''}`:i.id==='POTION'?'Hồi 55 HP và 30 MP':'Nguyên liệu tu luyện'}</p>${i.slot?(equipped?button('unequip-'+i.id,'Tháo',path+'/inventory/unequip',{slot:i.slot}):button('equip-'+i.id,'Trang bị',path+'/inventory/equip',{itemId:i.id})):i.id==='POTION'?button('use-'+i.id,'Sử dụng',path+'/inventory/use',{itemId:i.id}):''}${equipped?'<small>Đang trang bị</small>':''}</div></div>`;
  content=`<div class="eyebrow">TRANG BỊ TRÊN NGƯỜI</div><h2>Hành trang</h2><p>Công ${data.attack} · Thủ ${data.defense}</p><div class="dialog-list">${Object.entries(slots).map(([slot,name])=>{const item=data.definitions.find((i:any)=>i.id===data.equipment[slot]);return item?row(item,true):`<div class="dialog-row item-line"><span class="item-icon" aria-hidden="true">${symbols[slot]}</span><div><strong>${name}</strong><p class="empty-slot">Chưa trang bị</p></div></div>`;}).join('')}<h3>Trong túi</h3>${data.definitions.filter((i:any)=>(data.items[i.id]||0)>0&&data.equipment[i.slot]!==i.id).map((i:any)=>row(i,false)).join('')}</div>`;
  if(version===renderVersion)document.querySelector('#wallet-stones')!.textContent=String(data.stones);
 }else if(kind==='cultivation'){
  const data=await api<any>(path+'/cultivation');
  content=`<div class="eyebrow">NGHỊCH THIÊN CẢI MỆNH</div><h2>Tu luyện</h2><div class="dialog-row"><h3>${data.realm==='TRUC_CO'?'Trúc Cơ':'Luyện Khí'} ${['Sơ Kỳ','Trung Kỳ','Hậu Kỳ','Viên Mãn'][data.stage]}</h3><p>Tu vi hiện có: ${data.points}</p><p>${data.atLimit?'Đã đạt giới hạn cảnh giới MVP.':`Đột phá cần ${data.requiredPoints} tu vi và cấp ${data.requiredLevel}.`}</p>${button('breakthrough','Đột phá cảnh giới',path+'/cultivation/breakthrough',{},data.atLimit||data.points<data.requiredPoints||data.level<data.requiredLevel)}</div><p>Cấp độ và cảnh giới phát triển riêng biệt. Kiếm tu vi từ quái và nhiệm vụ.</p>`;
 }else if(kind==='organizations'){
  const data=await api<any>(path+'/available-organizations');
  content=`<div class="eyebrow">CON ĐƯỜNG TU LUYỆN</div><h2>Lựa chọn thế lực</h2><p>Yêu cầu cấp ${data.requiredLevel}. Nhận nhiệm vụ Con Đường Tu Luyện tại NPC, rồi chọn một khảo hạch.</p>${data.currentOrganization?'<p>Đã gia nhập: '+esc(data.organizations.find((o:any)=>o.id===data.currentOrganization)?.name)+'</p>':''}${data.trialOrganization?`<p>Khảo hạch: ${Math.min(data.trialProgress,data.trialRequired)}/${data.trialRequired} quái. Trở lại NPC để xác nhận sau khi hoàn thành.</p>`:''}<div class="dialog-list">${data.organizations.map((o:any)=>`<section class="dialog-row"><h3>${esc(o.name)}</h3><p>${esc(o.path)} · ${esc(o.archetype)}</p><p>${esc(o.description)}<br>Kỹ năng: ${esc(o.skill)}</p>${data.currentOrganization?'':data.trialOrganization===o.id?button('join-'+o.id,'Xác nhận gia nhập',path+'/organization/join',{organizationId:o.id},data.trialProgress<data.trialRequired):button('trial-'+o.id,'Chọn khảo hạch',path+'/organization/trial',{organizationId:o.id},!!data.trialOrganization||data.level<data.requiredLevel||!data.pathQuestAccepted)}</section>`).join('')}</div>`;
 }else if(kind==='skills'){
  const skills=await api<Skill[]>(path+'/skills');
  content=`<div class="eyebrow">CÔNG PHÁP ĐÃ LĨNH NGỘ</div><h2>Kỹ năng</h2>${skills.map(s=>`<section class="dialog-row"><h3>${esc(s.name)}</h3><p>${esc(s.description)}</p><p>MP ${s.mpCost} · Hồi chiêu ${s.cooldown/1000}s · Tầm ${s.range}</p></section>`).join('')}<p>Dùng thanh kỹ năng phía dưới khi đóng bảng. Kỹ năng thế lực mở sau khảo hạch.</p>`;
 }else if(kind==='character'){
  const data=await api<any>(path+'/inventory');
  content=`<div class="eyebrow">${races[c.race].name}</div><h2>${esc(c.name)}</h2><div class="dialog-row"><p>Cấp: ${c.level} · EXP: ${c.exp}/${c.level*100}</p><p>Công: ${data.attack} · Thủ: ${data.defense}</p><p>${c.realm==='TRUC_CO'?'Trúc Cơ':'Luyện Khí'} ${['Sơ Kỳ','Trung Kỳ','Hậu Kỳ','Viên Mãn'][c.realmStage]} · Tu vi ${c.cultivation}</p><p>Thế lực: ${esc(c.organization||'Chưa gia nhập')}</p><p>Hệ tu luyện: ${esc(c.cultivationPath||'Chưa lựa chọn')}</p></div>`;
  if(version===renderVersion)document.querySelector('#wallet-stones')!.textContent=String(data.stones);
 }else if(kind==='map'){
  content=`<div class="eyebrow">BẢN ĐỒ TAM GIỚI</div><h2>${esc(worldMap?.name||'Thế giới')}</h2><div class="dialog-row"><p>Thanh Vân Thành ⇄ Trung Châu</p><p>U Minh Thành ⇄ Trung Châu</p><p>Vạn Yêu Lâm ⇄ Trung Châu</p></div><h3>Cổng trong khu vực</h3>${worldMap?.portals.map(p=>`<div class="dialog-row"><strong>◇ ${esc(p.label)}</strong><p>Vị trí x = ${p.x} · Đến gần và nhấn E.</p></div>`).join('')||''}`;
 }else if(kind==='party'){
  content=partyMarkup();
 }else if(kind==='dungeon'){
  content=dungeonMarkup();
 }else if(kind==='duel'){
  content=duelMarkup();
 }else if(kind==='functions'){
  const availability=await api<{requiredLevel:number;level:number}>(path+'/available-organizations');
  content=`<div class="eyebrow">ĐỒNG HÀNH CÙNG ĐẠO HỮU</div><h2>Chức năng</h2><div class="function-grid"><button data-open="character">Nhân vật [C]</button><button data-open="map">Bản đồ [M]</button><button data-open="party">Tổ đội</button><button data-open="dungeon">Phó bản tổ đội</button><button data-open="duel">Thách đấu 1v1</button><button data-open="organizations" ${availability.level<availability.requiredLevel?'disabled':''}>Thế lực${availability.level<availability.requiredLevel?' · Cấp '+availability.requiredLevel:''}</button><button data-open="quests">Nhiệm vụ [Q]</button><button data-open="inventory">Hành trang [I]</button><button id="leave">Rời thế giới ↗</button><button id="network-retry">Kết nối lại</button>${['localhost','127.0.0.1'].includes(location.hostname)?'<button id="network-test">Thử ngắt mạng 7 giây</button>':''}</div><div class="dialog-row"><p>A / D: di chuyển · Space: nhảy</p><p>J: đánh thường · K: Linh Khí Kích</p><p>L: kỹ năng tộc · U: kỹ năng thế lực</p><p>Shift: thân pháp · E: tương tác</p></div>`;
 }
 if(version!==renderVersion||!dialog.isConnected||!dialog.open)return;
 document.querySelectorAll<HTMLButtonElement>('[data-panel]').forEach(b=>b.setAttribute('aria-selected',String(b.dataset.panel===kind||(b.dataset.panel==='functions'&&['character','map','organizations'].includes(kind)))));
 document.querySelector('#dialog-content')!.innerHTML=content+'<p id="panel-message" role="status"></p><button class="quiet" id="refresh-panel">Làm mới ↻</button>';
 if(kind==='party'){bindPartyPanel();requestParty();}
 if(kind==='dungeon'){bindDungeonPanel();requestDungeon();}
 if(kind==='duel'){bindDuelPanel();requestDuel();}
 dialog.focus({preventScroll:true});
 document.querySelectorAll<HTMLButtonElement>('[data-open]').forEach(b=>b.onclick=()=>void openProgression(b.dataset.open!,c));
 document.querySelector('#network-test')?.addEventListener('click',()=>{dialog.close();window.dispatchEvent(new CustomEvent('game:network-test'));});
 document.querySelector('#network-retry')?.addEventListener('click',()=>{dialog.close();window.dispatchEvent(new CustomEvent('game:network-retry'));});
 document.querySelector('#leave')?.addEventListener('click',()=>window.dispatchEvent(new CustomEvent('game:leave')));
 document.querySelector('#refresh-panel')!.addEventListener('click',()=>void openProgression(kind,c));
 document.querySelectorAll<HTMLButtonElement>('[data-action]').forEach(b=>b.onclick=async()=>{
  b.disabled=true;
  try{const action=actions[b.dataset.action!];await api(action.url,action.body);if(version!==renderVersion||!dialog.open)return;await openProgression(kind,c);const message=document.querySelector('#panel-message')!;message.className='dialog-message';message.textContent='Đã cập nhật.';}catch(e){if(version!==renderVersion)return;b.disabled=false;const message=document.querySelector('#panel-message')!;message.className='dialog-error';message.textContent=(e as Error).message;}
 });
}
