export type Race = 'HUMAN' | 'DEMON' | 'BEAST';
export interface Character { id:string; name:string; race:Race; characterClass:string; level:number; exp:number; cultivation:number; realm:string; realmStage:number; organization:string|null; cultivationPath:string|null; mapId:string }
export interface Player { id:string; name:string; race:Race; level:number; x:number; y:number; hp:number; maxHp:number; mp:number; maxMp:number; sequence:number; facing:number; exp:number;cultivation:number;realm:string;realmStage:number;organization:string|null;path:string|null;cooldowns:Record<string,number> }
export interface Monster {id:string;type:string;name:string;level:number;hp:number;maxHp:number;x:number;y:number;state:string;phase:number}
export interface Skill {id:string;name:string;description:string;mpCost:number;cooldown:number;range:number}
export interface MapDefinition { id:string; name:string; width:number; height:number; ground:number; color:string; platforms:{x:number;y:number;width:number;height:number}[]; portals:{x:number;target:string;label:string}[] }
export interface Snapshot { selfId:string; map:MapDefinition; players:Player[]; monsters:Monster[]; tick:number;serverTime:number;connectionId?:string;instanceId?:string|null;worldTick?:number }
export const races:Record<Race,{name:string;color:number;description:string;city:string}> = {
 HUMAN:{name:'Nhân Tộc',color:0x5aaaf1,description:'Lĩnh ngộ thiên đạo, tu luyện linh khí.',city:'Thanh Vân Thành'},
 DEMON:{name:'Ma Tộc',color:0xf2797d,description:'Rèn luyện ý chí, khai mở ma lực.',city:'U Minh Thành'},
 BEAST:{name:'Yêu Tộc',color:0x68cf9a,description:'Thức tỉnh huyết mạch, hòa cùng vạn vật.',city:'Vạn Yêu Lâm'}
};
