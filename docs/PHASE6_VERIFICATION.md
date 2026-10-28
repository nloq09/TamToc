# Phase 6 — kiểm chứng thực tế

Ngày thực thi: 28/10/2026, Asia/Bangkok. Mã được sửa trực tiếp trên MVP hiện có. Không tạo ứng dụng mới, không đổi framework, không xóa dữ liệu hoặc sửa migration đã áp dụng.

## Audit và thay đổi

- Spring Boot: `GameWorld` giữ world lock, scheduler 50 ms (20 Hz), broadcast 100 ms; `Movement` xử lý vị trí, platform và gravity, `MonsterAi`/`CombatEngine` quản lý AI/damage/cooldown. `GameSocket` giữ auth/rate limit và dispatch ý định. Các API nhân vật/progression vẫn kiểm tra ownership và serialize mutation qua world lock.
- `CharacterSessions` tách lease nhân vật khỏi connection; `CommandRequests` dedup lệnh; `NetworkMetrics` bổ sung số liệu. Frontend `GameConnection` thực hiện retry/snapshot/version; scene và VisualComponent được giữ lại.
- `PartyRegistry`, `PartyRewardPolicy`, `PartyEventPublisher` quản lý tổ đội và chia ngân sách. `InstanceRegistry` chứa runtime theo UUID, dùng chung world scheduler. `DungeonService` điều phối READY/wave/wipe/thưởng; `DuelService` quản lý lời mời/countdown/kết thúc; cùng một CombatEngine cho PvE/PvP.
- V1/V2 giữ nguyên. V3 được Flyway áp dụng và validate trên PostgreSQL đang có: dungeon_runs, participants, reward ledger có khóa ghép duy nhất, duel_results có PK/FK/constraint đối thủ/người thắng. Party và lease tạm không cần bảng mới.
- Bảng HTML/CSS trong ngăn trái: tổ đội, ready/phó bản, thách đấu. HUD hiển thị thành viên/HP, wave/timer, tên và HP đối thủ, countdown/kết quả. Sprite thử của người dùng được giữ.

## Build và test đã chạy

- Backend: **34 unit tests, 0 failure/error**, gồm 12 test MVP được giữ, 6 session, 6 party, 5 dungeon, 5 duel. Host JDK 25 compile release 21; Docker build/test/runtime Java 21. Build Maven trong Docker thành công.
- Frontend: `npm run build` thành công (TypeScript + Vite). Bundle ~1.27 MB, gzip ~353 KB, gồm Phaser; chưa tách chunk.
- `node --test tests/network-client.test.mjs`: **5/5**. Snapshot gate/epoch/version, callback socket cũ, không replay combat, duplicate-controller terminal, suspend/leave và abort vé đang chờ.
- `tests/multiplayer.mjs`: REST + WS + PostgreSQL thật; auth, ownership, mặc định ba tộc, vé một lần, chặn controller trùng, cổng, hiển thị hai người, di chuyển hai chiều, chặn tọa độ, chat, disconnect và lưu vị trí.
- `tests/combat.mjs`: HP quái chung, damage server, chia EXP đóng góp, cooldown, khóa kỹ năng, respawn quái.
- `tests/progression.mjs` trên QA local 8081: NPC, 5 kill thật, nhận thưởng một lần, loot, equipment, đột phá, khảo hạch, gia nhập Kiếm Tông, mở kỹ năng, chặn sai tộc/chọn thêm tổ chức, lưu tiến trình/cooldown qua đăng nhập lại.
- `tests/reconnect.mjs`: sử dụng chính transport TypeScript frontend với server thật; ngắt 7 giây/backoff, chịu damage trong grace, giữ ID quái, không nhân bản nhân vật, input/chat sau resume; takeover socket nửa mở, callback close cũ không xóa controller mới, chặn giả scope, quá grace join lại an toàn.
- `tests/party.mjs`: 5 user/ba tộc, accept đồng thời không vượt 4, dedup accept, quyền leader/chuyển quyền, party-only chat, giữ party khi resume, tổng ngân sách EXP/tu vi, AFK/khác map không nhận (kể cả đóng góp sát thương cũ đã quá 10 giây), personal loot/pickup, kick/leave/succession.
- `tests/dungeon.mjs` trên QA local 8081: 4 user/cấp 10 và một người ngoài (công cụ local chỉ cấp level); hai party chạy **hai instance song song**. READY và dedup start, ID quái khác nhau, HP/visibility tách biệt, giả scope bị chặn, ngắt 5 giây vào đúng instance; người ngoài không thể nhận lời mời cũ để vào đội đang chạy dungeon. Bot dùng INPUT/ATTACK/QI/kỹ năng tộc/potion qua API thật để đánh hết ba wave + boss. Hai lượt COMPLETED, lịch sử/unique grant đúng, đọc/retry không thêm linh thạch, trở về Trung Châu. Unit test thêm wipe/timeout/cleanup và lỗi commit giữ nguyên eligibility khi retry kể cả thành viên đã rời.
- `tests/duel.mjs`: 3 user, chặn PvP ngoài arena; từ chối không tạo instance; consent/countdown/dedup accept; chặn người ngoài/giả instance; damage/cooldown thực; knockout, progression/items không đổi, một kết quả DB; resume 5 giây, xử thua sau 15 giây, kết quả khi nối lại, đầu hàng lúc countdown. Unit test thêm timeout hòa, mục tiêu/range/MP, persistence retry cùng result.
- `tests/production.mjs`: Docker mặc định trả 404 cho dev endpoint, 401 JWT sửa đổi, 403 Origin HTTP/WebSocket không được phép.
- `git diff --check`: không lỗi whitespace (repo Windows tự xử lý CRLF).

Các bộ hồi quy multiplayer/combat/party/reconnect cuối được chạy trên QA 8081 để không làm nhiễu số lượng người trong lượt tải Docker. Production và duel cũng đã chạy trên Docker 8080. Test không dùng dữ liệu của người chơi để giả lập kết quả; dữ liệu QA giữ lại để điều tra.

## Browser QA

Đã thao tác hai tab với hai tài khoản thử khác nhau. Các lần kiểm tra trước xác nhận mời/accept party, HP/MP hai phía; nhóm sẵn sàng vào dungeon, wave/timer; một người rời làm nhóm hai người thất bại đúng luật, cả hai trở về lobby.

PvP: chọn người gần đó → gửi lời mời → bên kia chấp nhận; cả hai tới Đấu Trường, countdown 5 giây khóa nút kỹ năng, HUD hiện tên/HP đối thủ, NPC interaction bị khóa. Đóng tab đối thủ, phía còn lại nhận thắng do mất mạng quá 15 giây và trở về Thanh Vân Thành. Tab nối lại thấy bảng kết quả. Console cuối không có JavaScript error. Ảnh kiểm chứng lưu local `.tools/phase6-duel-ui.png`.

Lỗi tìm được trong kiểm thử: trả người đã mất mạng về map gọi snapshot với socket null, làm gián đoạn trả người còn lại. Đã sửa guard socket và chạy lại integration duel thành công. Cũng sửa helper test bỏ snapshot/AUTH cũ trước reconnect để không nhầm snapshot của lease trước.

## Đo tải

Script: `node tests/load.mjs`, báo cáo JSON không token tại `.runtime/phase6-load.json`. Bốn tài khoản × năm nhân vật, 20 socket đồng thời, năm tổ đội 4 người. INPUT 20 Hz, heartbeat 2 giây, chat khu vực + QI mỗi 10 giây; di chuyển x180–330 tại khu an toàn. Đây là smoke test giao thức/session/broadcast/party, chưa đại diện 20 người đánh boss hoặc nhiều instance đồng thời. Dungeon/duel đã được kiểm tra riêng ở trên.

Lượt cuối trên image backend `d54f78fcf5cc`, 19:23:36–19:28:36 ngày 28/10/2026, **300.017 ms — PASS**. Image này đã có hai guard cuối chống người đóng góp AFK nhận thưởng và nhận lời mời cũ vào party đang chạy dungeon; 34 unit tests đã chạy lại trong Docker, integration combat/party và hai dungeon cũng đã chạy lại thành công.

- 20 bot/năm tổ đội giữ kết nối đủ thời gian, **0 lỗi client, 0 disconnect của bot**, không phải chỉ đăng nhập rồi đứng im. Server có 21 người online gồm một client ngoài nhóm bot.
- CPU backend: 12,09–26,27%, trung bình 10 mẫu 16,17%. RAM: 312,5–320,9 MiB.
- 6.001 tick trong cửa sổ; thời gian xử lý trung bình **0,0917 ms**. Max high-water server **4,879 ms**, đã có trước cửa sổ và không tăng; 0 tick vượt budget 50 ms.
- Counter exceptions: 1 → 1, **không có lỗi server mới trong cửa sổ**. Một WARN cũ do đóng socket ở cleanup QA trước đó được giữ nguyên trong counter, không reset để che lỗi.
- Counter disconnects toàn server: 9 → 19 (**+10**); resumes: 2 → 12 (**+10**), resumeFailures không tăng. 20 socket bot đều instrument và không đóng/mở lại; các reconnect này thuộc kết nối ngoài nhóm bot. Không báo nhầm counter toàn server là 0.
- Outgoing toàn server: +100.317 message, +553.197.385 byte (~1,84 MB/s). Đây là full-state JSON theo scope, chưa tối ưu cho nhiều người cùng map.
- Sau bot LEAVE_WORLD: online 21 → 1, party 5 → 0, grace 0, active/allocated instance 0. Client ngoài nhóm được giữ nguyên; không có session/party/instance bot bị bỏ lại.

CPU/RAM lấy bằng Docker stats khoảng 30 giây/lần; tick/outgoing/error/disconnect lấy từ endpoint metrics có JWT. Mean tick trong cửa sổ tính bằng chênh lệch tổng thời gian tick/tổng tick, max là high-water từ lúc server khởi động. Đây là thời gian xử lý thân simulate sau khi lấy world lock, chưa đo scheduler jitter/p95 hay latency đầu-cuối. Docker CPU 100% tương ứng một logical CPU, không phải toàn máy; RAM là container backend, không bao gồm PostgreSQL, Vite và client bot.

Một lượt đầu đã chạy đủ 300.024 ms: client errors 0, unexpected disconnect 0; counter server tăng 1 tại đường gửi socket trong lúc frontend đang được cập nhật. Không coi lượt đó là PASS hoàn toàn. Đã nâng log lỗi gửi socket từ debug lên warn có stack và đo lại với mã cố định. Số liệu gốc giữ ở `.runtime/phase6-load-initial.json`.

## Giới hạn và phần chưa kiểm chứng

- Khi nhiều client test đóng socket đồng thời, kiểm tra isOpen và lệnh send có thể gặp race transport, ghi WARN `WebSocket session has been closed` và tăng counter exceptions. Đã quan sát ở cleanup test tổ đội, trước cửa sổ tải; client/session/party vẫn được dọn. Counter exceptions hiện bao gồm lỗi gửi socket; cần tách metric transport trước vận hành.
- Một node server, một world lock, một hàng đợi save. DB chậm có thể làm pause world khi thao tác cần commit; không phải kiến trúc scale MMO. Broadcast hiện gửi state toàn scope 10 Hz tới từng client: bandwidth tăng khi đông người cùng map.
- Party/instance/lease trong RAM, không tiếp tục dungeon/duel qua restart. Dungeon ACTIVE của node được đánh dấu FAILED SERVER_RESTART, nhân vật về anchor map chung đã lưu. Duel đang diễn ra chưa lưu record cho trường hợp server crash/restart giữa trận; chỉ trận đã kết thúc có lịch sử bền vững.
- Checkpoint thông thường 15 giây; crash có thể mất thay đổi sau checkpoint. Thưởng hoàn thành dungeon dùng ledger/transaction riêng. Unit test lỗi persistence/retry đã chạy; chưa thử cắt PostgreSQL đúng lúc COMMIT hoặc chaos test DB outage dài.
- Timeout/wipe/invite expiry được kiểm tra bằng clock giả domain; không chờ đủ 15 phút dungeon hoặc 3 phút duel trên WS thật. Reconnect/disconnect deadline đã chờ thời gian thật.
- Trong hồi quy QA host có một tick 58.339 ms (budget 50 ms), được log và không có tick chồng. Không giấu số liệu này; phép đo Docker riêng được báo theo cửa sổ ở trên.
- Chưa kiểm thử public HTTPS/WSS, mobile touch combat, nhiều server, soak hàng giờ, xếp hạng, giao dịch, bang hội, Tam Tộc Chiến hoặc đồ họa mới. Load smoke không phải tuyên bố sẵn sàng vận hành MMO công khai.

## Chạy lại

Backend unit: `cd backend; mvn test`. Client: `cd frontend; npm ci; npm run build`.

Backend Docker: `docker compose up -d --build`. Regression MVP: `scripts/test-integration.ps1`; transport/party/duel: `scripts/test-phase6.ps1`. Thêm `-IncludeLoad` để chạy 5 phút và lấy Docker stats. Script tôn trọng cửa sổ rate limit đăng ký; không tắt bảo vệ để chạy nhanh.

Dungeon/progression: máy chủ QA riêng, đặt SERVER_PORT=8081 rồi `scripts/start-backend.ps1 -EnableDevTools`; terminal khác đặt TEST_API=http://127.0.0.1:8081 và DEV_ADMIN_KEY từ `.env`. Chạy `tests/dungeon.mjs`/`tests/progression.mjs` hoặc wrapper `scripts/test-phase6.ps1 -TestApi http://127.0.0.1:8081 -IncludeDungeon`. Không bật endpoint dev trong production. Mỗi lượt test tạo tài khoản/nhân vật mới, giữ volume PostgreSQL.
