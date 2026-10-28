import Phaser from 'phaser';
import { type MapDefinition, type Player, type Snapshot,type Monster } from './types';
import type { GameConnection } from './network';
import {VisualComponent} from './player-visual';
import {preloadPlayerSprites,registerPlayerAnimations,type SpriteMetadata} from './sprite-pack';

export class WorldScene extends Phaser.Scene {
 private visuals=new Map<string,VisualComponent>();
 private selfId=''; private currentMap:MapDefinition|null=null;
 private keys!:Record<string,Phaser.Input.Keyboard.Key>;
 private sequence=0; private elapsed=0;
 private monsters=new Map<string,{container:Phaser.GameObjects.Container;hp:Phaser.GameObjects.Rectangle;label:Phaser.GameObjects.Text;target:Monster}>();
 private pathSkill='';
 private drops=new Map<string,Phaser.GameObjects.Rectangle>();
 private drawerWidth=-1;
 private spriteMetadata:SpriteMetadata|null=null;
 constructor(private connection:GameConnection) { super('world'); }
 preload(){preloadPlayerSprites(this);}
 create() {
  this.spriteMetadata=registerPlayerAnimations(this);
  this.keys=this.input.keyboard!.addKeys('A,D,LEFT,RIGHT,SPACE,E,J,K,L,U,SHIFT') as Record<string,Phaser.Input.Keyboard.Key>;
 }
 loadWorld(snapshot:Snapshot) {
  this.tweens.killAll();this.cameras.main.stopFollow();
  if(this.keys)Object.values(this.keys).forEach(key=>key.reset());
  this.sequence=0;this.elapsed=0;
  this.visuals.forEach(v=>v.destroy()); this.visuals.clear();this.monsters.clear();this.drops.clear(); this.children.removeAll(true);
  this.selfId=snapshot.selfId; this.currentMap=snapshot.map;
  this.drawerWidth=-1;
  const map=snapshot.map;
  this.cameras.main.setBackgroundColor(map.color);
  // Terrain, NPCs and monsters stay geometric while player visuals use the test sprite pack.
  for(let i=0;i<13;i++) {
   const x=i*290;
   this.add.triangle(x, map.ground,0,0,190,-180-(i%3)*65,380,0,0x577973,0.10).setOrigin(0);
   this.add.rectangle(x+100,map.ground-85,14,170,0x75a394,0.12);
   this.add.triangle(x+100,map.ground-100,-60,0,0,-100,60,0,0x75a394,0.16);
  }
  const grid=this.add.graphics().lineStyle(1,0xaac0b6,0.035);
  for(let x=0;x<map.width;x+=80) grid.lineBetween(x,0,x,map.ground);
  for(let y=60;y<map.ground;y+=80) grid.lineBetween(0,y,map.width,y);
  this.add.rectangle(0,map.ground,map.width,map.height-map.ground,0x162726).setOrigin(0);
  this.add.rectangle(0,map.ground,map.width,3,0x7c9b87).setOrigin(0);
  if(!['huyen-moc-party','duel-arena'].includes(map.id)){
  this.add.rectangle(300,map.ground-24,32,48,0xe4c567).setStrokeStyle(1,0xffe5a0);
  this.add.text(300,map.ground-70,'Tiếp Dẫn Sứ · [E]',{fontFamily:'Segoe UI',fontSize:'12px',color:'#f3d896'}).setOrigin(.5);
  }
  for(const platform of map.platforms) {
   this.add.rectangle(platform.x,platform.y,platform.width,platform.height,0x45635b).setOrigin(0).setStrokeStyle(1,0x8da58d,0.5);
  }
  for(const portal of map.portals) {
   this.add.rectangle(portal.x,map.ground-44,46,88,0x9470d5,0.3).setStrokeStyle(2,0xc7a4ff);
   this.add.rectangle(portal.x,map.ground-44,24,70,0x9470d5,0.16);
   this.add.text(portal.x,map.ground-110,portal.label,{fontFamily:'Segoe UI',fontSize:'13px',color:'#dfcafa'}).setOrigin(0.5);
   this.add.text(portal.x,map.ground+20,'[ E ]',{fontSize:'12px',color:'#bda6df'}).setOrigin(0.5);
  }
  this.cameras.main.setBounds(0,0,map.width,map.height);
  this.sync(snapshot.players);
  this.syncMonsters(snapshot.monsters);
  const self=this.visuals.get(this.selfId); if(self) this.cameras.main.startFollow(self.container,true,0.12,0.12);
 }
 sync(players:Player[]) {
  const ids=new Set(players.map(p=>p.id));
  for(const [id,visual] of this.visuals) if(!ids.has(id)) { visual.destroy(); this.visuals.delete(id); }
  for(const p of players) {
   const old=this.visuals.get(p.id);
   if(old) old.receive(p); else this.visuals.set(p.id,new VisualComponent(this,p,p.id===this.selfId,this.spriteMetadata));
  }
 }
 remove(id:string) { this.visuals.get(id)?.destroy(); this.visuals.delete(id); }
 setPathSkill(id:string){this.pathSkill=id;}
 syncLoot(loot:{id:string;x:number;y:number}[]){const ids=new Set(loot.map(l=>l.id));for(const[id,shape]of this.drops)if(!ids.has(id)){shape.destroy();this.drops.delete(id);}for(const l of loot)if(!this.drops.has(l.id))this.drops.set(l.id,this.add.rectangle(l.x,l.y-7,12,12,0xf2d08c).setStrokeStyle(1,0xfff2c6));}
 syncMonsters(monsters:Monster[]){
  const ids=new Set(monsters.map(m=>m.id));for(const [id,v]of this.monsters)if(!ids.has(id)){v.container.destroy();this.monsters.delete(id);}
  for(const m of monsters){
   let visual=this.monsters.get(m.id);
   if(!visual){
    const boss=m.type==='BOSS',w=boss?64:32,h=boss?80:35;
    const body=this.add.rectangle(0,-h/2,w,h,boss?0x9e3f46:0xe3984e).setStrokeStyle(1,0xffdca1,.7);
    const hp=this.add.rectangle(-w/2,-h-8,w,3,0xe9a079).setOrigin(0,.5);
    const label=this.add.text(0,-h-27,'',{fontFamily:'Segoe UI',fontSize:'11px',color:'#eed5b2'}).setOrigin(.5);
    const container=this.add.container(m.x,m.y,[body,hp,label]);visual={container,hp,label,target:m};this.monsters.set(m.id,visual);
   }
   visual.target=m;visual.hp.scaleX=m.hp/m.maxHp;visual.container.visible=m.hp>0;visual.label.setText(`${m.name} · ${m.level}${m.phase>1?' · Cuồng nộ':''}`);
  }
 }
 effect(event:any){
  if(!this.currentMap)return;
  if(typeof event.damage==='number'){
   this.visuals.get(event.target)?.action('hurt');
   const text=this.add.text(event.x,event.y-70,`${event.crit?'! ':''}${event.damage}`,{fontSize:event.crit?'22px':'16px',color:event.skill==='MONSTER'?'#ffa3a0':'#ffe4a4'}).setOrigin(.5);
   this.tweens.add({targets:text,y:text.y-45,alpha:0,duration:750,onComplete:()=>text.destroy()});
  }else{
   this.visuals.get(event.id)?.action(event.skill==='ATTACK'?'attack_1':event.skill==='DASH'?'run':'cast');
   const shape=this.add.rectangle(event.x+event.facing*45,event.y-25,event.skill==='QI'?130:55,12,0xe2d49a,.7);
   this.tweens.add({targets:shape,x:shape.x+event.facing*55,alpha:0,duration:230,onComplete:()=>shape.destroy()});
  }
 }
 update(_time:number,dt:number) {
  this.visuals.forEach(v=>v.update(dt));
  this.monsters.forEach(v=>{const alpha=1-Math.exp(-dt/60);v.container.x=Phaser.Math.Linear(v.container.x,v.target.x,alpha);});
  if(!this.keys || !this.currentMap) return;
  const drawer=document.querySelector<HTMLDialogElement>('#info-dialog');
  const drawerWidth=drawer?.open?drawer.getBoundingClientRect().width:0;
  if(drawerWidth!==this.drawerWidth){
   this.drawerWidth=drawerWidth;
   this.cameras.main.setBounds(-drawerWidth,0,this.currentMap.width+drawerWidth,this.currentMap.height);
   this.cameras.main.setFollowOffset(drawerWidth/2,0);
  }
  const focus=document.activeElement; const typing=focus instanceof HTMLInputElement || focus instanceof HTMLTextAreaElement || !!document.querySelector('dialog[open]');
  this.elapsed+=dt;
  if(this.elapsed>=50) {
   this.elapsed=0;
   const left=!typing&&(this.keys.A.isDown||this.keys.LEFT.isDown),right=!typing&&(this.keys.D.isDown||this.keys.RIGHT.isDown);
   this.connection.send('INPUT',{direction:Number(right)-Number(left),jump:!typing&&this.keys.SPACE.isDown,sequence:++this.sequence});
  }
  if(!typing&&Phaser.Input.Keyboard.JustDown(this.keys.E)) this.connection.send('INTERACT');
  if(!typing){
   if(Phaser.Input.Keyboard.JustDown(this.keys.J))this.connection.send('ATTACK');
   if(Phaser.Input.Keyboard.JustDown(this.keys.K))this.connection.send('CAST_SKILL',{skillId:'QI'});
   if(Phaser.Input.Keyboard.JustDown(this.keys.SHIFT))this.connection.send('CAST_SKILL',{skillId:'DASH'});
   if(Phaser.Input.Keyboard.JustDown(this.keys.L)){const race=this.visuals.get(this.selfId)?.target.race; if(race)this.connection.send('CAST_SKILL',{skillId:race+'_POWER'});}
   if(Phaser.Input.Keyboard.JustDown(this.keys.U)&&this.pathSkill)this.connection.send('CAST_SKILL',{skillId:this.pathSkill});
  }
 }
}
export function createGame(connection:GameConnection) {
 const scene=new WorldScene(connection);
 const game=new Phaser.Game({type:Phaser.AUTO,parent:'world',backgroundColor:'#142829',width:1100,height:640,scale:{mode:Phaser.Scale.RESIZE,autoCenter:Phaser.Scale.CENTER_BOTH},scene:[scene],render:{antialias:true}});
 return {game,scene};
}
