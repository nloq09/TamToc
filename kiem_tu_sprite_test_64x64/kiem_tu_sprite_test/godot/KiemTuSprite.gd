extends AnimatedSprite2D
## Attach to an AnimatedSprite2D inside your player scene.
## Keep this pack at res://kiem_tu_sprite_test/ or edit sprites_path.
@export var sprites_path: String = "res://kiem_tu_sprite_test/animations/"

const ANIMATIONS = {
    "idle": [4, 5.0, true],
    "walk": [6, 9.0, true],
    "run": [6, 12.0, true],
    "jump": [4, 8.0, false],
    "land": [3, 9.0, false],
    "attack_1": [6, 12.0, false],
    "cast": [6, 9.0, false],
    "hurt": [3, 8.0, false],
    "death": [5, 6.0, false],
    "meditate": [4, 5.0, true],
    "fly_sword": [4, 6.0, true],
}

func _ready() -> void:
    sprite_frames = SpriteFrames.new()
    for name in ANIMATIONS:
        var source: Texture2D = load(sprites_path + name + ".png")
        if source == null:
            push_warning("Missing sprite animation: " + name)
            continue
        var config: Array = ANIMATIONS[name]
        sprite_frames.add_animation(name)
        sprite_frames.set_animation_speed(name, config[1])
        sprite_frames.set_animation_loop(name, config[2])
        for i in range(config[0]):
            var piece := AtlasTexture.new()
            piece.atlas = source
            piece.region = Rect2(i * 64, 0, 64, 64)
            sprite_frames.add_frame(name, piece)
    offset = Vector2(0, -26) # ground pivot = (32, 58) in a 64x64 frame
    animation = "idle"
    play()

func face_direction(direction: float) -> void:
    if direction != 0:
        flip_h = direction < 0
