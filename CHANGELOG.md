# Changelog

## World Progression 15 maps — 29/10/2026

- Catalog `world_catalog_15_v1` có 15 canonical map IDs và alias tương thích save cũ; 57 planned map giữ PLANNED_DISABLED. Bốn thành hoàn toàn an toàn; thêm ba mid field, Hoang Nguyên và Cửu U bằng geometry Phase 7 đã được cho phép.
- 34 species, 31 normal groups, 12 boss placements; slot có biến thể level, zone/maxAlive/respawn và enabled được validate. Chỉ map có người/lease grace mô phỏng AI; AOI/delta và server-authoritative combat giữ nguyên.
- 46 quest definitions với race/level/prerequisite/follow-up, objective theo map/species/group/placement/wave; khám phá lưu qua relogin. Giữ WOLVES/PATH/khảo hạch chín thế lực, chống claim trùng, khóa tăng cấp mới trên 25.
- Hai dungeon data-driven: Huyền Mộc 10+, Cửu U 18+, wave/boss phase và unique reward ledger; Cửu U cooldown 15 phút lưu bền vững. World Boss dùng clock/scheduler và V4 unique cycle ledger, không sinh lại cùng cycle sau restart.
- Sổ tay Map/Quái/Boss, gợi ý quest, trạng thái đã gặp/đã đến, selector dungeon và inspector local/admin chỉ đọc. API metadata loại route debug; metrics có p50/p95/p99 theo khoảng tick.
- Java 21 Docker 75 unit tests, transport 7 tests, build frontend và các kịch bản thật routes/dungeon/discovery/save archived đạt. Báo cáo tải và phạm vi kiểm chứng: `docs/WORLD15_PERFORMANCE.md`; giới hạn/live World Boss và hướng dẫn rollback: `docs/WORLD15_IMPLEMENTATION.md`.

## Phase 7 — 28/10/2026

- Map data có geometry/collision, cổng có điều kiện/spawn, safe zone/checkpoint, nav graph và startup validation; sửa save không hợp lệ khi vào lại. Thêm ba field theo tộc, Biên Cảnh và Di Tích; giữ map/cổng/bãi luyện cũ.
- Quái có population slots/UUID qua respawn, FSM/LOS/threat/leash/RETURN, navigation vật lý dùng chung; elite/miniboss và boss hai phase với telegraph. Reward đúng một lần, drop table server/private loot quantity, quest map-scoped cấp 1–10.
- NPC AI GUARD/WANDERER/CHALLENGER từ config và thoại script; arena tập EASY/HARD riêng, cùng CombatEngine, countdown/cancel/reconnect/cleanup, không thay đổi tài sản hoặc progression.
- Protocol 3 AOI baseline + delta upsert/remove; client resync khi thiếu base, tương thích protocol 1/2, map chat giữ scope. Metadata vùng/cổng trên bảng Bản đồ, minimap và dev F3 overlay.
- Sửa input sequence qua chuyển map/snapshot, tránh UI NPC bị rerender mất nút, giữ AI hoàn thành jump trước combat, cache kết nối và registry NPC đọc metrics an toàn.
- Không thêm migration; checkpoint/map versions và quest counters bổ sung vào progress JSON cũ. Tài liệu thêm nội dung bằng dữ liệu và script đo tải riêng Phase 7; xem báo cáo thực thi trong docs.

## Phase 6 — 28/10/2026

- Multiplayer protocol 2: vé mới khi retry, proof phục hồi, lease 20 giây, controller duy nhất, epoch/version, heartbeat, snapshot trước delta, không replay combat. Giữ AUTH protocol 1 tương thích.
- Tổ đội đa tộc 2–4: lời mời TTL, quyền trưởng đội, chuyển quyền, kênh chat, HP/MP/trạng thái, chia ngân sách quái, personal loot.
- Huyền Mộc Bí Cảnh tổ đội: READY, registry instance riêng, 3 wave và boss AI chung, wipe/timeout/reconnect/cleanup, kết quả và thưởng transaction có unique ledger.
- Duel đồng thuận 1v1: arena riêng, countdown, PvP cùng combat engine, disconnect/forfeit/knockout/timeout, kết quả DB một lần, không thưởng hoặc mất tài sản.
- Flyway V3 thêm lịch sử dungeon/participants/reward grants/duel results. Giữ nguyên V1/V2 và dữ liệu cũ. Server restart hủy dungeon ACTIVE của node, character lưu anchor map chung.
- Metrics realtime; kiểm thử domain, transport, REST/WS/PostgreSQL thật và script đo tải 20 client/5 phút.
- Sửa race close từ socket cũ, input/VFX sau resume, UI giữ quái wave cũ, và lỗi trả người chơi mất mạng về map chung sau duel.
- Giữ bộ sprite Kiếm Tu 64×64 được cung cấp và giao diện ngăn trái/HUD theo ảnh tham khảo.

Kết quả chạy thực tế và giới hạn nằm trong `docs/PHASE6_VERIFICATION.md`.
