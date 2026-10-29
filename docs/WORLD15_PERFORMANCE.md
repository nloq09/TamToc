# World15 — đo tải thực tế

Thực thi **29/10/2026 13:58:24 Asia/Bangkok**, dài **300,016 ms** (300.016 giây). Dùng `tests/load-world15.mjs` trên backend Docker 8080 bản Java 21 đã qua 75 unit tests. Kết quả gốc `.runtime/world15-load.json`, log `.tools/world15-load.log` là dữ liệu cục bộ được gitignore; bản số liệu đã lọc nằm ở [test-results/world15-load-20261029.json](test-results/world15-load-20261029.json), receipt kiểm thử ở [test-results/world15-20261029.txt](test-results/world15-20261029.txt).

## Kịch bản và môi trường

- 20 native WebSocket protocol 3 thật từ Node.js 24: bốn tài khoản × năm nhân vật, 7 Nhân/7 Ma/6 Yêu. Sáu party 4+3 / 4+3 / 4+2 trên ba field khởi đầu. Global online **21 → 21**, bao gồm phiên người chơi ngoài nhóm test; không đăng xuất hay sửa dữ liệu của người đó.
- Backend Compose Java 21, PostgreSQL 17 dùng volume cũ/QA, Vite 5173 đang chạy; Docker báo 15,48 GiB khả dụng, không có quota CPU/RAM riêng. QA Java 8081 đã dừng trước đo. Không build/restart/chạy test chức năng khác trong khoảng đo; soạn tài liệu không đổi runtime.
- Có **145 slot quái đăng ký** (135 normal, 9 elite, 1 World Boss scheduled) và **29 NPC đăng ký**. Ba field có **48 slot quái/6 NPC** được mô phỏng; tổng NPC active trong mẫu **6–9** do người ngoài nhóm có thể kích hoạt thêm map. Map vắng giữ slot/time, không tick AI. Nhóm tải đánh đàn NORMAL phía đầu field, không kéo cả 145 quái cùng lúc. World Boss nằm ngoài cửa sổ event trong kịch bản này.
- Input 20 Hz/client, heartbeat 2 giây, chọn mục tiêu/ATTACK/QI/race skill theo HP/MP/cooldown; map chat 10 giây, nghỉ protected camp theo chu kỳ. Không dùng admin grant HP/level/EXP. Loot được sinh/đồng bộ riêng, script không đo thao tác nhặt. Private dungeon/duel/training có test chức năng riêng, không thuộc benchmark này.

## Kết quả trong đúng khoảng đo

- **20/20 client giữ kết nối**, **0 lỗi command**, **0 unexpected disconnect**, 0 lần chết. Mỗi client nhận **472–788 EXP lifetime** thật; không dùng số kill broadcast làm số kill độc nhất.
- **6,002 tick / 300.016 giây = 20.0056 Hz**. Ring lấy **6,002 mẫu** sau `sinceTick=38371`; p50 **0.301157 ms**, **p95 1.623355 ms**, p99 **3.544212 ms**, max riêng cửa sổ **10.066071 ms**. Mean riêng cửa sổ **0.551489 ms**, tính bằng hiệu `(meanTickMs × ticks)` trước/sau chia hiệu ticks.
- **0 tick vượt ngân sách 50 ms**. Counter exception **0 → 0**. `maxTickMs` tích lũy **22.667399 → 22.667399 ms** là max toàn phiên, không thay cho max cửa sổ ở trên. Global disconnect/resume có thể đổi do phiên ngoài nhóm; số disconnect của nhóm lấy từ 20 socket riêng.
- Docker CPU **10 mẫu**, min **14.39%**, trung bình số học **20.10%**, max **32.24%**. Đây là CPU% của container theo Docker, không phải phần trăm toàn máy hoặc cả PostgreSQL; không suy ra CPU p95 từ ít mẫu.
- RAM backend **381.4–384.6 MiB**. Mẫu khoảng 30 giây, Docker stats thêm 1–2 giây, không đo peak liên tục hoặc chứng nhận không leak sau nhiều giờ.

Thời gian tick chỉ đo thân `simulate`, **không gồm** chờ world lock, broadcast/serialization, DB từ REST, scheduler hoặc latency end-to-end. Không dùng p95 này để khẳng định packet/gameplay latency tương đương.

## Network

Đếm `Buffer.byteLength` trên JSON nhận thực ở đúng 20 socket, không ước lượng bằng số entity:

- **98,830 messages / 345,257,112 byte**: **1150.80 kB/s** cả nhóm, **57.54 kB/s/client** trung bình (kB thập phân). Không gồm framing/TCP/TLS, REST, asset hoặc chiều input client → server.
- Server global outgoing tăng **106,223 messages / 372,409,383 byte**, bao gồm phiên ngoài nhóm. Không thay số byte nhóm bằng counter global.
- Nhóm nhận **60,000 ENTITY_DELTA**, **3,701 COMBAT_EVENT**, **10,537 SKILL_CAST**, **1,747 MONSTER_DEFEATED** và **1,598 MONSTER_SPAWN**. Bản tin broadcast có thể được nhiều người nhận; đây không phải số event/kill độc nhất.
- Delta ở cấp entity, avatar cùng AOI thay đổi vị trí/HP/sequence/cooldown thường xuyên, nên băng thông còn đáng kể. Chưa có field patch/binary/compression hoặc A/B snapshot, không công bố phần trăm tiết kiệm.

## Cleanup, lần chạy và giới hạn

Sau rời nhóm test, online **21 → 1**, party **6 → 0**, AOI cache **21 → 1**, delta client **21 → 1**; grace **0**, active/allocated instance **0/0**, training NPC **0**. Slot vẫn 145/NPC registry 29, không nhân bản. Khoảng đo và cleanup server đã được xác nhận từ dữ liệu đã lưu và metrics có JWT sau đó. Lượt full script thoát code 1 vì 9 native Node socket còn CLOSING sau 45 giây khi client/server đóng đồng thời. Sửa harness để gửi LEAVE_WORLD rồi chờ server đóng; probe riêng 20 client đạt 20/20 CLOSED, online 21→1, AOI 1. Không ghi exit code full script thành PASS và không giả vờ có lượt đo 5 phút thứ tư sau sửa.

Có ba khoảng đo đủ 5 phút nhưng full-script exit code đều 1 ở điều kiện cleanup: lần đầu đòi global online về 0 dù có người vừa vào; lần hai timeout 15 giây; lần cuối tăng chờ 45 giây và lưu được chẩn đoán 9 socket Node CLOSING trong khi server đã online 1/party 0/AOI 1. Lần hai giữ được 300.020 ms/6.001 tick, p95 1,546416 ms; báo cáo chính dùng khoảng cuối ở trên. Không xóa dấu vết hay ghi các exit code đó thành thành công. Harness hiện ACK từng PARTY_LEAVE, giữ số liệu trước cleanup và dùng server-led close. Probe 20 client riêng kiểm chứng transport fix, không phải một phép đo tải dài mới. Không đụng phiên người chơi khác.

**Đạt acceptance 20 client cho kịch bản local này.** Chưa chứng nhận sức chứa production, hàng nghìn người, mạng chậm, độ bền nhiều giờ, 20 người cùng World Boss/boss telegraph hoặc latency gameplay/DB p95. Chưa có full playthrough tự nhiên 1→25 và balance mọi tổ hợp. Phạm vi chức năng: [WORLD15_IMPLEMENTATION](WORLD15_IMPLEMENTATION.md).
