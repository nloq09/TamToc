import type {Character} from './types';
import type {GameConnection} from './network';
import {currentParty} from './social-ui';
const esc=(s:unknown)=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
let connection:GameConnection,self:Character,open:()=>void;
let lobby:any=null,run:any=null,result:any=null,error='',lastRevision=-1;
export function initializeActivities(c:Character,transport:GameConnection,openPanel:()=>void){self=c;connection=transport;open=openPanel;lobby=null;run=null;result=null;error='';lastRevision=-1;document.querySelector('#activity-notice')!.addEventListener('click',()=>open());}
export function requestDungeon(){connection.send('DUNGEON_LIST_REQUEST');}
export function dungeonMarkup(){
 if(run){const finished=run.state!=='ACTIVE';return `<div class="eyebrow">HUYỀN MỘC BÍ CẢNH · TỔ ĐỘI</div><h2>${finished?'Kết quả phó bản':'Đợt '+run.wave+' / '+run.totalWaves}</h2><div class="dialog-row"><p>Trạng thái: ${esc(run.state)} · ${esc(run.reason)}</p><p>Còn ${Math.ceil(run.remainingMs/1000)} giây · Wipe: ${run.wipes}/1</p>${result?`<p>Thưởng hoàn thành: ${result.exp||0} EXP · ${result.cultivation||0} tu vi · ${result.stones||0} linh thạch<br>Hồi Linh Đan × ${result.itemQuantity||0}</p>`:''}<button data-dungeon="leave">${finished?'Rời phó bản':'Rời và bỏ lượt tham gia'}</button></div><p>Thưởng hoàn thành chỉ cấp một lần. Thành viên đã rời hoặc không đủ điều kiện không nhận thưởng.</p><p class="dialog-error">${esc(error)}</p>`;}
 if(!lobby)return '<p>Đang tải phó bản…</p>';
 const party=currentParty(),ready=(lobby.ready||[]) as string[],members=party.members||[];
 return `<div class="eyebrow">TIẾP DẪN SỨ · TRUNG CHÂU</div><h2>Huyền Mộc Bí Cảnh</h2><p>Cấp ${lobby.minLevel}+ · 2–4 người · ${lobby.durationMs/60000} phút · 3 đợt quái</p><p>Cả đội tới Trung Châu, gần x=${lobby.entryX}, còn sống và xác nhận sẵn sàng. Đợt cuối là Huyền Mộc Yêu Vương.</p>${members.map(m=>`<div class="dialog-row"><strong>${esc(m.name)}${m.id===party.leaderId?' · Trưởng đội':''}</strong><p>Cấp ${m.level} · ${ready.includes(m.id)?'✓ Sẵn sàng':'Chưa sẵn sàng'}</p></div>`).join('')||'<p>Hãy tạo tổ đội trước khi tham gia.</p>'}<div class="dialog-row"><button data-dungeon="ready" ${!party.inParty?'disabled':''}>${ready.includes(self.id)?'Hủy sẵn sàng':'Xác nhận sẵn sàng'}</button><button data-dungeon="start" ${party.leaderId!==self.id||members.length<2||!members.every(m=>ready.includes(m.id))?'disabled':''}>Bắt đầu phó bản</button></div><p class="dialog-error">${esc(error)}</p>`;
}
export function bindDungeonPanel(){document.querySelector<HTMLElement>('#dialog-content')!.onclick=e=>{
 const button=(e.target as Element).closest<HTMLButtonElement>('[data-dungeon]');if(!button||button.disabled)return;error='';
 switch(button.dataset.dungeon){case 'ready':connection.send('DUNGEON_READY_SET',{ready:!lobby?.ready?.includes(self.id)});break;case 'start':connection.send('DUNGEON_START_REQUEST',{dungeonId:'HUYEN_MOC',requestId:crypto.randomUUID()});break;case 'leave':connection.send('DUNGEON_LEAVE_REQUEST');break;}
};}
function render(){const dialog=document.querySelector<HTMLDialogElement>('#info-dialog');if(dialog?.open&&dialog.dataset.panel==='dungeon'){const el=document.querySelector<HTMLElement>('#dialog-content')!;const scroll=el.scrollTop;el.innerHTML=dungeonMarkup();el.scrollTop=scroll;bindDungeonPanel();}const hud=document.querySelector<HTMLElement>('#activity-hud');if(hud){hud.hidden=!run;hud.textContent=run?`Huyền Mộc · Đợt ${run.wave}/3 · ${Math.ceil(run.remainingMs/1000)}s · ${run.state}`:'';}const notice=document.querySelector<HTMLButtonElement>('#activity-notice');if(notice){notice.hidden=!result;notice.textContent=result?'Xem kết quả phó bản':'';}}
export function activityMessage(type:string,p:any){
 switch(type){
  case 'WORLD_SNAPSHOT':if(p.mapId!=='huyen-moc-party'&&run){run=null;result=null;render();}break;
  case 'DUNGEON_LIST':lobby=p;if(!p.runId){run=null;result=null;}render();break;
  case 'DUNGEON_INSTANCE_CREATED':case 'DUNGEON_WAVE_STARTED':case 'DUNGEON_PROGRESS_UPDATED':run=p;render();break;
  case 'DUNGEON_FINISHED':run=p.run;result=p.rewards;render();break;
  case 'DUNGEON_LEFT':run=null;result=null;requestDungeon();render();break;
  case 'DUNGEON_ERROR':error=p.message+' ('+p.code+')';render();break;
  case 'PARTY_UPDATED':if(p.revision!==lastRevision){lastRevision=p.revision;requestDungeon();}break;
 }
}
