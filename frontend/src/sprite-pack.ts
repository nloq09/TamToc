import Phaser from 'phaser';

export const KIEM_TU_TEXTURE='kiem-tu';
const METADATA='kiem-tu-metadata';
export interface SpriteMetadata {
 size:{width:number;height:number};
 root_anchor:{x:number;y:number};
 sheet_columns:number;
 animations:Record<string,{frames:number;fps:number;loop:boolean;sheet_row:number}>;
}

export function preloadPlayerSprites(scene:Phaser.Scene){
 scene.load.spritesheet(KIEM_TU_TEXTURE,'/assets/kiem-tu/spritesheet.png',{frameWidth:64,frameHeight:64});
 scene.load.json(METADATA,'/assets/kiem-tu/metadata.json');
}

export function registerPlayerAnimations(scene:Phaser.Scene):SpriteMetadata|null {
 if(!scene.textures.exists(KIEM_TU_TEXTURE)||!scene.cache.json.exists(METADATA))return null;
 const metadata=scene.cache.json.get(METADATA) as SpriteMetadata;
 scene.textures.get(KIEM_TU_TEXTURE).setFilter(Phaser.Textures.FilterMode.NEAREST);
 for(const [name,definition]of Object.entries(metadata.animations)){
  const key=animationKey(name);
  if(scene.anims.exists(key))continue;
  const first=definition.sheet_row*metadata.sheet_columns;
  scene.anims.create({key,frames:scene.anims.generateFrameNumbers(KIEM_TU_TEXTURE,{start:first,end:first+definition.frames-1}),frameRate:definition.fps,repeat:definition.loop?-1:0});
 }
 return metadata;
}
export function animationKey(name:string){return 'kiem-tu:'+name;}
