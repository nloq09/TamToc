from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import json, zipfile, math, random

OUT = Path('/mnt/data/kiem_tu_sprite_test')
OUT.mkdir(parents=True, exist_ok=True)
SZ=64
C={
    'edge':'#17233f','edge2':'#273457','hair':'#242744','hair_shadow':'#141d35','hair_light':'#445071',
    'skin':'#f4c29e','skin_shade':'#cf927f','skin_light':'#ffe2ba','eye':'#20648f','eye_light':'#9be7ff',
    'robe':'#ecf4ef','robe_light':'#ffffff','robe_shadow':'#a6bfd5','blue':'#4f86b9','blue_dark':'#244c7b',
    'turquoise':'#6ed9df','turquoise_dark':'#268c9c','belt':'#324e78','gold':'#d9b56b','gold_light':'#ffeca6',
    'pants':'#2a3658','boot':'#27334e','boot_highlight':'#526887','blade':'#eaffff','blade_shadow':'#7dd6f6',
    'aura':'#80ffff','aura2':'#2ea6e6','white':'#ffffff','sheath':'#465777'
}

def layer():return Image.new('RGBA',(SZ,SZ),(0,0,0,0))
def polygon(d,coords,fill,outline=None,w=1):
    d.polygon([(int(x),int(y)) for x,y in coords],fill=fill)
    if outline:d.line([(int(x),int(y)) for x,y in coords]+[(int(coords[0][0]),int(coords[0][1]))],fill=outline,width=w,joint='curve')
def seg(d,pts,fill,width,outline=None):
    if outline:d.line(pts,fill=outline,width=width+3,joint='curve')
    d.line(pts,fill=fill,width=width,joint='curve')
def ell(d,box,fill,outline=None,width=1):d.ellipse(tuple(map(int,box)),fill=fill,outline=outline,width=width)
def px(d,x,y,col,size=1):d.rectangle([int(x),int(y),int(x)+size-1,int(y)+size-1],fill=col)

def render(anim,t):
    spec={
        'idle':4,'walk':6,'run':6,'jump':4,'land':3,'attack_1':6,'cast':6,
        'hurt':3,'death':5,'meditate':4,'fly_sword':4
    }
    count=spec[anim]
    phase=(t/count)*2*math.pi
    dy=0
    if anim=='idle':dy=[0,0,-1,0][t]
    if anim=='walk':dy=[0,-1,0,0,-1,0][t]
    if anim=='run':dy=[0,-2,-1,0,-2,-1][t]
    if anim=='jump':dy=[0,-5,-9,-5][t]
    if anim=='land':dy=[-2,1,0][t]
    if anim=='attack_1':dy=[0,0,0,-1,0,0][t]
    if anim=='cast':dy=[0,0,-1,-1,0,0][t]
    if anim=='hurt':dy=[0,1,0][t]
    if anim=='meditate':dy=[1,1,0,1][t]
    if anim=='fly_sword':dy=[-2,-3,-2,-1][t]
    if anim=='death':dy=[0,2,5,7,8][t]
    # each is a 64x64 independent layer, composited in order for body-part separation
    ls={k:layer() for k in ['back_fx','back_hair','weapon_back','legs','robe_back','body','back_arm','head','front_hair','front_arm','weapon_front','front_fx']}
    ds={k:ImageDraw.Draw(v) for k,v in ls.items()}
    # coordinate transformer for body shift; head forward lean in running and attacking
    lean=1 if anim=='run' else (2 if anim=='attack_1' and t in (2,3,4) else (-2 if anim=='hurt' else 0))
    if anim=='death':lean=[0,-2,-6,-9,-9][t]
    ox=lean; oy=dy
    if anim=='meditate':ox=0
    if anim=='death' and t>=3:
        # explicit prone state (final two frames), body drawn as unified readable sprite
        g=ds['body']
        ell(g,(13,48,45,55),C['robe_shadow'],C['edge'],2)
        polygon(g,[(19,47),(29,45),(44,47),(47,52),(22,55)],C['robe'],C['edge'],2)
        polygon(g,[(28,46),(34,46),(40,50),(28,52)],C['blue'],C['edge'],1)
        ell(g,(10,43,26,54),C['skin'],C['edge'],2)
        polygon(g,[(9,44),(13,37),(22,40),(24,45),(16,46)],C['hair'],C['edge'],2)
        seg(g,[(39,51),(55,51)],C['pants'],5,C['edge'])
        ell(g,(52,49,59,54),C['boot'],C['edge'])
        if t==3:
            for x,y in [(13,39),(19,34),(35,38)]:px(ds['front_fx'],x,y,'#bbebff',2)
        order=list(ls)
        result=layer()
        for name in order:result.alpha_composite(ls[name])
        return result,ls

    if anim=='meditate':
        # character sitting cross-legged, feet planted at baseline
        x=ox;y=oy
        a=ds['legs'];b=ds['robe_back']
        polygon(a,[(21+x,49+y),(41+x,49+y),(46+x,54+y),(40+x,57+y),(21+x,57+y),(18+x,54+y)],C['pants'],C['edge'],2)
        ell(a,(18+x,52+y,32+x,58+y),C['boot'],C['edge'],1)
        ell(a,(34+x,52+y,47+x,58+y),C['boot'],C['edge'],1)
        polygon(b,[(25+x,43+y),(39+x,43+y),(47+x,52+y),(34+x,54+y),(17+x,52+y)],C['robe'],C['edge'],2)
        seg(b,[(23+x,51+y),(42+x,51+y)],C['blue'],2)
        yshift=5
    else:yshift=0
    x=ox;y=oy+yshift

    # rear ethereal ribbon / dark hair tail - enough visual identity at small size
    d=ds['back_hair']
    wind=(t%3-1) if anim in ('walk','run','attack_1') else (0 if t%2==0 else 1)
    bx=18-abs(wind)*2 if anim in ('run','attack_1') else 18+wind
    polygon(d,[(30+x,15+y),(26+x,21+y),(bx+x,27+y),(bx-4+x,38+y),(bx+3+x,39+y),(26+x,30+y),(32+x,28+y)],C['hair_shadow'],C['edge'],2)
    polygon(d,[(25+x,21+y),(21+x,27+y),(bx-1+x,35+y),(bx+2+x,32+y),(28+x,25+y)],C['hair'],None)
    seg(d,[(24+x,25+y),(19+x,33+y)],C['hair_light'],1)
    polygon(d,[(22+x,26+y),(19+x,30+y),(16+x,29+y),(18+x,26+y)],C['turquoise'],C['edge2'],1)

    # sword sheathed on the back for regular frames
    if anim not in ('attack_1','meditate'):
        d=ds['weapon_back']
        polygon(d,[(23+x,25+y),(17+x,38+y),(19+x,40+y),(26+x,27+y)],C['sheath'],C['edge'],1)
        seg(d,[(24+x,27+y),(20+x,20+y)],C['gold'],2,C['edge'])
        px(d,19+x,18+y,C['gold_light'],2)
    
    # animated legs, split hip/shin possible layers in modular runtime
    if anim!='meditate':
        d=ds['legs']
        mov=0
        if anim in ('walk','run'):
            mov=round(math.sin(phase)* (4 if anim=='walk' else 6))
        if anim=='jump':mov=[0,-2,-3,-1][t]
        if anim=='attack_1':mov=[0,-2,-2,3,4,0][t]
        if anim=='hurt':mov=[0,3,1][t]
        if anim=='death':mov=[0,2,4][t]
        # each leg dark pixel-shaded, shifts in running animation
        lx=28+x-mov;rx=36+x+mov
        seg(d,[(29+x,45+y),(lx,53+y)],C['pants'],5,C['edge'])
        seg(d,[(37+x,45+y),(rx,53+y)],C['pants'],5,C['edge'])
        seg(d,[(28+x,48+y),(lx,53+y)],C['boot_highlight'],2)
        seg(d,[(36+x,48+y),(rx,53+y)],C['blue_dark'],2)
        ell(d,(lx-5,52+y,lx+3,58+y),C['boot'],C['edge'],1)
        ell(d,(rx-2,52+y,rx+6,58+y),C['boot'],C['edge'],1)
        px(d,lx-3,54+y,C['boot_highlight'],3);px(d,rx,54+y,C['boot_highlight'],3)

    # rear tails of robe, keyed into hip. Offsets respond to run pose
    if anim!='meditate':
        d=ds['robe_back']
        flutter=2 if anim in ('run','attack_1','jump') else 0
        polygon(d,[(26+x,39+y),(39+x,39+y),(43+x,48+y),(39+x,53+y),(32+x,50+y),(24+x,54+y),(21-flutter+x,51+y)],C['robe_shadow'],C['edge'],2)
        polygon(d,[(27+x,40+y),(33+x,41+y),(29+x,52+y),(23-flutter+x,52+y)],C['robe'],C['edge'],1)
        seg(d,[(25+x,49+y),(26+x,52+y)],C['turquoise_dark'],1)
        polygon(d,[(35+x,42+y),(40+x,45+y),(40+x,52+y),(35+x,49+y)],C['robe'],C['blue_dark'],1)
    # robe chest
    d=ds['body']
    polygon(d,[(26+x,29+y),(38+x,29+y),(42+x,39+y),(40+x,45+y),(26+x,46+y),(22+x,38+y)],C['robe'],C['edge'],2)
    polygon(d,[(27+x,29+y),(34+x,31+y),(29+x,39+y),(25+x,35+y)],C['robe_light'],None)
    polygon(d,[(35+x,31+y),(39+x,32+y),(34+x,41+y),(31+x,39+y)],C['robe_shadow'],None)
    polygon(d,[(29+x,30+y),(35+x,36+y),(32+x,39+y),(25+x,33+y)],C['blue'],None)
    seg(d,[(25+x,42+y),(40+x,42+y)],C['belt'],3,C['edge'])
    ell(d,(31+x,40+y,35+x,44+y),C['turquoise'],C['edge'])
    px(d,32+x,41+y,C['white'])
    px(d,39+x,44+y,C['gold'],2)

    # rear arm: separated by angle from side; back sleeve should swing against near arm
    d=ds['back_arm']
    swing = round(math.sin(phase)* (4 if anim=='walk' else 7)) if anim in ('walk','run') else 0
    if anim=='meditate':
        seg(d,[(26+x,34+y),(27+x,43+y),(32+x,46+y)],C['robe_shadow'],7,C['edge'])
        ell(d,(30+x,44+y,34+x,48+y),C['skin'],C['edge'],1)
    else:
        seg(d,[(25+x,34+y),(21+x-swing,42+y)],C['robe_shadow'],8,C['edge'])
        seg(d,[(24+x,35+y),(22+x-swing,40+y)],C['robe_light'],3)
        ell(d,(18+x-swing,39+y,23+x-swing,44+y),C['skin'],C['edge'],1)

    # head/face, slightly larger than body; chin shadow etc
    d=ds['head']
    ell(d,(25+x,13+y,44+x,32+y),C['skin'],C['edge'],2)
    ell(d,(27+x,16+y,41+x,28+y),C['skin_light'])
    ell(d,(41+x,23+y,45+x,27+y),C['skin_shade'],C['edge'],1)
    # face eyes facing right
    if anim in ('meditate','death') or (anim=='idle' and t==2):
        seg(d,[(35+x,23+y),(39+x,23+y)],C['edge'],1)
    elif anim=='hurt' and t==1:
        seg(d,[(35+x,22+y),(40+x,25+y)],C['edge'],2)
    else:
        ell(d,(36+x,21+y,39+x,26+y),C['edge'])
        px(d,38+x,22+y,C['eye_light'])
        seg(d,[(35+x,20+y),(40+x,20+y)],C['hair_shadow'],1)
    px(d,41+x,27+y,C['skin_shade'])
    px(d,43+x,28+y,C['skin_shade'])
    # scalp/topknot/hair, standard hair front modular layer
    d=ds['front_hair']
    ell(d,(24+x,10+y,44+x,23+y),C['hair'],C['edge'],2)
    polygon(d,[(23+x,16+y),(29+x,13+y),(33+x,15+y),(38+x,15+y),(33+x,23+y),(28+x,25+y),(24+x,26+y)],C['hair'],C['edge'],1)
    polygon(d,[(37+x,13+y),(44+x,16+y),(46+x,21+y),(42+x,22+y),(39+x,19+y),(34+x,19+y)],C['hair'],C['edge'],1)
    seg(d,[(28+x,14+y),(34+x,12+y),(40+x,15+y)],C['hair_light'],1)
    # bun and hairpin
    ell(d,(28+x,5+y,39+x,15+y),C['hair_shadow'],C['edge'],2)
    ell(d,(30+x,7+y,38+x,13+y),C['hair'],None)
    polygon(d,[(28+x,13+y),(38+x,11+y),(38+x,14+y),(29+x,16+y)],C['turquoise_dark'],C['edge'],1)
    px(d,33+x,12+y,C['gold_light'],2)
    px(d,31+x,8+y,C['hair_light'],2)
    # ribbon
    polygon(d,[(29+x,14+y),(24+x,17+y),(23+x,20+y),(27+x,18+y)],C['turquoise'],C['edge'],1)
    
    # front arm, battle / skill changes
    d=ds['front_arm']
    hand=(43+x,42+y)
    if anim=='attack_1':
        hand=[(43+x,40+y),(44+x,36+y),(47+x,33+y),(48+x,30+y),(48+x,33+y),(44+x,41+y)][t]
        seg(d,[(39+x,35+y),hand],C['robe'],7,C['edge'])
        seg(d,[(39+x,35+y),hand],C['robe_shadow'],3)
    elif anim=='cast':
        hand=[(43+x,39+y),(45+x,34+y),(47+x,31+y),(47+x,31+y),(47+x,33+y),(43+x,40+y)][t]
        seg(d,[(38+x,34+y),hand],C['robe'],7,C['edge'])
        seg(d,[(40+x,35+y),hand],C['robe_shadow'],3)
    elif anim=='meditate':
        hand=(36+x,46+y)
        seg(d,[(39+x,36+y),(41+x,43+y),hand],C['robe'],7,C['edge'])
        seg(d,[(37+x,41+y),hand],C['robe_shadow'],2)
    elif anim=='hurt':
        hand=(45+x,38+y) if t==1 else (43+x,43+y)
        seg(d,[(39+x,34+y),hand],C['robe'],8,C['edge'])
    else:
        sw=round(math.sin(phase)* (4 if anim=='walk' else 7)) if anim in ('walk','run') else 0
        hand=(42+x+sw,42+y-sw//2)
        seg(d,[(39+x,35+y),(40+x+sw,40+y-sw//2),hand],C['robe'],8,C['edge'])
        seg(d,[(40+x,36+y),(41+x+sw,39+y-sw//2)],C['robe_shadow'],2)
    ell(d,(hand[0]-2,hand[1]-2,hand[0]+2,hand[1]+2),C['skin'],C['edge'],1)
    # wrist blue piping and jade cuff
    px(d,hand[0]-4,hand[1]-3,C['turquoise'],2)

    if anim=='attack_1':
        d=ds['weapon_front']
        # sword orientation varies; blade drawn by line + pommel not each image unique shape
        offsets=[(-2,11),(2,-11),(9,-2),(16,-5),(14,6),(0,10)]
        ex=hand[0]+offsets[t][0];ey=hand[1]+offsets[t][1]
        seg(d,[(hand[0]-2,hand[1]+2),(hand[0]+1,hand[1]-1)],C['gold'],2,C['edge'])
        seg(d,[hand,(ex,ey)],C['blade_shadow'],4,C['edge'])
        seg(d,[(hand[0]+1,hand[1]-1),(ex,ey)],C['blade'],2)
        px(d,hand[0]-1,hand[1]+1,C['gold_light'],2)
        if t in (2,3,4):
            q=ds['front_fx']
            if t==2:
                seg(q,[(51+x,23+y),(57+x,27+y),(61+x,32+y)],'#78dbf8',2)
            if t==3:
                seg(q,[(49+x,20+y),(58+x,24+y),(61+x,32+y),(58+x,41+y)],'#62bff7',3)
                seg(q,[(50+x,20+y),(59+x,25+y),(62+x,32+y)],'#e4ffff',1)
            if t==4:
                seg(q,[(50+x,34+y),(58+x,36+y),(61+x,42+y)],'#67d4f5',2)
    if anim=='cast':
        d=ds['front_fx']
        if t in (1,2,3,4):
            r=[3,5,7,5][t-1]
            cx=53+x;cy=31+y
            ell(d,(cx-r,cy-r,cx+r,cy+r),None,C['aura2'],2)
            ell(d,(cx-r+2,cy-r+2,cx+r-2,cy+r-2),C['aura'],C['white'])
            if t>=2:
                for px_x,px_y in [(cx-r-3,cy-3),(cx+r+1,cy+1),(cx-2,cy-r-3)]:px(d,px_x,px_y,C['white'],2)
    if anim=='meditate':
        d=ds['back_fx']; cyc=[0,1,2,1][t]
        ell(d,(16,55,48,61),None,C['aura2'],1)
        if cyc:
            ell(d,(20,56,44,60),None,C['turquoise'],1)
            px(d,19,42-cyc*2,C['aura'],2);px(d,46,36+cyc*2,C['aura'],2)
    if anim=='fly_sword':
        d=ds['back_fx']
        polygon(d,[(14,59),(46,59),(59,57),(46,56),(14,56),(7,58)],C['aura2'],C['edge2'],1)
        seg(d,[(12,57),(54,57)],C['blade'],2)
        px(d,14,61,C['aura'],2);px(d,40,61,C['aura'],2)
    if anim=='hurt' and t==1:
        d=ds['front_fx'];seg(d,[(48,20),(51,16)],C['gold_light'],2);seg(d,[(46,16),(51,22)],C['gold_light'],2)
    if anim=='land' and t==1:
        d=ds['front_fx'];seg(d,[(12,57),(17,54)],C['robe_shadow'],1);seg(d,[(46,54),(52,57)],C['robe_shadow'],1)
    result=layer()
    for name in ls:result.alpha_composite(ls[name])
    return result,ls

configs={
 'idle':{'frames':4,'fps':5,'loop':True},
 'walk':{'frames':6,'fps':9,'loop':True},
 'run':{'frames':6,'fps':12,'loop':True},
 'jump':{'frames':4,'fps':8,'loop':False},
 'land':{'frames':3,'fps':9,'loop':False},
 'attack_1':{'frames':6,'fps':12,'loop':False,'hit_frame':3},
 'cast':{'frames':6,'fps':9,'loop':False,'release_frame':3},
 'hurt':{'frames':3,'fps':8,'loop':False},
 'death':{'frames':5,'fps':6,'loop':False},
 'meditate':{'frames':4,'fps':5,'loop':True},
 'fly_sword':{'frames':4,'fps':6,'loop':True}
}

sheet=layer(); sheet=Image.new('RGBA',(6*64,len(configs)*64),(0,0,0,0))
frame_meta={};previews=[]
for row,(name,cfg) in enumerate(configs.items()):
    animdir=OUT/'frames'/name;animdir.mkdir(parents=True,exist_ok=True)
    strip=Image.new('RGBA',(cfg['frames']*SZ,SZ),(0,0,0,0))
    frames=[]
    for i in range(cfg['frames']):
        img,layers=render(name,i)
        img.save(animdir/f'{i:02d}.png')
        strip.alpha_composite(img,(i*SZ,0))
        sheet.alpha_composite(img,(i*SZ,row*SZ))
        frames.append(img)
        frame_meta[f'{name}_{i:02d}']={'frame':{'x':i*64,'y':row*64,'w':64,'h':64},'duration':int(1000/cfg['fps']),'sourceSize':{'w':64,'h':64},'anchor':{'x':32,'y':58}}
    strip.save(OUT/'animations'/f'{name}.png') if (OUT/'animations').is_dir() else None
    (OUT/'animations').mkdir(exist_ok=True)
    strip.save(OUT/'animations'/f'{name}.png')
    # Save gif, repeat in loop for user inspection
    rendered=[]
    for frame in frames:
        bg=Image.new('RGBA',(64,64),'#e3efe9')
        for iy in range(0,64,8):
            for ix in range(0,64,8):
                if (ix//8+iy//8)%2==0:ImageDraw.Draw(bg).rectangle((ix,iy,ix+7,iy+7),fill='#d3e4dc')
        bg.alpha_composite(frame)
        rendered.append(bg.convert('RGB').resize((256,256),Image.Resampling.NEAREST))
    (OUT/'gifs').mkdir(exist_ok=True)
    rendered[0].save(OUT/'gifs'/f'{name}.gif',save_all=True,append_images=rendered[1:],duration=max(65,int(1000/cfg['fps'])),loop=0,optimize=False)

sheet.save(OUT/'spritesheet.png')
metadata={
 'format':'spritesheet v1','character':'Kiem Tu - Chibi Tien Hiep','size':{'width':64,'height':64},
 'direction':'right (flip_h to face left)','coordinate_origin':'top-left',
 'root_anchor':{'x':32,'y':58},
 'sheet':'spritesheet.png','sheet_columns':6,'sheet_rows':len(configs),
 'animations':{n:{**cfg,'sheet_row':idx,'strip':f'animations/{n}.png','frame_path_pattern':f'frames/{n}/%02d.png'} for idx,(n,cfg) in enumerate(configs.items())},
 'frames':frame_meta,
 'important':'Prototype pixel sprites made at 64x64; per-frame alignment and transparent BG are fixed. Adjust fps or pivots to your engine.'}
(OUT/'metadata.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
# export modular still pose 0 as separately aligned layers, serves as a basis for swapping future gear
mod=OUT/'modular_parts_idle';mod.mkdir(exist_ok=True)
_,md=render('idle',0)
for nam,im in md.items():
    if im.getbbox():im.save(mod/f'{nam}.png')
(OUT/'README.md').write_text('''# KIẾM TU – Sprite Test Pack (64×64)

Bộ sprite prototype 2D chibi tu tiên Trung Quốc. Nhân vật nhìn **phải**, nền **trong suốt**. Các frame có đúng kích thước **64 × 64 px**. Không phải bộ art production hoàn chỉnh.

## File
- `spritesheet.png`: atlas 6 cột × 11 hàng; mỗi ô 64×64.
- `metadata.json`: số frame, FPS, tọa độ trong atlas, lặp/không lặp, điểm neo.
- `animations/*.png`: mỗi animation là một strip PNG ngang, frame 64×64.
- `frames/<animation>/*.png`: từng frame rời PNG trong suốt.
- `gifs/*.gif`: GIF phóng to để kiểm tra.
- `modular_parts_idle/*.png`: các layer riêng ở tư thế đứng, cùng canvas 64×64 (ảnh mẫu cho việc thay trang bị sau này).

## Godot 4
1. Import một file `animations/idle.png`.
2. Thêm `AnimatedSprite2D` → `SpriteFrames` → Add animation tên `idle`.
3. Add frames from Sprite Sheet, cài `Horizontal=4`, `Vertical=1` với `idle` (animation khác có số frame riêng).
4. Chọn FPS từ `metadata.json`. Chọn Loop cho các animation có `loop=true`.
5. Đặt `AnimatedSprite2D.offset = Vector2(0, -26)` nếu muốn chân nhân vật nằm đúng vị trí Node2D ban đầu, do neo ảnh ở (32,58) và tâm ảnh ở (32,32). Hoặc điều chỉnh parent Sprite theo scene.
6. Nhìn sang trái: `AnimatedSprite2D.flip_h = true`.
7. `attack_1` có `hit_frame=3`: dùng marker thời điểm ra đòn **chỉ cho hiệu ứng**; server mới quyết định sát thương thực tế.

## Lưu ý
- Các `modular_parts_idle` cần composite theo đúng thứ tự từ metadata logic: `back_fx`, `back_hair`, `weapon_back`, `legs`, `robe_back`, `body`, `back_arm`, `head`, `front_hair`, `front_arm`, `weapon_front`, `front_fx`.
- Các animation phù hợp để **test cảm giác điều khiển và chức năng**, còn art cần polish trước khi phát hành.
- Có thể sửa/thiết kế lại sprite từ mã nguồn `build_tutien_sprites.py` đính kèm trong file ZIP.
''',encoding='utf-8')

# Preview high-res, grid and labels using installed sans fonts
try:ft=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',18)
except:ft=ImageFont.load_default()
scale=3
left=190; top=70; rowh=64*scale+18; gap=14
P=Image.new('RGB',(left+6*64*scale+50,top+len(configs)*rowh+35),'#142238')
dp=ImageDraw.Draw(P)
dp.text((20,18),'KIEM TU - sprite test 64x64 | 11 animations',fill='#e8f5ff',font=ft)
for row,(name,cfg) in enumerate(configs.items()):
    y0=top+row*rowh
    dp.text((20,y0+75),f'{name}  ({cfg["frames"]})',fill='#c6e5f2',font=ft)
    for f in range(cfg['frames']):
        x0=left+f*64*scale
        for yy in range(0,64,8):
            for xx in range(0,64,8):
                dp.rectangle((x0+xx*scale,y0+yy*scale,x0+(xx+8)*scale-1,y0+(yy+8)*scale-1),fill=('#e4eee9' if (xx//8+yy//8)%2==0 else '#cbded4'))
        frame=Image.open(OUT/'frames'/name/f'{f:02d}.png').resize((64*scale,64*scale),Image.Resampling.NEAREST)
        P.paste(frame,(x0,y0),frame)
        dp.text((x0+4,y0+64*scale+1),str(f),fill='#e1edf5')
P.save(OUT/'preview.png')
# copy source into pack
import shutil
shutil.copy('/mnt/data/build_tutien_sprites.py',OUT/'build_tutien_sprites.py')
zip_path=Path('/mnt/data/kiem_tu_sprite_test_64x64.zip')
with zipfile.ZipFile(zip_path,'w',zipfile.ZIP_DEFLATED) as z:
    for f in OUT.rglob('*'):
        if f.is_file():z.write(f,Path('kiem_tu_sprite_test')/f.relative_to(OUT))
print('CREATED:',zip_path,zip_path.stat().st_size)
print('PREVIEW:',OUT/'preview.png', 'SHEET:',OUT/'spritesheet.png', 'SHEET_SIZE:',sheet.size)
print('ANIMATIONS:',len(configs),'FRAMES:',sum(c['frames'] for c in configs.values()))
