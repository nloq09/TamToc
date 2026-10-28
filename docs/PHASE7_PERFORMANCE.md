# Phase 7 — số đo thực tế

Thực thi 28/10/2026, bắt đầu **22:23:16 Asia/Bangkok** (15:23:16 UTC), dài **301.640 ms**, tương đương 5 phút 1,64 giây. Script `tests/load-world.mjs`, kết quả thô `.runtime/phase7-load.json`, log `.tools/phase7-D-load.log` trên máy phát triển. Hai thư mục bằng chứng này được gitignore; báo cáo này lưu kết quả để review trong repo.

## Môi trường và kịch bản

- Backend Docker Compose Java 21, PostgreSQL 17 có dữ liệu cũ/QA, frontend Vite đang chạy. Docker báo giới hạn memory khả dụng 15,48 GiB; Compose không đặt CPU/memory quota riêng. Dừng QA Java 8081 trước đo, không chạy build hoặc integration khác trong khoảng đo. Công việc soạn tài liệu không thay runtime đang đo.
- 20 kết nối WebSocket protocol 3 thật từ Node.js 24, bốn tài khoản × năm nhân vật; 7 Nhân/7 Ma/6 Yêu. Sáu party theo nhóm 4+3/4+3/4+2 trên ba field riêng. Một người dùng đang online từ trước nên global online là 21 xuyên suốt; không đóng tab hoặc session đó.
- 64 slot quái mở và 21 NPC AI được mô phỏng. Nhóm tải đánh các đàn quái NORMAL ở phần bên trái ba field, có guard/citizen hoạt động, không phải cả 64 quái đều được kéo aggro đồng thời. Monster slot toàn thế giới và AI map xa vẫn chạy patrol/return/respawn theo scheduler.
- Input 20 Hz/client, heartbeat 2 giây, chọn/đánh mục tiêu/skill/race skill theo HP/MP/cooldown, map chat 10 giây, nghỉ ở safe camp theo chu kỳ. Không sửa HP/damage/EXP bằng admin. Loot được sinh và đồng bộ riêng; script không đo thao tác nhặt loot. Private dungeon/duel/training được test chức năng riêng, không chạy cùng lượt tải này.

## Kết quả

- **20/20 kết nối giữ mở suốt lượt**, 0 unexpected disconnect, 0 error command từ nhóm đo, 0 lần nhân vật chết. Cả 20 đều tăng EXP thật, từ 586 đến 1.407 EXP lifetime/người.
- Docker CPU có **11 mẫu**: min **13,20%**, max **34,86%**, trung bình số học **19,70%**. Đây là Docker CPU%, không phải phần trăm của toàn bộ máy Windows hay số liệu cả PostgreSQL. Không suy ra p95 từ 11 mẫu.
- Backend RAM **330,8–352,9 MiB** trong các mẫu Docker `MemUsage`. Mẫu cách khoảng 30 giây, Docker stats thêm 1–2 giây mỗi lần; không phải peak liên tục hoặc bằng chứng không leak sau nhiều giờ.
- **6.033 simulation ticks / 301,64 giây = 20,0007 Hz**. Mean thời gian thân `simulate` trong chính cửa sổ đo: **0,52995 ms**, tính bằng hiệu `(meanTickMs × ticks)` trước/sau chia hiệu ticks. **0 tick vượt 50 ms** trong lượt.
- `maxTickMs` tích lũy server trước và sau đều **33,984174 ms**; không reset counter trước đo, nên đây là max toàn phiên gồm startup/regression, không phải max riêng của 5 phút. `simulate` timing không gồm thời gian chờ world lock/scheduler, broadcast/serialization, DB từ REST hoặc latency end-to-end. Không dùng 0,53 ms để khẳng định mọi packet có latency đó.
- Counter exceptions server **1 → 1**, không tăng trong đo. Counter 1 đã có trước lượt do send vào socket vừa đóng trong kiểm thử cleanup; không ghi thành “server chưa từng có lỗi”. Log cleanup vẫn là giới hạn transport đã nêu trong implementation.

## Network

Đo `Buffer.byteLength` trên mọi JSON message thực nhận ở 20 WebSocket, không ước lượng bằng số entity:

- **114.565 message**, **292.167.214 byte** trong cửa sổ đo; trung bình **968,60 kB/s** cả nhóm, **48,43 kB/s/client** (kB thập phân). Chưa gồm TCP/WebSocket framing, TLS, REST, input hướng client → server hoặc asset tải ban đầu.
- Server global outgoing bytes tăng **300.826.245 byte**, bao gồm người online sẵn và control messages ngoài nhóm; vì vậy không thay thế số byte riêng 20 client bằng global counter.
- Nhóm nhận **60.340 ENTITY_DELTA**, 7.092 COMBAT_EVENT, 18.059 SKILL_CAST, 2.727 MONSTER_DEFEATED và 2.660 MONSTER_SPAWN. Đây là số **bản tin nhận**, sự kiện map được broadcast tới nhiều người, không phải số đòn/kill độc nhất.
- Delta là ở cấp entity: upsert toàn view khi HP/position/sequence/cooldown thay đổi, remove ID khi ra AOI. Các nhóm di chuyển/đánh gần nhau nên phần lớn avatar trong AOI thay đổi mỗi broadcast; băng thông còn đáng kể. Chưa có field patch/binary/quantization/compression và chưa làm A/B toàn snapshot, nên không công bố phần trăm tiết kiệm băng thông.

## Cleanup và acceptance

Sau LEAVE_WORLD cả 20 client: online **21 → 1**, delta clients và interest cache **21 → 1**, party **6 → 0**, grace **0**, active/allocated private instance **0**, training NPC **0**. NPC mở vẫn **21**, monster slots **64**; slot không nhân bản. Người online từ trước còn nguyên. Script assert cleanup, EXP thật, packet delta/combat/defeat và counters trước/sau rồi mới in PASS.

**Đạt acceptance 20 client của milestone D cho kịch bản này.** Đây là phép đo local năm phút ở ba field, không phải chứng nhận sức chứa production, tải hàng nghìn người, thời gian dài, worst-case 20 người cùng boss telegraph hoặc băng thông thấp. Database/network latency và latency gameplay chưa có percentile riêng.

Sau lượt đo chỉ siết validator startup cho portal/group ID và từng slot spawn nội suy trong solid, thêm assertions dữ liệu hỏng vào MapEngineTest. Không đổi simulation/AI/broadcast, số map/slot/NPC hoặc config tải. Bản cuối được Docker build chạy lại toàn bộ unit tests và smoke AOI trên server sau restart; số đo trên vẫn thuộc lượt trước thay đổi validator đó.

## Chạy lại

Giữ PostgreSQL volume; backend Docker hoạt động ở 8080. Chạy riêng `node tests/load-world.mjs`, cần Docker CLI truy cập stats. Giữ duration tối thiểu 300.000 ms. Script tự tạo bốn tài khoản, 20 nhân vật; không tắt rate limit. Chờ cửa sổ rate limit nếu vừa chạy nhiều integration tests. Kết quả QA được giữ trong DB, không reset dữ liệu người dùng.

Sau thay số lượng quái/AI/think interval hoặc thay backend runtime, chạy lại và cập nhật báo cáo; không tái sử dụng số đo này làm kết quả cho một cấu hình khác.
