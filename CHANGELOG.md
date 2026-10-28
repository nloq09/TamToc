# Changelog

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
