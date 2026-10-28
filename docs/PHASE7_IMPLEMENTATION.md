# Phase 7 — World / Monster / NPC AI

Tài liệu này theo prompt Map–Monster–Bot, không phải Phase 7 bang hội trong roadmap cũ. Giữ Phase 6 reconnect/party/dungeon/duel; không thêm PvP, giao dịch hay bang hội mới.

## Audit trước triển khai — 28/10/2026

- Map: `world/MapCatalog.java`, `maps.json` có bốn map mở và hai map instance Phase 6, ground/platform một chiều và portal đơn giản. Thiếu validation/tham chiếu, vùng an toàn có dữ liệu, checkpoint, solid obstacles, spawn points và vùng luyện cấp riêng. `Movement.java` dùng tọa độ chân, server điều khiển.
- World/session: `GameWorld.java` giữ khóa chung, simulation 20 Hz, state broadcast 10 Hz theo map/instance. `CharacterSessions.java` có lease/proof, grace 20 giây và snapshot/version. Chưa có AOI theo khoảng cách hoặc delta entity.
- Quái: `combat/CombatCatalog.java`, `MonsterState.java`, `MonsterAi.java`, `combat.json` có ba loài và boss hai phase. Spawn/NPC/safe zone còn cố định trong world/AI. Thiếu group metadata, threat có hysteresis, leash timeout, navigation, telegraph và drop table cấu hình.
- Combat/progression: `CombatEngine.java` là pipeline dùng chung PvE/duel. `ProgressionRules.java` và progress JSON lưu quest/kill counters, inventory, cooldown, realm, chín thế lực. Giữ ownership qua `CharacterService`/JWT, ledger transaction dungeon và Flyway V1–V3.
- NPC/bot: Tiếp Dẫn Sứ là hình khối và interaction tại tọa độ cố định; chưa có bot runtime, brain, scripted dialogue hay solo training instance. Bot Phase 7 sẽ là entity AI riêng, không nằm trong danh sách player/online.
- Baseline thực thi: 34/34 backend unit tests; frontend TypeScript/Vite build thành công. Git bắt đầu từ commit Phase 6 `6a4404e`.

## Milestone A — ĐÃ KIỂM THỬ

`MapCatalog.java` chuẩn hóa map/portal/collision/spawn/safe zone/checkpoint/waypoint/nav reference; từ chối ID trùng, đích map/spawn không tồn tại, kích thước âm, spawn trong solid và reference sai. Bổ sung map **Thanh Vân Ngoại Sơn** (1–6), cổng tại Thanh Vân Thành x190, đường về thành x100, cổng tới Trung Châu x3030 yêu cầu cấp 6. Vật cản x1450 cao 90 world units, platform một chiều và trại nghỉ checkpoint x1970.

Giữ ID/cổng/bãi luyện của MVP. Thành có lõi an toàn và bãi luyện cũ bên phải; không âm thầm xóa quái/nhiệm vụ cũ chỉ vì thêm vùng luyện cấp mới. Các spawn cũ được chuyển nguyên vị trí/số lượng sang `monsterSpawnGroups` trong dữ liệu map. NPC Guide cũng lấy từ `npcSpawns`.

Một world unit tương ứng một pixel thiết kế; x tăng sang phải, y tăng xuống dưới, vị trí actor là tâm bàn chân, player hitbox rộng 32/cao 44, tốc độ 240 unit/s, gravity mặc định 1500 unit/s², jump velocity -570 unit/s. `MapCollision.java` xử lý sweep ngang, LOS qua solid; `Movement.java` đáp xuống ground/platform/obstacle và chặn trần solid. Dash dùng cùng sweep. Phaser chỉ vẽ cùng rectangle được server xuất, không quyết định vị trí hay tạo collision khác.

`USE_PORTAL {portalId}` và INTERACT cũ đều kiểm tra vị trí thật, level/quest/enabled/activity/capacity, cooldown 1 giây; server chọn target spawn. Chuyển map dưới world lock, gửi PLAYER_LEFT/JOINED và full WORLD_SNAPSHOT theo luồng cũ, thêm MAP_ENTERED; lỗi PORTAL_REJECTED có code. Client không chọn tọa độ.

Checkpoint/map version nằm trong progress JSON hiện hữu với giá trị mặc định cho dữ liệu cũ; **không cần Flyway mới**. CHECKPOINT_REACHED chỉ ghi khi checkpoint đổi. Trước join, map đã xóa/private hoặc vị trí đã thành solid/ngoài bounds được sửa về spawn hợp lệ. Reconnect còn lease giữ đúng world state; proof cũ sau lease hết hạn đưa về checkpoint an toàn.

GET `/api/world/maps`, GET `/api/world/maps/{mapId}` dùng JWT, chỉ trả metadata/config hình học, không có dữ liệu tài khoản, runtime instance hay loot seed. Frontend có adapter cho snapshot map tám trường cũ trong lúc cập nhật server/client; endpoint/message cũ giữ nguyên.

F3 chỉ bật trong Vite development: collision, portal bounds, safe zones, spawn groups, waypoint/nav edges, entity state và tick. Chỉ quan sát, không có quyền thay gameplay. HUD/minimap dùng map metadata và obstacle thật. Tắt debug trong production build.

Đã thực thi:

- 39/39 backend unit tests (giữ 34 bài cũ + 5 MapEngineTest); Docker build/test Java 21 thành công.
- TypeScript/Vite production build thành công.
- `node tests/maps.mjs`: bốn user/ba tộc, metadata/collision, từ chối portal ở xa và level thấp, hai người cùng field thấy cùng ID quái/chat, người khác map không thấy, walk/dash bị chặn bởi wall, jump vượt wall, checkpoint persist, ngắt 5 giây phục hồi đúng map và fallback về checkpoint.
- `node tests/multiplayer.mjs`: auth/ownership/vé/controller/cổng cũ/movement/chat/persistence đều PASS.
- Hai browser tab với hai tài khoản QA cùng Ngoại Sơn: HUD map/level/2 người, F3 vẽ collision/navigation, không có lỗi console. Ảnh local `.tools/phase7-A-ui.png`.

File chính: `world/MapCatalog.java`, `MapCollision.java`, `Movement.java`, `WorldMetadataController.java`, `GameWorld.java`, `maps.json`, `ProgressData.java`, `GameSocket.java`, `CombatEngine.java`, frontend `types.ts`, `world-debug.ts`, `game.ts`, `main.ts`, `game-shell.ts`; test `MapEngineTest.java`, `tests/maps.mjs`.

Để thử bằng hai tab: đăng nhập hai tài khoản Nhân Tộc, tới cổng x190 của Thanh Vân Thành, nhấn E; cùng vào Ngoại Sơn, nhấn F3 ở dev, thử wall/platform và trại nghỉ. Tab thứ ba ở Trung Châu không nhận field chat/entities. Các tộc khác vẫn đi cổng cũ tới Trung Châu.

## Milestone B — ĐÃ KIỂM THỬ

- `MonsterPopulation` tạo số slot giới hạn từ spawn group; mỗi quái có UUID riêng, definitionId, spawnGroupId, instanceId. Chết/hồi sinh dùng lại đúng slot/ID, không nhân bản. Tham chiếu loài và drop item được kiểm tra khi startup.
- `CombatCatalog` cấu hình NORMAL/ELITE/MINIBOSS/WORLD_BOSS và behavior/phase/ability/drop table. Giữ WOLF/SOLDIER/SPIRIT/BOSS cùng chỉ số/EXP/tu vi cũ; thêm Yêu Lang Đầu Đàn ở Ngoại Sơn và định nghĩa Thạch Vệ cho vùng khó. Boss trong dungeon hiển thị tier DUNGEON_BOSS mà không sao chép definition.
- `MonsterAi`: perception có LOS/safe zone/scope, threat theo damage, giữ target ít nhất 1,5 giây và chỉ đổi khi threat cao hơn 35%; nhóm quái gần cùng group có thể cảnh giác khi đồng loại bị đánh. Có ALERT/CHASE/ATTACK/CASTING/RETURN/DEAD/RESPAWN, leash và timeout truy đuổi. Hết mục tiêu/timeout quay về bằng navigation rồi hồi đầy HP, xóa threat/contribution. Trong grace nhân vật vẫn là mục tiêu hợp lệ, không bất tử.
- `MotionBody`, `Movement`, `MapCollision`, `Navigation` dùng chung vật lý; AI chỉ WALK qua đoạn có nền và không vướng solid, JUMP/DROP theo graph/capability. Không có route thì RETURN/dừng, không teleport xuyên tường. Brain nghĩ mỗi 200 ms, physics tick 50 ms, không tạo thread theo entity.
- Boss hai phase lấy dữ liệu, gửi BOSS_PHASE_CHANGED và BOSS_TELEGRAPH trước khi resolve. Hit kiểm tra lại vị trí, khoảng cách, safe zone, LOS và scope ở thời điểm resolve. Telegraph còn hiệu lực cũng nằm trong snapshot để client vào vùng muộn thấy cảnh báo. Phaser vẽ rectangle đúng footprint, không tính damage.
- `MonsterDefeats` phát MonsterDefeated đúng một lần mỗi vòng đời; EXP/tu vi vẫn qua PartyRewardPolicy và progression cũ. `LootRoller` dùng seed riêng server và drop table; personal loot có quantity, pickup nguyên tử với stack cap. Thêm metadata MATERIAL/QUEST và hai vật phẩm chuẩn bị nội dung; chưa có giao dịch.
- World nhóm actor theo map/instance một lần mỗi tick; quái map vắng người vẫn có thể return/respawn. Chỉ checkpoint khi có thay đổi, không ghi DB mỗi tick. Metadata GET `/api/world/monsters` không trả drop table/seed/threat.

Đã chạy: **46/46 unit tests** (7 EcologyTest mới: group slots/ID/respawn, threat/hysteresis, leash/reset, LOS/safe zone, phase/telegraph-before-damage, WALK/JUMP/DROP và seed/drop quantity); frontend build; `tests/ecology.mjs`, `tests/combat.mjs`, `tests/party.mjs`; `tests/dungeon.mjs` trên QA 8081 với hai party/ba wave, boss thật, assertions phase/telegraph trước damage, cách ly và grant một lần. Có WARN socket đã đóng khi cleanup đồng thời, như giới hạn transport Phase 6; không có lỗi FSM/scheduler trong lần kiểm tra log.

Không cần migration mới: config JSON và runtime trong RAM, kill/checkpoint map bổ sung vào progress JSON cũ bằng field có default. Source chính thêm `MonsterPopulation.java`, `MonsterDefeats.java`, `LootRoller.java`, `MotionBody.java`, `Navigation.java`, mở rộng `CombatCatalog.java`, `MonsterState.java`, `MonsterAi.java`, `GameWorld.java`, data combat/maps/progression và frontend debug/telegraph.

## Milestone C — ĐÃ KIỂM THỬ

`bots.json` có BotDefinition/Stats/BehaviorProfile/Dialogue/Difficulty; `BotCatalog` kiểm tra reference/skill/race/profile/route/dialogue. Map chỉ tham chiếu definition/spawn/route. GUARD tuần tra, perception/LOS/leash, chỉ chọn MonsterState thù địch, dùng Combat Engine chung và rút về khi HP thấp; WANDERER đi waypoint và dừng TALK với thoại designer; CHALLENGER cho ba tộc mở bài luyện riêng khi người chơi chủ động chọn EASY/HARD. TRAINER/ESCORT/SCOUT chưa làm, dành cho milestone sau.

`BotRegistry` giữ runtime AI riêng; actor composition dùng PlayerState.ai (không tạo user/JPA character, không vào CharacterSessions). Snapshot có bots[] và isAI=true, attitude/HP/MP/state, nhãn [AI] và hình khối; players[] và số online chỉ là người thật. AI nghĩ theo interval, vật lý mỗi tick; không dùng ML/LLM hoặc API bên ngoài. NPC chết giữ đúng ID/slot và respawn theo dữ liệu; practice NPC được xóa hoàn toàn khi kết thúc.

`TrainingService` tạo arena cá nhân, chuẩn bị 3 giây, giới hạn 3 phút; EASY/HARD khác stats/tốc độ/reaction interval công khai từ cấu hình. Cả hero và NPC dùng chung skill validation, MP/cooldown/range/LOS/crit/buff và physics. Chính sách target yêu cầu cùng training instance và một phía là AI; không biến thành PvP ngoài arena. Không award EXP/tu vi/loot/quest credit, không dùng hoặc mất vật phẩm. Khi thắng/thua/hủy/timeout/logout/hết grace: hủy run, xóa NPC/instance, trả hero về map cũ với đầy HP/MP, giữ cooldown hợp lệ. Reconnect trong grace phục hồi đúng NPC/run; kết quả tạm giữ 60 giây. Không cần bảng DB cho trận tập không phần thưởng.

WS mới: INTERACT {entityId?}, DIALOGUE_STARTED, NPC_LIST, START_TRAINING {botEntityId,difficulty,requestId}, CANCEL_TRAINING {requestId}, TRAINING_STATE_REQUEST, TRAINING_STARTED/STATE/RESULT/ERROR. Handler dùng controller đã xác thực và request receipt Phase 6; client không gửi damage/winner/reward. NPC ID không xin được connection ticket hoặc truy cập như nhân vật sở hữu. Snapshot/role scene giữ tương thích.

Frontend: Chức năng → NPC AI / Đối luyện, nút trò chuyện, hai độ khó, countdown, HP đối thủ, HUD hủy/kết quả. Sửa rerender bảng theo dữ liệu hiển thị để nút không biến mất mỗi 100 ms; không giảm tần suất motion. Sửa sequence input tiếp tục tăng qua map snapshot của cùng controller, tránh bị server bỏ qua input sau chuyển map. F3 thêm state/perception NPC và thời gian xử lý tick.

Đã chạy: **52/52 unit tests** (thêm 5 BotTrainingTest và một regression AI hoàn thành JUMP trước khi đánh); frontend production build; `tests/bots.mjs` với 4 user/3 tộc: script dialogue khi NPC di chuyển, chặn remote ID/friendly damage/NPC ticket, hai practice instance đồng thời, stats EASY/HARD, countdown, start/cancel dedup, resume 2 giây cùng NPC, thắng bằng input/skill thật, inventory/EXP/tu vi giữ nguyên, logout dọn và xem kết quả sau nối lại. Hai browser tab Nhân/Ma bắt đầu EASY/HARD từ UI: ID instance khác nhau, một người thật + một NPC AI; đứng yên thua đúng luật và trở về map cũ với HP/MP đầy, không tăng EXP; mở trận rồi hủy qua UI thành công, không có console error. Ảnh local `.tools/phase7-C-ui.png`.

File chính: package `bot/` (BotCatalog, BotState, BotBrain, BotRegistry, TrainingService), `bots.json`, map spawn data, shared PlayerState/CombatEngine/TargetPolicy/GameWorld/GameSocket/NetworkMetrics; client `npc-ui.ts` và Phaser BotView. Không thêm Flyway migration.

## Milestone D — ĐÃ KIỂM THỬ

Nội dung hoàn chỉnh trong config: ba field Thanh Vân Ngoại Sơn/Huyết Vụ Cốc/Linh Thú Sơn Lộ (1–6), Tam Giới Biên Cảnh (6–12), Thiên Môn Di Tích (10–20). Tổng 12 map gồm 4 town, 5 field, dungeon/duel/training; 64 slot quái mở và 21 NPC AI. Thêm SOLDIER_VETERAN/SPIRIT_VETERAN/RUIN_SPIRIT cùng elite/miniboss/boss; bảy quest theo vùng với requiredLevel 1/3/4/6/8/10/10. Lv10 ruins là hard gate, khoảng cấp đề xuất của vùng không tự động khóa mọi cổng; cổng Biên Cảnh từ Trung Châu vẫn cho khám phá cấp 1. Dungeon Huyền Mộc giữ điều kiện party cấp 5 của Phase 6, dù vùng đề xuất 10–20.

Quest KILL mới lấy mốc definition hoặc map:definition lúc nhận; reward pipeline tăng đúng một lần khi có phần thưởng hợp lệ. WOLVES/PATH và save cũ được giữ. Guide proximity/điểm hiển thị lấy từ npcSpawns của map, không còn tọa độ cố định trong logic progression. Startup validate quest target/map/portal quest cùng monster/bot references; kiểm tra tất cả slot spawn nội suy, không chỉ hai đầu group. Không thêm Flyway; giữ V1–V3 nguyên vẹn.

`WorldInterest` lọc x/y trong scope, luôn có self, personal loot giữ owner. Protocol 3 AUTH xác nhận delta support, snapshot tạo interestVersion rồi ENTITY_DELTA chứa base/version và bốn tập upsert/remove. Record bất biến so sánh state, không gửi lại entity đứng yên không đổi. Ra AOI/chết/xóa/đổi scope được remove hoặc full baseline; thay connection xóa frame cũ. Player/monster/NPC/loot đều dùng cùng cơ chế. Sự kiện combat lọc theo vị trí; CHAT map-wide, party/control/activity gửi theo quy tắc riêng. Protocol 1/2 còn nhận ENTITY_UPDATE đầy đủ trong AOI.

Client `InterestState` ghép delta thành state dùng lại renderer hiện có, yêu cầu đúng base/map/instance, khóa gameplay và xin RESUME_WORLD một lần khi mismatch. Không replay lệnh cũ; full snapshot thay cache, phiên cũ/epoch/version bị loại. UI Map dùng metadata và connections thật, hiện cấp/quest/locked condition; minimap/scene vẽ entity trong AOI. NPC vẫn có nhãn [AI], không thành tài khoản/người online.

Đã thực thi trên bản D:

- **57/57 backend unit tests**, gồm WorldInterestTest 3 và WorldContentTest 2 mới; Docker Java 21 build/test thành công. Transport **7/7** và TypeScript/Vite production build đạt.
- `tests/interest.mjs`: 4 client thật, 3 field theo tộc, v3 + v2 compatibility, player/NPC đi ra và vào lại AOI, remove/upsert đúng ID/không trùng, resync baseline, map chat, 2 giây reconnect không tăng frame cache, private training/return không leak.
- `tests/world-content.mjs` trên QA local: quest OUTER_WOLVES bỏ qua kill thật trong thành, tính đủ 4 kill field sau nhận, claim một lần; cổng ruins cấp 10/return spawn Biên Cảnh và quest/map persistence.
- Hồi quy thật `multiplayer`, `combat`, `maps`, `ecology`, `party`, `bots`, `duel`, `reconnect`, `production`; `progression` và `dungeon` trên QA 8081 đều đạt. Assertion reconnect sửa để so UUID của slot cùng thấy trong hai AOI động, không giả định danh sách toàn map hoặc spawn x180 hardcode. Không nới các rule gameplay.
- Browser bản D: mở bảng Map thấy vùng/cổng/cấp lấy từ server, đi cổng E sang Ngoại Sơn, HP/MP/HUD/minimap/NPC AI hiện đúng và console không có warn/error. Ảnh local `.tools/phase7-D-map-ui.png` và `.tools/phase7-D-world-ui.png`; test hai browser và hai arena đồng thời đã ghi ở A/C.

`tests/load-world.mjs` đã đạt với **20 client / 6 party / 3 field / 301,64 giây**, 64 monster slots và 21 NPC. Không error command/unexpected disconnect, mọi nhân vật nhận EXP thật; tick 20,0007 Hz, mean simulation 0,52995 ms, 0 slow tick; CPU mẫu trung bình 19,70%, RAM cao nhất 352,9 MiB. Network thực nhận 292.167.214 byte, 48,43 kB/s/client. Dọn 20 session/AOI và 6 party, giữ người online sẵn. Phạm vi, cách tính và giới hạn counter ở `PHASE7_PERFORMANCE.md`.

File chính D: `WorldInterest.java`, metadata/metrics/session/world/socket, maps/combat/progression/bots JSON, `ProgressionCatalog/Rules`, frontend network/progression panel, WorldInterestTest/WorldContentTest, `tests/interest.mjs`, `world-content.mjs`, `load-world.mjs`. Hướng dẫn dữ liệu: `WORLD_AUTHORING.md`; API/WS: `PROTOCOL.md`.

## Giới hạn và cách thử

- Một process/world lock; AOI giảm bytes/rendering, không chuyển world thành distributed hoặc ngừng mô phỏng map xa. Tick metrics không gồm thời gian chờ khóa và broadcast. Save queue tuần tự, REST progression đợi commit; DB chậm vẫn có thể gây pause. Chưa claim tải production/hàng nghìn người.
- Geometry/navigation placeholder và graph designer; không tự suy đường đi cho mọi địa hình. Config cần restart, không hot reload. Giữ sprite Kiếm Tu; không thêm art/VFX cầu kỳ.
- Role TRAINER/ESCORT/SCOUT, guild/economy/PvP mới chưa làm theo scope prompt này. Runtime practice không lưu DB, server restart không tiếp tục trận; kết quả RAM 60 giây. Checkpoint định kỳ 15 giây vẫn có cửa sổ mất tiến trình nếu crash.
- Bãi luyện cũ trong town sau x420 là chủ ý tương thích. Safe zone chặn sát thương quái theo chính sách AI; giữ hành vi cast của MVP. Không xem đó là vùng PvP.
- Có thể gặp WARN send vào socket vừa đóng trong cleanup đồng thời. Có ghi counter exceptions tích lũy; báo cáo tải so trước/sau để phân biệt lỗi trong đo và lỗi cũ, không khẳng định counter luôn 0.
- Hai tài khoản khác nhau: dùng cổng x190 vào cùng field, di chuyển hơn 900 ngang rồi quay lại, thấy avatar/NPC biến mất/xuất hiện đúng; chat vẫn nhận trong cùng map. Vào Map xem các cổng mới. Ở gần CHALLENGER trong thành, mỗi tab chủ động EASY/HARD; hai arena có ID khác, không thấy đối thủ của tab kia, hủy trả về đúng map. F3 chỉ ở Vite development; không thay quyền gameplay.
