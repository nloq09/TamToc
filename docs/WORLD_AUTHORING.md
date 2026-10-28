# Thêm nội dung thế giới bằng dữ liệu

Nguồn chuẩn là bốn file trong `backend/src/main/resources/`: `maps.json`, `combat.json`, `bots.json`, `progression.json`. Client đọc hình học/metadata từ server. Không thêm tọa độ song song trong Phaser. Các thay đổi cấu hình được nạp khi **khởi động lại backend**, chưa có hot reload.

## Quy ước và vòng đời

- Một world unit là một pixel thiết kế; x tăng sang phải, y tăng xuống. Tọa độ actor là tâm bàn chân. Player hitbox 32×44, tốc độ 240 unit/s, gravity 1500 và jump velocity 570 hướng lên ở map mặc định.
- Giữ ID ổn định. Không sửa Flyway V1–V3. Checkpoint, map version và bộ đếm quest được lưu trong progress JSON hiện có; runtime quái/NPC không tạo tài khoản hoặc row JPA cho mỗi entity.
- `simulate` 50 ms: hết lease/loot/invite → di chuyển và hồi sinh người chơi → checkpoint/regen → NPC mở → nhóm mục tiêu theo map/instance → Monster AI → dungeon/duel/training → metrics. Lệnh skill được xác thực và resolve dưới cùng world lock khi nhận, không giả định mọi combat nằm sau AI trong tick. `updates` 100 ms tạo AOI snapshot/delta; trạng thái party/activity mỗi 500 ms.
- Brain interval là dữ liệu (quái mặc định 200 ms, NPC theo profile); vật lý vẫn 50 ms. Không tạo timer/thread cho từng quái hoặc bot. Mọi target/hit/portal kiểm tra scope thật trên server.
- Đóng private instance xóa runtime quái hoặc practice NPC, snapshot mới thay baseline của scope cũ. Hết kết nối xóa cache AOI theo connection ID. Runtime session/party/trận tập không được phục hồi qua server restart.

## Map và cổng

Sao chép cấu trúc `thanh-van-ngoai-son` cho một FIELD mới rồi sửa:

1. `id`, `name`, `version`, `regionId`, `raceTheme`, khoảng cấp, `mapType`, kích thước, `ground`, `bounds`, `gravity`, `maxPlayers`, `pvpPolicy`.
2. `groundColliders` là nền đặc, `obstacles` là solid, `platforms` là bề mặt một chiều. Renderer dùng chính các rectangle này. Spawn/waypoint phải ở trong bounds và không cắt solid; đặt y ở mặt nền/platform.
3. Tạo ít nhất một `spawnPoints`. `safeZones` có bounds, `protectedArea`, `heal`; `spawnId` tùy chọn biến vùng thành checkpoint. Spawn checkpoint phải nằm trong vùng đó. Lõi thành an toàn; bãi luyện MVP bên phải x420 được giữ để tương thích nội dung cũ.
4. `portals` có `portalId` duy nhất toàn thế giới, `sourceMapId`, x, `triggerBounds`, target map/spawn, `requiredLevel`, `requiredQuest` tùy chọn, `enabled`. Cổng về phải được khai báo riêng. Client chỉ gửi ID; không chọn x/y đích. Đặt trigger dễ tới và kiểm tra cấp thực tế khác với khoảng cấp đề xuất.
5. Nối vùng bằng cổng ở map đang có. UI Bản đồ đọc danh sách/kết nối mới từ `/api/world/maps`, không cần sửa client.
6. Tăng version khi sửa hình học. Người chơi lưu ngoài bounds/trong solid/map đã xóa được chuyển về checkpoint hợp lệ hoặc spawn mặc định; save thuộc map DUNGEON/ARENA không còn runtime được đưa về thành của tộc. Giữ spawn ID checkpoint cũ hoặc cung cấp fallback đầu danh sách.

Navigation gồm waypoint `{id,x,y}` và cạnh có hướng `{from,to,type}`. WALK yêu cầu có nền và không vướng solid; JUMP chỉ dùng khi capability và quỹ đạo vật lý đạt; DROP kiểm tra độ cao/LOS. Thêm cạnh ngược khi muốn đi hai chiều. Không có route hợp lệ thì AI dừng/quay về, không teleport. Chỉ khai báo graph không bảo đảm mọi actor đi được: test capability/quỹ đạo theo speed/jump/gravity thật. F3 trong dev vẽ geometry/nav/spawn/sensor/state; production build tắt overlay.

## Loài quái và spawn group

Thêm definition vào `combat.json.monsters`, dùng WOLF, WOLF_ALPHA hoặc RUIN_SENTINEL làm mẫu. Các trường gồm stats/reward, `behavior` nội tuyến, tier, drop table và tùy chọn phase/ability. `behavior` cấu hình detection, attack range/cooldown, leash/timeout, think interval, speed/jump/drop và canJump. Tier đã hỗ trợ NORMAL/ELITE/MINIBOSS/WORLD_BOSS; BOSS trong dungeon xuất DUNGEON_BOSS ở runtime.

Trong map thêm `monsterSpawnGroups`:

```json
{"id":"new-field-wolves","definitionId":"WOLF","maxAlive":3,"startX":720,"endX":1080,"y":540,"respawnMs":5000}
```

ID group duy nhất toàn thế giới, số slot 1–100. Validator kiểm tra từng slot nội suy không nằm trong solid; designer cần kiểm tra thêm lộ trình tuần tra không cắt trại nghỉ. Slot giữ UUID qua respawn; reward flag reset khi bắt đầu vòng đời mới. Không tạo row/timer mới mỗi lần chết. Alert chỉ chia sẻ nhóm gần; threat/leash/RETURN được quản lý trong engine.

Boss phase dùng ngưỡng HP giảm dần và ability ID hợp lệ. Ability là AoE có windup/footprint; telegraph gửi trước resolve và còn nằm trong snapshot cho người vào AOI muộn. Test người đứng ngoài footprint/LOS/scope không bị hit. Không gửi loot seed/threat hoặc drop table qua metadata công khai.

Drop tham chiếu item có thật trong `progression.json`, chance 0–1, quantity hợp lệ và stack cap. Reward/kill credit đi qua `MonsterDefeats.once` và `PartyRewardPolicy`; không thêm cách grant thứ hai trong AI hoặc UI. Vật phẩm MATERIAL/QUEST hiện chỉ là inventory metadata, chưa có hệ giao dịch.

## NPC AI và hội thoại

Thêm definition/profile/dialogue trong `bots.json`, rồi tham chiếu từ `map.botSpawnGroups` qua `definitionId`, `spawnPoint`, `patrolRoute`, `interactionRadius`. Mọi waypoint trong route phải có trong map. `npcSpawns` hiện dùng cho Guide có thoại script và điểm nhận quest.

Ba vai trò đã chạy: GUARD (`HOSTILE_MONSTERS_ONLY`), WANDERER (`NEVER`), CHALLENGER (`CONSENT_TRAINING`). Dùng definition cùng tộc hoặc neutral làm mẫu; danh sách skill phải tồn tại và phù hợp tộc. NPC không vào `players`, không được cấp connection ticket. View luôn có `isAI=true`, role/attitude/faction và nhãn [AI]. Không dùng LLM hoặc dịch vụ bên ngoài để chọn hành vi.

GUARD perception/LOS, patrol, đánh quái bằng CombatEngine, rút về khi HP thấp. WANDERER patrol và dừng TALK với các dòng thoại designer. CHALLENGER chỉ tạo arena khi có START_TRAINING chủ động, EASY/HARD lấy reaction/stat modifier từ `difficulties`. Không tự bật trận khi người chơi đi ngang. TRAINER/ESCORT/SCOUT và role mới cần logic riêng, chưa thể chỉ khai báo tên trong JSON.

## Quest theo vùng

Thêm quest `type: KILL`, target definition, count/reward, `requiredLevel`, `mapId` tùy chọn. Ví dụ OUTER_WOLVES chỉ tính WOLF ở Thanh Vân Ngoại Sơn. Bộ đếm lấy mốc lúc nhận; kill trước nhận hoặc khác map không tính. WOLVES/PATH giữ bộ đếm cũ để save cũ tiếp tục được. Nhận quest ở gần NPC GUIDE của map hiện tại; UI lấy vị trí Guide từ map. Claim kiểm tra mục tiêu/status và commit một lần.

## Kiểm tra trước khi dùng

Startup Java validators kiểm tra ID/reference, map geometry/spawn, portal/spawn/quest, waypoint, group, bot profile/dialogue/skill, monster behavior/phase/drop và quest target. Sai dữ liệu làm startup fail với vị trí/ID, không silently bỏ nội dung.

Chạy backend unit tests, frontend build và `tests/maps.mjs`, `tests/ecology.mjs`, `tests/bots.mjs`, `tests/interest.mjs`. Với QA local có DEV_ADMIN_KEY, chạy thêm `tests/world-content.mjs` và progression/dungeon. Hai tài khoản ở cùng vùng phải thấy cùng entity ID trong khoảng nhìn, đi xa rồi quay lại không ghost; tài khoản khác map/instance không nhận entities. Đo lại bằng `tests/load-world.mjs` nếu tăng đáng kể số slot/AI hoặc frequency.

AOI mặc định ±900 ngang/±550 dọc, gồm self. Protocol 3 gửi baseline rồi upsert/remove; protocol 1/2 vẫn full ENTITY_UPDATE trong AOI. Chat khu vực là map-wide; party/control/activity có scope riêng. AOI giảm băng thông và rendering, không tắt mô phỏng quái map xa.
