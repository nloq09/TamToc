import type {GameConnection} from './network';
import {races,type Character,type Player} from './types';

interface Member {id:string;name?:string;race?:keyof typeof races;level?:number;hp?:number;maxHp?:number;mp?:number;maxMp?:number;status:string;mapId?:string;instanceId?:string|null}
interface Party {inParty:boolean;id?:string;leaderId?:string;maxSize:number;members?:Member[];revision?:number}
interface Invitation {id:string;name:string;inviterId:string;expiresAt:number}
const esc=(s:unknown)=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
let self:Character,connection:GameConnection;
let party:Party={inParty:false,maxSize:4},nearby:Player[]=[],invites:Invitation[]=[],message='';
let openParty:()=>void;

export function initializeSocial(c:Character,transport:GameConnection,open:()=>void){
 self=c;connection=transport;openParty=open;party={inParty:false,maxSize:4};nearby=[];invites=[];message='';
 document.querySelector('#party-notice')!.addEventListener('click',()=>openParty());
 const selector=document.querySelector<HTMLSelectElement>('#chat-channel');if(selector)selector.value='AREA';
}
function send(type:string,payload:Record<string,unknown>={}){
 message='';if(!connection.send(type,{...payload,requestId:crypto.randomUUID()}))message='Đang mất kết nối. Hãy chờ đồng bộ lại.';
}
export function requestParty(){connection.send('PARTY_STATE_REQUEST');connection.send('NEARBY_PLAYERS_REQUEST');}
export function currentParty(){return party;}
export function partyMarkup(){
 const leader=party.leaderId===self.id;
 return `<div class="eyebrow">ĐỒNG HÀNH TAM TỘC</div><h2>Tổ đội ${party.members?.length||0}/${party.maxSize}</h2><p>Nhân, Ma, Yêu có thể cùng đội. Tổ đội tạm được giữ trong lúc server chạy.</p>${invites.map(i=>`<div class="dialog-row"><strong>${esc(i.name)} mời ngươi vào đội</strong><p>Lời mời có hiệu lực 60 giây.</p><button data-party="accept" data-id="${i.id}">Chấp nhận</button><button data-party="decline" data-id="${i.id}">Từ chối</button></div>`).join('')}${party.inParty?(party.members||[]).map(m=>`<div class="dialog-row"><strong>${esc(m.name||'Đạo hữu')}${m.id===party.leaderId?' · Trưởng đội':''}</strong><p>${m.race?races[m.race].name:''} · Cấp ${m.level??'—'} · ${m.status==='ONLINE'?'Trực tuyến':m.status==='DISCONNECTED_GRACE'?'Đang nối lại':'Ngoại tuyến'}</p><p>HP ${m.hp??'—'}/${m.maxHp??'—'} · MP ${m.mp??'—'}/${m.maxMp??'—'}<br>${esc(m.mapId||'Chưa rõ')}${m.instanceId?' · Phó bản riêng':''}</p>${leader&&m.id!==self.id?`<button data-party="transfer" data-id="${m.id}" ${m.status!=='ONLINE'?'disabled':''}>Nhường trưởng đội</button><button data-party="kick" data-id="${m.id}">Đuổi</button>`:''}</div>`).join(''):'<p>Chưa có tổ đội. Mời một đạo hữu để bắt đầu.</p>'}<div class="dialog-row"><h3>Đạo hữu cùng khu vực</h3><button data-party="refresh">Làm mới danh sách</button>${nearby.map(p=>`<p>${esc(p.name)} · ${races[p.race].name} · Cấp ${p.level} <button data-party="invite" data-id="${p.id}" ${party.inParty&&!leader?'disabled':''}>Mời</button></p>`).join('')||'<p>Chưa có đạo hữu khác trong bản đồ này.</p>'}</div>${party.inParty?'<div class="dialog-row"><button data-party="leave">Rời tổ đội</button></div>':''}<p id="party-message" class="dialog-message">${esc(message)}</p><p>EXP/tu vi của một quái được chia theo ngân sách chung. Đồng đội cần ở gần, còn sống và có hoạt động gần đây; vật phẩm rơi thuộc từng người.</p>`;
}
export function bindPartyPanel(){
 const root=document.querySelector<HTMLElement>('#dialog-content')!;
 root.onclick=e=>{
  const button=(e.target as Element).closest<HTMLButtonElement>('[data-party]');if(!button||button.disabled)return;
  const id=button.dataset.id;
  switch(button.dataset.party){
   case 'refresh':requestParty();break;
   case 'invite':send('PARTY_INVITE',{targetCharacterId:id});break;
   case 'accept':send('PARTY_ACCEPT',{invitationId:id});break;
   case 'decline':send('PARTY_DECLINE',{invitationId:id});break;
   case 'leave':send('PARTY_LEAVE');break;
   case 'kick':send('PARTY_KICK',{targetCharacterId:id});break;
   case 'transfer':send('PARTY_TRANSFER_LEADER',{targetCharacterId:id});break;
  }
 };
}
function render(){
 const panel=document.querySelector<HTMLDialogElement>('#info-dialog');
 if(panel?.open&&panel.dataset.panel==='party'){
  const root=document.querySelector<HTMLElement>('#dialog-content')!;const scroll=root.scrollTop;root.innerHTML=partyMarkup();root.scrollTop=scroll;bindPartyPanel();
 }
 const notice=document.querySelector<HTMLElement>('#party-notice');if(notice){notice.hidden=invites.length===0;notice.textContent=`${invites[0]?.name||'Đạo hữu'} mời tổ đội · Xem`;}
 const hud=document.querySelector<HTMLElement>('#party-hud');if(hud){hud.hidden=!party.inParty;hud.innerHTML=(party.members||[]).map(m=>`<div><strong>${m.id===party.leaderId?'♔ ':''}${esc(m.name||'Đạo hữu')}</strong><span>${m.status==='ONLINE'?`HP ${m.hp}/${m.maxHp}`:'Đang nối lại…'}</span><i style="width:${m.hp!==undefined&&m.maxHp?m.hp/m.maxHp*100:0}%"></i></div>`).join('');}
}
export function socialMessage(type:string,p:any){
 switch(type){
  case 'PARTY_UPDATED':party=p;render();break;
  case 'PARTY_DISBANDED':party={inParty:false,maxSize:party.maxSize};message=p.reason==='KICKED'?'Đã rời đội theo quyết định trưởng đội.':'Đã rời tổ đội.';render();break;
  case 'NEARBY_PLAYERS':nearby=p;render();break;
  case 'PARTY_INVITATION_RECEIVED':invites=invites.filter(i=>i.id!==p.id).concat(p);render();break;
  case 'PARTY_INVITATION_RESOLVED':case 'PARTY_INVITATION_EXPIRED':invites=invites.filter(i=>i.id!==(p.invitationId||p.id));render();break;
  case 'PARTY_INVITATION_SENT':message='Đã gửi lời mời tổ đội.';render();break;
  case 'PARTY_INVITATION_DECLINED':message='Đạo hữu đã từ chối lời mời.';render();break;
  case 'PARTY_ERROR':message=p.message+' ('+p.code+')';render();break;
 }
}
