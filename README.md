# Tam Tộc: Nghịch Thiên

Web MMORPG 2D tu tiên, bản MVP gồm các vertical slice Phase 1–4. Client TypeScript + Phaser 3 + Vite; backend Java 21 + Spring Boot, Spring Security, JPA, WebSocket; PostgreSQL + Flyway. Không có chế độ offline hay backend giả.

## Chạy bằng Docker (khuyến nghị)

Cần Docker Desktop đang chạy và Node.js 22.12+ hoặc 24.

Từ thư mục gốc, mở PowerShell:

```powershell
.\scripts\init-local.ps1
docker compose up -d --build
cd frontend
npm ci
npm run dev
```

Mở **http://127.0.0.1:5173/**. Backend: http://127.0.0.1:8080/api/health. PostgreSQL: localhost:55432; bên trong Compose dùng db:5432. Mật khẩu và JWT secret được tạo ngẫu nhiên trong `.env`; mẫu biến ở `.env.example`. Không ghi đè secret của database đã có dữ liệu.

Vite proxy `/api` và `/ws` tới Spring Boot. Dùng HTTPS/WSS và cấu hình Origin chính xác nếu triển khai công khai; bản này mặc định chỉ bind cổng Docker vào loopback. Sites hosting không được dùng vì backend cần JVM và PostgreSQL.

## Chạy backend để phát triển

Cần JDK 21 (hoặc JDK hỗ trợ `--release 21`), Maven 3.6.3+ và PostgreSQL Docker:

```powershell
docker compose up -d db
# Nếu backend Compose đang chạy: docker compose stop backend
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\scripts\start-backend.ps1
```

Script nạp `.env`, chọn profile `production` mặc định. Trong workspace hiện tại, script cũng nhận Maven đã tải ở `.tools/apache-maven-3.9.11`; thư mục này không phải source được quản lý. Trên máy khác, cài Maven vào PATH. Frontend vẫn chạy `npm run dev` trong cửa sổ khác.

## Bắt đầu chơi

1. Đăng ký tài khoản: 3–24 chữ ASCII/số/gạch dưới; mật khẩu 10–64 ký tự.
2. Tạo nhân vật bằng **tên và tộc**. Không có lựa chọn nghề, thế lực hay hệ tu luyện ở bước này. Nhân vật bắt đầu cấp 1, EXP 0, Luyện Khí Sơ Kỳ, NOVICE, tổ chức/path null; có Mộc Kiếm, Áo Vải và 3 Hồi Linh Đan.
3. Nhân Tộc ở Thanh Vân Thành, Ma Tộc ở U Minh Thành, Yêu Tộc ở Vạn Yêu Lâm. Các khu có cổng tím tới Trung Châu; ba tộc gặp nhau tại bản đồ chung này.
4. Di chuyển tới **Tiếp Dẫn Sứ ở x=300**. Mở Nhiệm vụ để nhận “Mối họa Yêu Lang”; chỉ những quái hạ sau khi nhận mới được tính. Hoàn thành 5 Yêu Lang rồi lĩnh thưởng.
5. Khu bên trái x=420 là khu an toàn, hồi HP. Quái từ x≈800. Cùng gây sát thương lên quái sẽ nhận credit khi ở cùng map, còn sống và gần mục tiêu lúc quái chết.
6. Nhặt khối vuông vật phẩm bằng E; mở túi để sử dụng/trang bị/tháo. Tu vi dùng trong bảng Tu luyện; cấp độ và cảnh giới là hai tiến trình riêng. MVP hỗ trợ tới Trúc Cơ Viên Mãn.
7. Cấp 10 mở bảng Thế lực. Nhận “Con Đường Tu Luyện” tại NPC, chọn khảo hạch thuộc tộc, hạ 3 quái mới, trở lại NPC rồi **xác nhận gia nhập**. Có 9 thế lực dùng chung 3 archetype và một Combat Engine. Không có chuyển thế lực trong MVP.

Điều khiển:

- A/D hoặc ←/→: di chuyển; Space: nhảy.
- J: đánh thường; K: Linh Khí Kích; Shift: Thân Pháp.
- L: kỹ năng tộc; U: kỹ năng thế lực sau khi mở khóa.
- E: nhặt đồ, nói chuyện NPC hoặc dùng cổng khi ở gần.
- I: túi đồ; Q: nhiệm vụ; C: nhân vật; M: bản đồ; Enter: focus chat.
- Có thể bấm nút kỹ năng trên HUD. Chat chỉ gửi trong cùng map.

Giao diện chơi phủ toàn bộ khung trình duyệt: HP/MP góc trên trái, khu vực và minimap ở phía trên, kỹ năng sát cạnh dưới. Nhiệm vụ, hành trang, kỹ năng, tu luyện và chức năng mở trong ngăn bên trái có tab chuyển nhanh; bản đồ vẫn hiển thị phía sau. Dùng nút × hoặc Escape để đóng bảng. Hành trang chia rõ 6 ô trang bị trên người và vật phẩm trong túi; linh thạch hiển thị ở chân bảng từ dữ liệu server.

Mở hai trình duyệt hoặc hai cửa sổ riêng và đăng nhập hai tài khoản để kiểm tra multiplayer. Không cho hai kết nối điều khiển cùng một nhân vật. Khi mất mạng, rời thế giới rồi vào lại để lấy vé mới.

## Kiểm thử

Backend unit tests:

```powershell
cd backend
mvn test
```

Build client:

```powershell
cd frontend
npm run build
```

Integration tests dùng **REST, WebSocket và PostgreSQL thật**, không mock. Khởi động backend trước; đóng các client game đang chơi để kiểm thử đếm số người trong map ổn định. Mỗi lần chạy tạo tài khoản/nhân vật test mới và giữ dữ liệu để điều tra.

```powershell
.\scripts\test-integration.ps1
```

Bao phủ đăng ký/đăng nhập sai mật khẩu, ownership, không nhận level từ client, vé dùng một lần, kết nối trùng, hai chiều di chuyển qua map chung, chat, ngắt kết nối, lưu vị trí, HP quái chung, EXP đóng góp, khóa skill, cooldown và quái hồi sinh.

Với backend Docker mặc định, chạy thêm `node tests/production.mjs` để kiểm tra endpoint dev vắng mặt, chữ ký JWT và Origin. Kết quả thực thi và các giới hạn kiểm thử ghi trong `docs/VERIFICATION.md`.

Để kiểm thử Phase 3–4 nhanh, dừng backend Compose rồi chạy backend host bằng:

```powershell
.\scripts\start-backend.ps1 -EnableDevTools
# Cửa sổ khác:
.\scripts\test-integration.ps1 -IncludeProgression
```

Endpoint `POST /api/dev/characters/{id}/level10` chỉ tồn tại với profile `local`, yêu cầu JWT của chủ nhân, request loopback và header `X-Dev-Key` khớp `DEV_ADMIN_KEY` riêng trong `.env`. Không có nút cấp level trên client; người chơi thông thường không sử dụng được. Profile mặc định/Compose không bật endpoint này. Công cụ chỉ cấp level 10; khảo hạch và nhiệm vụ trong test vẫn dùng quái thật.

## Cấu trúc

```text
backend/src/main/java/vn/tamtoc/
  account/       tài khoản, đăng nhập, JWT
  security/      CORS, BCrypt, rate limit
  character/     entity JPA, ownership, API tạo nhân vật
  realtime/      vé ngắn hạn, xác thực socket, protocol
  world/         realtime state, tick loop, vật lý, map, loot
  combat/        Skill Engine, Monster AI, trạng thái quái
  progression/   nhiệm vụ, túi đồ, cảnh giới, thế lực, persistence
backend/src/main/resources/
  db/migration/  schema Flyway
  maps.json      map, địa hình, cổng
  combat.json    skill, quái, boss
  progression.json  nhiệm vụ, vật phẩm, 9 thế lực, cấp yêu cầu
frontend/src/
  api.ts, network.ts   REST và native WebSocket
  game.ts             Phaser scene, VisualComponent, nội suy
  game-ui.ts, progression-ui.ts  HUD và các bảng tương tác
tests/                 integration tests Node.js 22+
scripts/               tạo env, chạy server, kiểm thử
```

Client gửi ý định `INPUT {direction,jump,sequence}`, không gửi tọa độ chính thức. Server chạy tick 20Hz, broadcast 10Hz theo map; sửa trong `application.yml`. Client nội suy dữ liệu server, chưa có prediction/reconciliation. Hình học nằm trong VisualComponent/scene, không dùng sprite; thay sprite không làm đổi gameplay server.

PostgreSQL lưu users, characters và character_progress. Progress aggregate chứa inventory/equipment, quest status/counters và cooldown trong tài liệu JSON có kiểm soát server; không có API cập nhật tùy ý aggregate. Một cột organization duy nhất trên nhân vật bảo đảm không có hai thế lực chính. Tất cả thao tác gameplay dùng chung khóa world, và cập nhật character/progress nằm cùng transaction. Snapshot bất biến được ghi tuần tự lúc hạ quái, đổi map, disconnect, định kỳ 15 giây và shutdown; không ghi DB mỗi tick. REST thay đổi tiến trình đợi commit rồi phản hồi. Crash đột ngột có thể mất phần thay đổi từ checkpoint gần nhất.

Vé WebSocket: REST có JWT và ownership → vé 30 giây → message AUTH đầu tiên. Vé dùng một lần, không đặt access token vào URL. Socket chưa xác thực hết hạn sau 10 giây, giới hạn message/kích thước; HTTP auth có rate limit. Server kiểm tra MP, hồi chiêu, mở khóa skill, tầm đánh, sát thương, quest và điều kiện tổ chức.

## Giới hạn hiện tại

Đây là MVP một tiến trình server, chưa benchmark tải MMO. Chưa có client prediction, reconnect tự động, refresh token, chiến đấu PvP, tổ đội, bang hội, giao dịch hay phó bản. Chưa có cast-time khác 0, projectile mô phỏng riêng hoặc hệ status effect tổng quát; kỹ năng hiện tại dùng đòn cone/area, heal, dash, shield, empower và slow. Nội dung quest hiện tại là nhiệm vụ Yêu Lang và chuỗi gia nhập thế lực; item rơi hiện có nanh và đan dược. NPC và vị trí spawn quái của MVP còn cấu hình trong source world. Các giới hạn này thuộc phạm vi mở rộng sau khi nền tảng được kiểm thử ổn định, không được thay bằng chức năng giả.

Dừng dịch vụ bằng `docker compose stop`. Dữ liệu nằm trong volume `pgdata`; giữ volume khi khởi động lại.
