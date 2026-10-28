import Phaser from 'phaser';
import type {MapDefinition,Snapshot,Monster,Bot} from './types';

/** Display only; it cannot change authoritative collision, AI or position. */
export class WorldDebug {
 private graphics:Phaser.GameObjects.Graphics;
 private info:Phaser.GameObjects.Text;
 private sensors:Phaser.GameObjects.Graphics;
 private enabled=false;
 constructor(scene:Phaser.Scene,private map:MapDefinition,private snapshot:Snapshot){this.graphics=scene.add.graphics().setDepth(500);this.sensors=scene.add.graphics().setDepth(499);this.info=scene.add.text(12,185,'',{fontSize:'10px',fontFamily:'monospace',backgroundColor:'#061717e8',padding:{x:8,y:6},color:'#d7e7bb'}).setScrollFactor(0).setDepth(501);this.render();}
 toggle(){this.enabled=!this.enabled;this.render();}
 destroy(){this.graphics.destroy();this.sensors.destroy();this.info.destroy();}
 update(monsters:Monster[],tick:number,durationMs=0,bots:Bot[]=[]){this.sensors.clear();if(!this.enabled)return;for(const m of monsters){if(m.detection)this.sensors.lineStyle(1,0xffc07a,.2).strokeCircle(m.x,m.y,m.detection);if(m.leashRadius)this.sensors.lineStyle(1,0xff7777,.15).strokeCircle(m.spawnX??m.x,m.spawnY??m.y,m.leashRadius);}for(const b of bots)this.sensors.lineStyle(1,0x92c7ff,.2).strokeCircle(b.x,b.y,b.perception);this.info.setText(`${this.map.id} v${this.map.version} · ${this.map.mapType}\nInstance: ${this.snapshot.instanceId||'open'} · ${this.map.recommendedLevelMin}–${this.map.recommendedLevelMax}\nTick ${tick} · ${durationMs.toFixed(2)}ms · monsters ${monsters.length} · AI ${bots.length}\n${monsters.slice(0,4).map(m=>`${m.type}: ${m.state}`).join('\n')}\n${bots.slice(0,3).map(b=>`${b.botType}: ${b.state}`).join('\n')}\n[F3] collision / portals / routes / spawn zones`);}
 private render(){this.graphics.clear();this.graphics.visible=this.enabled;this.info.visible=this.enabled;if(!this.enabled)return;const g=this.graphics,m=this.map;
  for(const r of [...m.groundColliders,...m.obstacles])g.lineStyle(2,0xff7766,.9).strokeRect(r.x,r.y,r.width,r.height);
  for(const r of m.platforms)g.lineStyle(2,0xffe17a,.9).strokeRect(r.x,r.y,r.width,r.height);
  for(const p of m.portals)g.lineStyle(2,0xc990ff,.9).strokeRect(p.triggerBounds.x,p.triggerBounds.y,p.triggerBounds.width,p.triggerBounds.height);
  for(const z of m.safeZones)g.lineStyle(1,0x6bdca7,.7).strokeRect(z.bounds.x,z.bounds.y,z.bounds.width,z.bounds.height);
  for(const s of m.monsterSpawnGroups)g.lineStyle(2,0xe28b5a,.7).lineBetween(s.startX,s.y-40,s.endX,s.y-40);
  for(const w of m.waypoints)g.lineStyle(1,0x80d9ff,1).strokeCircle(w.x,w.y-4,6);
  for(const e of m.navigation){const a=m.waypoints.find(w=>w.id===e.from),b=m.waypoints.find(w=>w.id===e.to);if(a&&b)g.lineStyle(1,e.type==='WALK'?0x80d9ff:0xffcc77,.8).lineBetween(a.x,a.y-8,b.x,b.y-8);}
 }
}
