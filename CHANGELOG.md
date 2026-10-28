# Changelog

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
