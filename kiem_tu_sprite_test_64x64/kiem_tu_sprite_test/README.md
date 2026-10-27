# KIẾM TU – Sprite Test Pack (64×64)

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

## Xem thử không cần engine
Mở `demo.html` bằng Chrome/Edge sau khi giải nén. Có thể dùng phím A/D, Shift, Space, J, K, M, F, H, X, R. Không cần mạng Internet.

## Import nhanh sang Godot 4
Copy toàn bộ thư mục `kiem_tu_sprite_test` vào thư mục project Godot (`res://kiem_tu_sprite_test/`).
Tạo một `AnimatedSprite2D`, attach script `godot/KiemTuSprite.gd` và chạy scene. Script tự chia các strip 64×64 và cấu hình FPS/loop. Đổi hướng bằng `flip_h` hoặc `face_direction()`.
