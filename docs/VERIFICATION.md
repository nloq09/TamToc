# Kết quả kiểm thử — 27/10/2026 (Asia/Bangkok)

Các kiểm tra dưới đây đã được thực thi trong workspace, không chỉ viết test.

- `npm run build`: TypeScript và Vite build thành công. Bundle JS khoảng 1.24 MB trước gzip, gồm Phaser.
- `mvn test`: 12 test, 0 lỗi, 0 thất bại; movement/collision, combat/cooldown/unlock, AI boss, quest rewards, inventory, 9 thế lực/3 archetype, realm cap.
- `docker compose build backend`: thành công; Maven chạy lại 12 test trong môi trường Java 21 trước khi đóng gói JRE image.
- `docker compose up -d backend`: backend Docker chạy với PostgreSQL 17; Flyway validation thành công trên dữ liệu đã tạo trước đó.
- `tests/multiplayer.mjs`: thành công trên backend host và backend Docker; hai tài khoản độc lập, ownership, tạo nhân vật, chặn payload chỉnh level, vé một lần, chống kết nối trùng, map/cổng, di chuyển hai chiều, chặn tọa độ, chat, disconnect, lưu vị trí.
- `tests/combat.mjs`: thành công trên host và Docker; HP quái dùng chung, sát thương server, EXP đóng góp cho hai người, hồi chiêu, skill chưa mở khóa, hồi sinh quái.
- `tests/progression.mjs`: thành công trên backend profile local với khóa quản trị riêng; NPC, 5 kill thật, nhận thưởng đúng một lần, loot, equip/unequip, đột phá, khảo hạch 3 kill, gia nhập Thanh Vân Kiếm Tông, mở Kiếm Khí Trảm, chặn thế lực sai tộc và thành viên thứ hai, lưu tiến trình và cooldown qua reconnect.
- `tests/production.mjs`: thành công trên backend Docker mặc định; endpoint dev trả 404 khi có JWT hợp lệ; JWT sửa đổi trả 401; CORS HTTP và WebSocket Origin lạ trả 403.
- Kiểm tra thêm trên dữ liệu tài khoản do test progression tạo: sau khi dừng backend host và khởi động backend Docker, realm stage, Kiếm Tu, linh kiếm đang trang bị, linh thạch và hai nhiệm vụ đã lĩnh thưởng vẫn đúng trong cùng PostgreSQL volume.
- Browser QA: đăng ký, tạo nhân vật chỉ có tên/tộc, vào Phaser world, hiển thị NPC/quái/HUD/kỹ năng; bảng túi đồ tháo kiếm làm Công 4 → 0 và có thể trang bị lại; bảng nhiệm vụ, khóa nút thế lực dưới cấp yêu cầu. Không có JavaScript error trong lần đọc console cuối.

Unit test chạy host bằng JDK 25 với compiler release 21; Docker build/test và runtime dùng Java 21.

Chưa thực hiện benchmark tải, kiểm thử hai cửa sổ trình duyệt đồng thời hoặc kiểm thử public HTTPS/WSS. Multiplayer được kiểm chứng bằng hai client WebSocket thật trong integration test; browser QA dùng một tab. Chưa có khẳng định sẵn sàng vận hành MMO công khai.

Tài khoản test được giữ trong database để kiểm tra persistence. Tất cả mật khẩu test chỉ dùng cho dữ liệu kiểm thử local; secret vận hành nằm trong `.env` bị gitignore.

## Cập nhật bố cục theo ảnh tham khảo

Client đã build thành công sau thay đổi bố cục. Browser QA xác nhận ngăn bên trái mở/đóng, chuyển tab hành trang/kỹ năng/chức năng, tháo và trang bị lại kiếm, khóa thế lực theo cấp từ server, thu gọn/mở chat. Thanh nhiệm vụ hiển thị tiến độ thật; camera dịch để nhân vật vẫn nằm trong phần bản đồ còn nhìn thấy khi mở bảng. Backend không thay đổi trong lần cập nhật này.
