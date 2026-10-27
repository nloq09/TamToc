/** Transport sends intent only. Rendering never modifies authoritative positions. */
export class GameConnection {
 private socket:WebSocket|null=null;
 private intentional=false;
 constructor(private onMessage:(type:string,payload:any)=>void,private onClose:(intentional:boolean)=>void) {}
 connect(ticket:string) {
  const scheme=location.protocol==='https:'?'wss:':'ws:';
  this.socket=new WebSocket(`${scheme}//${location.host}/ws`);
  this.socket.onopen=()=>this.send('AUTH',{ticket});
  this.socket.onmessage=e=>{ const message=JSON.parse(e.data); this.onMessage(message.type,message.payload); };
  this.socket.onclose=()=>this.onClose(this.intentional);
  this.socket.onerror=()=>this.onMessage('ERROR',{message:'Không kết nối được máy chủ. Kiểm tra backend và thử vào lại.'});
 }
 send(type:string,payload:unknown={}) { if(this.socket?.readyState===WebSocket.OPEN) this.socket.send(JSON.stringify({type,payload})); }
 close() { this.intentional=true; this.send('LEAVE_WORLD'); this.socket?.close(); }
}
