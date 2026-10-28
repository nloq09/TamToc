import Phaser from 'phaser';
import {races,type Player} from './types';
import {KIEM_TU_TEXTURE,animationKey,type SpriteMetadata} from './sprite-pack';

/** Visual state follows authoritative snapshots/events; frames never produce gameplay damage. */
export class VisualComponent {
 readonly container:Phaser.GameObjects.Container;
 target:Player;
 private readonly hp:Phaser.GameObjects.Rectangle;
 private readonly label:Phaser.GameObjects.Text;
 private readonly sprite:Phaser.GameObjects.Sprite|null;
 private moving=false;
 private airborne=false;
 private actionUntil=0;
 private currentAnimation='';

 constructor(private scene:Phaser.Scene,p:Player,self:boolean,private metadata:SpriteMetadata|null){
  this.target=p;
  const race=races[p.race];
  const halo=scene.add.ellipse(0,1,38,7,race.color,self?0.45:0.25);
  let body:Phaser.GameObjects.GameObject;
  if(metadata){
   this.sprite=scene.add.sprite(0,0,KIEM_TU_TEXTURE,0)
    .setOrigin(metadata.root_anchor.x/metadata.size.width,metadata.root_anchor.y/metadata.size.height)
    .setFlipX(p.facing<0);
   this.currentAnimation=p.hp>0?'idle':'death';
   this.sprite.play(animationKey(this.currentAnimation));
   body=this.sprite;
  }else{
   this.sprite=null;
   body=scene.add.rectangle(0,-22,32,44,race.color).setStrokeStyle(self?2:1,self?0xf5e5b3:0xffffff,.8);
  }
  this.label=scene.add.text(0,-81,`${p.name} · Lv.${p.level}`,{fontFamily:'Segoe UI',fontSize:'12px',color:'#e9f2ef',backgroundColor:'#0c171dcc',padding:{x:5,y:2}}).setOrigin(.5);
  this.hp=scene.add.rectangle(-20,-65,40,3,0x74d9a9).setOrigin(0,.5);
  const raceMark=scene.add.rectangle(-24,-65,3,3,race.color);
  this.container=scene.add.container(p.x,p.y,[halo,body,this.label,this.hp,raceMark]);
 }

 receive(next:Player){
  const previous=this.target;
  const vertical=next.y-previous.y;
  this.moving=Math.abs(next.x-previous.x)>.4;
  const wasAirborne=this.airborne;
  this.airborne=Math.abs(vertical)>.4;
  this.target=next;
  this.sprite?.setFlipX(next.facing<0);
  if(next.hp<=0){if(previous.hp>0)this.play('death');return;}
  if(previous.hp<=0){
   this.actionUntil=0;this.airborne=false;
   this.container.setPosition(next.x,next.y);this.play('idle');return;
  }
  if(wasAirborne&&!this.airborne)this.action('land');
 }

 action(name:'attack_1'|'cast'|'hurt'|'land'|'run'){
  if(!this.sprite||!this.metadata||this.target.hp<=0)return;
  if(name==='land'&&this.actionUntil>this.scene.time.now)return;
  const definition=this.metadata.animations[name];if(!definition)return;
  this.actionUntil=this.scene.time.now+(name==='run'?350:definition.frames/definition.fps*1000);
  this.play(name,true);
 }

 private play(name:string,restart=false){
  if(!restart&&this.currentAnimation===name)return;
  if(this.scene.anims.exists(animationKey(name))){this.currentAnimation=name;this.sprite?.play(animationKey(name));}
 }

 update(dt:number){
  const alpha=1-Math.exp(-dt/45);
  this.container.x=Phaser.Math.Linear(this.container.x,this.target.x,alpha);
  this.container.y=Phaser.Math.Linear(this.container.y,this.target.y,alpha);
  this.hp.scaleX=this.target.hp/this.target.maxHp;
  this.label.setText(`${this.target.name} · Lv.${this.target.level}`);
  this.container.alpha=this.target.hp>0?1:.65;
  if(this.target.hp<=0)this.play('death');
  else if(this.scene.time.now>=this.actionUntil)this.play(this.airborne?'jump':this.moving?'run':'idle');
 }

 destroy(){this.container.destroy();}
}
