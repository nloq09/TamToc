# Tam Tộc: Nghịch Thiên

Web MMORPG 2D tu tiên, bản MVP Phase 1–4 được mở rộng multiplayer trong Phase 6. Client TypeScript + Phaser 3 + Vite; backend Java 21 + Spring Boot, Spring Security, JPA, WebSocket; PostgreSQL + Flyway. Không có chế độ offline hay backend giả.

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
- Có thể bấm nút kỹ năng trên HUD. Chat Khu vực gửi trong cùng map/instance; kênh Tổ đội chỉ tới thành viên.

Giao diện chơi phủ toàn bộ khung trình duyệt: HP/MP góc trên trái, khu vực và minimap ở phía trên, kỹ năng sát cạnh dưới. Nhiệm vụ, hành trang, kỹ năng, tu luyện và chức năng mở trong ngăn bên trái có tab chuyển nhanh; bản đồ vẫn hiển thị phía sau. Dùng nút × hoặc Escape để đóng bảng. Hành trang chia rõ 6 ô trang bị trên người và vật phẩm trong túi; linh thạch hiển thị ở chân bảng từ dữ liệu server.

Mở hai trình duyệt hoặc hai cửa sổ riêng và đăng nhập hai tài khoản để kiểm tra multiplayer. Không cho hai kết nối điều khiển cùng một nhân vật. Mất mạng ngoài ý muốn sẽ tự xin vé mới và nối lại; grace mặc định 20 giây. Trong Chức năng có nút thử ngắt mạng 7 giây (chỉ localhost). Khi refresh, tab giữ khóa phục hồi trong sessionStorage; Rời thế giới sẽ xóa khóa.

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
  party/         lời mời, quyền trưởng đội, chia ngân sách thưởng
  instance/      registry theo instanceId, lịch sử và ledger DB
  dungeon/       ready, 3 wave, wipe, thưởng và cleanup
  duel/          lời thách đấu, countdown, kết quả 1v1
backend/src/main/resources/
  db/migration/  schema Flyway
  maps.json      map, địa hình, cổng
  combat.json    skill, quái, boss
  progression.json  nhiệm vụ, vật phẩm, 9 thế lực, cấp yêu cầu
frontend/src/
  api.ts, network.ts   REST và native WebSocket
  game.ts             Phaser scene, nội suy, input và hiệu ứng
  player-visual.ts    VisualComponent và trạng thái animation nhân vật
  sprite-pack.ts      nạp atlas, đăng ký animation theo metadata
  game-ui.ts, progression-ui.ts  HUD và các bảng tương tác
  social-ui.ts, activity-ui.ts, duel-ui.ts  tổ đội/phó bản/đấu trường
tests/                 integration tests đã kiểm tra trên Node.js 24
scripts/               tạo env, chạy server, kiểm thử
```

Client gửi ý định `INPUT {direction,jump,sequence}`, không gửi tọa độ chính thức. Server chạy tick 20Hz, broadcast 10Hz theo (mapId, instanceId); sửa trong `application.yml`. Client nội suy dữ liệu server, chưa có prediction/reconciliation. Nhân vật đang thử bộ sprite Kiếm Tu 64×64 do người dùng cung cấp, nạp từ `frontend/public/assets/kiem-tu`; animation/FPS/điểm neo lấy từ metadata gốc. Ba tộc dùng chung hình mẫu, màu dưới chân phân biệt tộc. NPC, quái và địa hình vẫn là hình học. VisualComponent phản ánh snapshot và sự kiện chiến đấu thật; animation không tính sát thương hay thay đổi vị trí server. Các animation ngồi thiền/phi kiếm chưa gắn với gameplay. Nếu asset không tải được, nhân vật dùng hình khối dự phòng.

PostgreSQL lưu users, characters và character_progress. Progress aggregate chứa inventory/equipment, quest status/counters và cooldown trong tài liệu JSON có kiểm soát server; không có API cập nhật tùy ý aggregate. Một cột organization duy nhất trên nhân vật bảo đảm không có hai thế lực chính. Tất cả thao tác gameplay dùng chung khóa world, và cập nhật character/progress nằm cùng transaction. Snapshot bất biến được ghi tuần tự lúc hạ quái, đổi map, disconnect, định kỳ 15 giây và shutdown; không ghi DB mỗi tick. REST thay đổi tiến trình đợi commit rồi phản hồi. Crash đột ngột có thể mất phần thay đổi từ checkpoint gần nhất.

Vé WebSocket: REST có JWT và ownership → vé 30 giây → message AUTH đầu tiên. Vé dùng một lần, không đặt access token vào URL. Socket chưa xác thực hết hạn sau 10 giây, giới hạn message/kích thước; HTTP auth có rate limit. Server kiểm tra MP, hồi chiêu, mở khóa skill, tầm đánh, sát thương, quest và điều kiện tổ chức.

## Giới hạn hiện tại

Đây là bản phát triển một tiến trình server. Phase 7 thêm map data, quái có FSM, NPC AI và AOI/delta; Phase 6 cung cấp reconnect, tổ đội, phó bản riêng và duel 1v1; phép đo tải local và phạm vi kịch bản ghi trong docs/PHASE6_VERIFICATION.md. Chưa có client prediction, refresh token, bang hội, giao dịch hoặc Tam Tộc Chiến. Party/instance/session đang ở RAM: khởi động lại server xóa trạng thái tạm, đưa nhân vật về map chung đã lưu; lượt phó bản ACTIVE của node được đánh dấu FAILED, không tự thưởng. Duel bị gián đoạn do server restart chưa có cơ chế tiếp tục trận/lưu trận đang diễn ra. Chưa có cast-time khác 0, projectile mô phỏng riêng hoặc hệ status effect tổng quát; kỹ năng hiện tại dùng đòn cone/area, heal, dash, shield, empower và slow. Quest gồm Yêu Lang, chuỗi gia nhập thế lực và bảy nhiệm vụ theo vùng cấp 1–10; item có trang bị, đan dược và nguyên liệu/quest metadata. Map, cổng, spawn, NPC/thoại, behavior và drop được cấu hình JSON, kiểm tra khi startup. Navigation dùng graph designer; không tự sinh đường đi cho mọi địa hình. TRAINER/ESCORT/SCOUT chưa có. Có thể thấy WARN khi gửi vào socket vừa đóng đồng thời; cleanup vẫn chạy, cần cải thiện transport khi mở rộng. Các giới hạn này thuộc phạm vi mở rộng sau khi nền tảng được kiểm thử ổn định, không được thay bằng chức năng giả.

Dừng dịch vụ bằng `docker compose stop`. Dữ liệu nằm trong volume `pgdata`; giữ volume khi khởi động lại.


## Chơi Phase 6

- Chức năng → Tổ đội: mời đạo hữu trong cùng khu vực, nhận/từ chối lời mời. Tối đa 4 người, cho phép ba tộc cùng đội; trưởng đội được đuổi/nhường quyền. Lời mời hết hạn sau 60 giây. Cả đội thấy HP/MP, map và trạng thái nối lại.
- EXP/tu vi từ một quái chia đều, phần dư theo UUID tăng dần. Tổng không vượt ngân sách quái. Đồng đội cần còn sống, cùng scope, trong 800 đơn vị và đã di chuyển/thi triển kỹ năng hợp lệ trong 10 giây. Vật phẩm rơi riêng cho từng nhân vật đủ điều kiện.
- Cấp 5+, nhóm 2–4 tới Trung Châu gần Tiếp Dẫn Sứ x=350 (±150). Chức năng → Phó bản tổ đội → từng người Sẵn sàng → trưởng đội Bắt đầu. Có ba wave, boss Huyền Mộc Yêu Vương, giới hạn 15 phút. Wipe đầu làm lại wave sau 4 giây; wipe thứ hai thất bại. Giữ suất khi mất mạng trong grace; dưới hai suất còn hợp lệ thì thất bại.
- Thắng phó bản: thành viên trực tuyến, chưa rời và có hoạt động trong 10 giây tại instance nhận 500 EXP, 150 tu vi, 100 linh thạch, 1 Hồi Linh Đan nếu stack dưới 99. Thưởng quái vẫn theo ngân sách chung. Kết quả/ledger và progress được commit cùng transaction; retry dùng cùng grant đã chuẩn bị. Rời qua bảng phó bản hoặc tự trả về sau 60 giây kết thúc.
- Chức năng → Thách đấu 1v1: chọn đạo hữu cùng map trong 800 đơn vị. Hai bên đồng thuận, đếm ngược 5 giây, tối đa 3 phút. Hạ gục/đầu hàng/rời game/mất mạng 15 giây kết thúc trận; hết giờ hòa. HP/MP phục hồi đầy đủ khi vào/ra, buff tạm được xóa; cooldown vẫn áp dụng. Không thưởng hoặc trừ EXP/tu vi/vật phẩm. Ngoài duel không có PvP.
- GET `/api/characters/{id}/activities` dùng JWT chủ nhân để xem 20 kết quả phó bản và duel gần nhất. GET `/api/network/metrics` yêu cầu JWT.

Chi tiết giao thức: [docs/PROTOCOL.md](docs/PROTOCOL.md). Thay đổi: [CHANGELOG.md](CHANGELOG.md). Kiểm thử và giới hạn: [docs/PHASE6_VERIFICATION.md](docs/PHASE6_VERIFICATION.md).

```powershell
node --test tests/network-client.test.mjs
# Backend Docker đã chạy:
node tests/reconnect.mjs
node tests/party.mjs
node tests/duel.mjs
node tests/load.mjs # Ít nhất 5 phút; cần Docker CLI để đo CPU/RAM.
# Backend local riêng ở 8081, cùng PostgreSQL:
$env:SERVER_PORT='8081'
.\scripts\start-backend.ps1 -EnableDevTools
# Terminal khác: đặt TEST_API=http://127.0.0.1:8081 và DEV_ADMIN_KEY từ .env:
node tests/dungeon.mjs
node tests/progression.mjs
```

Test tạo dữ liệu QA mới và giữ lại trong PostgreSQL. Chạy các bộ tích hợp tuần tự để tránh rate limit đăng ký 10 lần/phút; khi gặp 429, đợi cửa sổ một phút rồi chạy lại. Không tắt rate limit để chạy test. `tests/load.mjs` tạo 4 tài khoản × 5 nhân vật, năm tổ đội; xuất số liệu không chứa token vào `.runtime/phase6-load.json`. Tham số `TEST_API` chọn backend QA cho các integration script; production.mjs luôn kiểm tra Docker 8080.

## Chơi Phase 7

- Cổng x190 ở ba thành khởi đầu đưa tới **Thanh Vân Ngoại Sơn / Huyết Vụ Cốc / Linh Thú Sơn Lộ** (đề xuất 1–6). Đến gần và nhấn E. Cổng cũ x2200 sang Trung Châu vẫn hoạt động. Map → xem kết nối và cấp/quest cần thiết từ dữ liệu server.
- **Tam Giới Biên Cảnh** đề xuất 6–12; cổng cuối các field yêu cầu cấp 6, Trung Châu x760 cũng nối tới đây cho người muốn khám phá sớm. **Thiên Môn Di Tích** đề xuất 10–20, cổng từ Trung Châu x3080 hoặc Biên Cảnh x3450 yêu cầu cấp 10. Đây là khoảng đề xuất; điều kiện vào ghi riêng trên mỗi cổng.
- Ground/solid/platform được server và client dùng chung. Field có vật cản phải nhảy qua; vùng viền xanh là nghỉ/hồi phục, vào trại nghỉ lưu checkpoint. Hồi sinh về checkpoint; reconnect còn grace giữ đúng map/instance/vị trí. Thành vẫn giữ bãi luyện cũ bên phải lõi an toàn.
- Quái NORMAL/ELITE/MINIBOSS/WORLD_BOSS có aggro/threat/leash/RETURN, spawn/respawn theo nhóm và UUID ổn định. Huyền Mộc Yêu Vương có hai phase và cảnh báo AoE trước hit; damage, EXP/tu vi, personal loot/quantity đều do server quyết định. Quest vùng chỉ tính đúng definition/map sau khi nhận.
- NPC có nhãn **[AI]**: GUARD tuần tra/đánh quái, WANDERER đi lại và thoại script, CHALLENGER mở bài đối luyện riêng. Ở gần NPC → Chức năng → NPC AI / Đối luyện → EASY/HARD. Chuẩn bị 3 giây, tối đa 3 phút, có nút hủy; thắng/thua không đổi EXP/tu vi/đồ và trả HP/MP đầy. NPC không tính là người online.
- F3 trong Vite dev bật overlay geometry/nav/spawn/sensor/state/tick; production build tắt. Đồ họa quái/NPC/địa hình vẫn placeholder; giữ bộ sprite Kiếm Tu đã được cung cấp.
- Protocol 3: baseline + AOI delta upsert/remove, vùng ±900 ngang/±550 dọc, luôn gồm self. HUD đếm người trong khoảng nhìn; chat khu vực vẫn map-wide. Client thiếu baseline xin snapshot mới, không replay combat. Protocol 1/2 còn được hỗ trợ với full update trong AOI.

Không thêm migration Phase 7; dữ liệu bền vững bổ sung vào progress JSON có default cho save cũ, giữ V1–V3. Config nạp lúc khởi động server, chưa có hot reload. Thêm nội dung: [docs/WORLD_AUTHORING.md](docs/WORLD_AUTHORING.md). Thay đổi/kiểm chứng từng milestone: [docs/PHASE7_IMPLEMENTATION.md](docs/PHASE7_IMPLEMENTATION.md). Số đo tải thực tế: [docs/PHASE7_PERFORMANCE.md](docs/PHASE7_PERFORMANCE.md).

```powershell
node --test tests/network-client.test.mjs
node tests/maps.mjs
node tests/ecology.mjs
node tests/bots.mjs
node tests/interest.mjs
node tests/load-world.mjs # Docker đang chạy; 20 client, ba field combat, tối thiểu 5 phút.
# QA local 8081 và DEV_ADMIN_KEY đã thiết lập:
node tests/world-content.mjs
```

Load Phase 7 dùng 4 tài khoản × 5 nhân vật, 6 party trên ba field, 64 monster slots và 21 NPC, xuất `.runtime/phase7-load.json`. Chạy riêng khi không có các test khác để CPU/RAM/network có ý nghĩa. Không dùng mức tải local này để khẳng định sức chứa production hoặc hàng nghìn người chơi.
