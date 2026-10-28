export type ConnectionState='connecting'|'syncing'|'connected'|'reconnecting'|'failed'|'closed';
export interface ConnectionStatus {state:ConnectionState;attempt:number;message:string}
interface Message {type:string;payload:any;worldEpoch?:string;stateVersion?:number}
/** A full snapshot anchors AOI deltas; missing bases request a new snapshot, never guess. */
export class InterestState {
 private sets=new Map<string,Map<string,any>>();
 private meta:any={};
 private version=0;
 private static readonly kinds=['players','monsters','bots','loot'];
 reset(snapshot:any){this.sets.clear();for(const kind of InterestState.kinds)this.sets.set(kind,new Map((snapshot[kind]||[]).map((row:any)=>[row.id,row])));this.meta=Object.fromEntries(Object.entries(snapshot).filter(([k])=>!InterestState.kinds.includes(k)));this.version=snapshot.interestVersion??0;}
 apply(delta:any):any|null{
  if(!this.version||delta.baseInterestVersion!==this.version||delta.interestVersion!==this.version+1||delta.mapId!==this.meta.mapId||(delta.instanceId??null)!==(this.meta.instanceId??null))return null;
  for(const kind of InterestState.kinds){const diff=delta[kind];if(!diff||!Array.isArray(diff.upsert)||!Array.isArray(diff.remove))return null;}
  for(const kind of InterestState.kinds){const rows=this.sets.get(kind)!;for(const id of delta[kind].remove)rows.delete(id);for(const row of delta[kind].upsert)rows.set(row.id,row);}
  this.version=delta.interestVersion;this.meta={...this.meta,...Object.fromEntries(Object.entries(delta).filter(([k])=>!InterestState.kinds.includes(k)))};return {...this.meta,...Object.fromEntries(InterestState.kinds.map(kind=>[kind,[...this.sets.get(kind)!.values()]]))};
 }
}
interface Options {
 ticket:(signal?:AbortSignal)=>Promise<string>;
 characterId:string;
 url?:string;
 storage?:Pick<Storage,'getItem'|'setItem'|'removeItem'>;
 baseDelayMs?:number;
 maxDelayMs?:number;
 maxAttempts?:number;
}

/** No gameplay queue: reconnect sends fresh credentials and waits for a full snapshot. */
export class GameConnection {
 private socket:WebSocket|null=null;
 private stopped=false;
 private ready=false;
 private generation=0;
 private attempt=0;
 private lastVersion=-1;
 private worldEpoch='';
 private resumeToken='';
 private lastReceived=0;
 private retryTimer:ReturnType<typeof setTimeout>|null=null;
 private heartbeat:ReturnType<typeof setInterval>|null=null;
 private offlineUntil=0;
 private ticketRequest:AbortController|null=null;
 private interest=new InterestState();
 private readonly onMessage:(type:string,payload:any)=>void;
 private readonly onStatus:(status:ConnectionStatus)=>void;
 private readonly options:Options;
 private readonly storage:Options['storage'];

 constructor(onMessage:(type:string,payload:any)=>void,onStatus:(status:ConnectionStatus)=>void,options:Options){
  this.onMessage=onMessage;this.onStatus=onStatus;this.options=options;
  this.storage=options.storage??(typeof sessionStorage==='undefined'?undefined:sessionStorage);
  try{this.resumeToken=this.storage?.getItem(this.storageKey())||'';}catch{/* Storage can be unavailable in private sessions. */}
 }
 private storageKey(){return 'tamtoc-resume:'+this.options.characterId;}
 private status(state:ConnectionState,message:string){this.onStatus({state,attempt:this.attempt,message});}
 connect(){this.stopped=false;void this.open();}
 private async open(){
  if(this.stopped)return;
  const generation=++this.generation;this.ready=false;
  this.status(this.attempt?'reconnecting':'connecting',this.attempt?`Mất kết nối – đang kết nối lại (lần ${this.attempt})`:'Đang kết nối…');
  let ticket:string;
  const controller=new AbortController();this.ticketRequest=controller;
  const timeout=setTimeout(()=>controller.abort(),5000);
  try{ticket=await this.options.ticket(controller.signal);}
  catch(e){
   if(this.stopped||generation!==this.generation)return;
   const status=(e as {status?:number}).status;
   if(status===401||status===403||status===404){this.fail((e as Error).message);return;}
   this.schedule();return;
  }finally{clearTimeout(timeout);if(this.ticketRequest===controller)this.ticketRequest=null;}
  if(this.stopped||generation!==this.generation)return;
  const url=this.options.url||`${location.protocol==='https:'?'wss:':'ws:'}//${location.host}/ws`;
  const socket=new WebSocket(url);this.socket=socket;this.lastReceived=Date.now();
  this.startHeartbeat();
  socket.onopen=()=>{if(generation===this.generation&&!this.stopped)socket.send(JSON.stringify({type:'AUTH',payload:{ticket,protocolVersion:3,...(this.resumeToken?{resumeToken:this.resumeToken}:{})}}));};
  socket.onmessage=e=>{
   if(this.stopped||generation!==this.generation||this.socket!==socket)return;
   this.lastReceived=Date.now();
   let message:Message;
   try{message=JSON.parse(String(e.data));}catch{this.fail('Máy chủ gửi dữ liệu không hợp lệ.');return;}
   const {type,payload}=message;
   if(type==='AUTH_RESULT'){
    this.interest=new InterestState();
    if(payload.resumeToken){this.resumeToken=payload.resumeToken;try{this.storage?.setItem(this.storageKey(),this.resumeToken);}catch{}}
    this.worldEpoch=message.worldEpoch||payload.worldEpoch||'';this.lastVersion=-1;this.ready=false;
    this.status('syncing','Đang đồng bộ thế giới…');this.onMessage(type,payload);return;
   }
   if(type==='ERROR'&&!this.ready){
    this.onMessage(type,payload);
    if(payload.retryable||payload.code==='RATE_LIMITED'){socket.close(4000,'Retry later');return;}
    this.fail(payload.message||'Không thể khôi phục phiên. Hãy rời thế giới và đăng nhập lại.');return;
   }
   if(!this.ready&&type!=='WORLD_SNAPSHOT')return;
   if(message.worldEpoch&&this.worldEpoch&&message.worldEpoch!==this.worldEpoch)return;
   if(message.stateVersion!==undefined){if(message.stateVersion<=this.lastVersion)return;this.lastVersion=message.stateVersion;}
   if(type==='WORLD_SNAPSHOT'){
    this.interest.reset(payload);
    this.ready=true;this.attempt=0;this.onMessage(type,payload);this.status('connected','Đã kết nối · WebSocket');return;
   }
   if(!this.ready||type==='PONG')return;
   if(type==='ENTITY_DELTA'){
    const state=this.interest.apply(payload);
    if(!state){this.ready=false;this.status('syncing','Đang đồng bộ vùng quan sát…');socket.send(JSON.stringify({type:'RESUME_WORLD',payload:{}}));return;}
    this.onMessage('ENTITY_UPDATE',state);return;
   }
   this.onMessage(type,payload);
  };
  socket.onclose=e=>{
   if(generation!==this.generation||this.stopped)return;
   this.ready=false;this.stopHeartbeat();
   if(e.code===4001){this.fail('Phiên này đã được phục hồi ở một kết nối khác.');return;}
   this.schedule();
  };
  socket.onerror=()=>{/* onclose handles the retry; do not schedule two reconnect attempts. */};
 }
 private schedule(){
  if(this.stopped||this.retryTimer)return;
  this.ready=false;this.stopHeartbeat();
  if(++this.attempt>(this.options.maxAttempts??12)){this.fail('Chưa kết nối lại được. Mở Chức năng → Kết nối lại, hoặc rời thế giới.');return;}
  const delay=Math.min(this.options.maxDelayMs??5000,(this.options.baseDelayMs??500)*2**Math.min(this.attempt-1,5))*(.8+Math.random()*.4);
  this.status('reconnecting',`Mất kết nối – đang kết nối lại (lần ${this.attempt})`);
  this.retryTimer=setTimeout(()=>{this.retryTimer=null;void this.open();},Math.max(delay,this.offlineUntil-Date.now()));
 }
 private startHeartbeat(){
  this.stopHeartbeat();
  this.heartbeat=setInterval(()=>{
   const socket=this.socket;if(!socket)return;
   if(Date.now()-this.lastReceived>8000){socket.close(4000,'Server heartbeat missing');return;}
   if(socket.readyState===WebSocket.OPEN)socket.send(JSON.stringify({type:'PING',payload:{}}));
  },2000);
 }
 private stopHeartbeat(){if(this.heartbeat)clearInterval(this.heartbeat);this.heartbeat=null;}
 private fail(message:string){this.stopped=true;this.ready=false;this.generation++;this.ticketRequest?.abort();this.stopHeartbeat();if(this.retryTimer)clearTimeout(this.retryTimer);this.retryTimer=null;this.socket?.close();this.status('failed',message);}
 send(type:string,payload:unknown={}){
  if(!this.ready||this.stopped||this.socket?.readyState!==WebSocket.OPEN)return false;
  this.socket.send(JSON.stringify({type,payload}));return true;
 }
 retry(){if(this.retryTimer)clearTimeout(this.retryTimer);this.retryTimer=null;this.ticketRequest?.abort();this.socket?.close();this.generation++;this.attempt=0;this.stopped=false;void this.open();}
 simulateDisconnect(ms=7000){
  if(!this.ready)return;
  this.offlineUntil=Date.now()+ms;this.ready=false;this.status('reconnecting','Mất kết nối – đang thử ngắt mạng 7 giây…');this.socket?.close(4000,'Local reconnect test');
 }
 suspend(){this.stopped=true;this.ready=false;this.generation++;this.ticketRequest?.abort();if(this.retryTimer)clearTimeout(this.retryTimer);this.retryTimer=null;this.stopHeartbeat();this.socket?.close(4000,'Page suspended');}
 close(){
  this.send('LEAVE_WORLD');this.stopped=true;this.ready=false;this.generation++;
  this.ticketRequest?.abort();
  if(this.retryTimer)clearTimeout(this.retryTimer);this.retryTimer=null;this.stopHeartbeat();this.socket?.close(1000,'Leave world');
  try{if(this.storage?.getItem(this.storageKey())===this.resumeToken)this.storage?.removeItem(this.storageKey());}catch{}
  this.status('closed','Đã rời thế giới');
 }
}
