# Giao thức Phase 6

Giữ REST/JWT và các message MVP. Client gửi `{type, payload}`; lệnh có tác động tổ đội/start/duel thêm `requestId` UUID ở root hoặc payload. Server trả `{type,payload,worldEpoch,stateVersion}`. `stateVersion` tăng theo event toàn world, có khoảng trống giữa người nhận; không phải số frame liên tiếp. `worldTick` tăng ở tick 20 Hz. Broadcast 10 Hz, chỉ cùng `(mapId,instanceId)`; `instanceId=null` là map chung.

## Kết nối

1. POST `/api/characters/{id}/connection-ticket`, Bearer JWT chủ nhân. Vé 30 giây, dùng một lần.
2. Mở `/ws`, message đầu `AUTH {ticket,protocolVersion:2,resumeToken?}`. Token không nằm trong URL.
3. `AUTH_RESULT` trả `characterId,connectionId,worldEpoch,resumed,resumeToken`. Khóa phục hồi bí mật 32 byte, chỉ lưu sessionStorage theo nhân vật. Nó chỉ dùng cùng vé ownership hợp lệ; không thay JWT.
4. Nhận `WORLD_SNAPSHOT` đầy đủ trước khi nhận delta. `RESUME_WORLD {}` xin lại snapshot; client không được chọn character/map/instance. Snapshot chứa `map,selfId,players,monsters,loot,serverTime,worldTick,mapId,instanceId`.
5. `PING {}` mỗi 2 giây → `PONG`. Mất heartbeat server 15 giây đóng socket; mất mạng được giữ lease 20 giây, vẫn chịu sát thương, không nhận input. Client backoff 0.5–5 giây + jitter, tối đa 12 lần, vé mới mỗi lần, timeout REST 5 giây; không replay gameplay. Snapshot xóa VFX và trạng thái input tạm.
6. `LEAVE_WORLD {}` kết thúc ngay, bỏ proof và trạng thái tổ đội. Refresh/pagehide giữ proof; thế giới vẫn mô phỏng trong grace. Server restart tạo epoch mới, proof cũ được fallback vào join an toàn. Protocol 1 vẫn dùng AUTH cũ và dọn ngay khi disconnect.

Một nhân vật có một controller. Vé mới thiếu proof đúng bị `CHARACTER_ALREADY_CONNECTED`; proof đúng có thể thay socket nửa mở, socket cũ nhận close 4001 và không được xóa controller mới khi callback close tới muộn. Client loại frame sai epoch/version và callback từ socket cũ.

## Lệnh gameplay giữ nguyên

- `INPUT {direction:-1|0|1,jump:boolean,sequence:integer>=0}`. Không nhận x/y từ client. Sequence reset sau binding mới.
- `ATTACK {}` hoặc `CAST_SKILL {skillId}`. `targetCharacterId` nếu có chỉ hợp lệ trong duel và phải là đối thủ. Damage/HP/winner do server quyết định.
- `INTERACT {}`: nhặt personal loot hoặc NPC/cổng; duel bị chặn, dungeon chỉ nhặt loot.
- `CHAT {message}`: scope map + instance. `PARTY_CHAT {text}`: chỉ thành viên tổ đội, dùng event `CHAT` với `channel=PARTY`.

## Tổ đội

- `NEARBY_PLAYERS_REQUEST {}`, `PARTY_STATE_REQUEST {}` → `NEARBY_PLAYERS`, `PARTY_UPDATED`.
- Có requestId: `PARTY_INVITE {targetCharacterId}`, `PARTY_ACCEPT {invitationId}`, `PARTY_DECLINE {invitationId}`, `PARTY_LEAVE {}`, `PARTY_KICK {targetCharacterId}`, `PARTY_TRANSFER_LEADER {targetCharacterId}`.
- Event: `PARTY_INVITATION_RECEIVED {id,inviterId,name,expiresAt}`, SENT/RESOLVED/DECLINED/EXPIRED; `PARTY_UPDATED {inParty,id,leaderId,members,maxSize,revision}`; `PARTY_DISBANDED {reason}`. Thành viên chứa chỉ số HP/MP, tộc, level, scope, trạng thái kết nối; không chứa tài khoản/JWT.
- Leader được mời/đuổi/nhường quyền. Rời hoặc hết grace chuyển trưởng đội tới UUID nhỏ nhất trong số thành viên online, fallback thành viên còn lại. Party có thể tạm 1 người trong lúc mời; không được vào dungeon một người. Tối đa 4, TTL lời mời 60 giây. Party tạm giữ trong RAM.
- Reward: đóng góp + đồng đội đủ điều kiện, sống/online, cùng scope, bán kính 800, hoạt động trong 10 giây. Chia đều ngân sách EXP/tu vi quái; phần dư theo UUID. Personal loot chỉ chủ nhân thấy/nhặt. Cờ rewardProcessed chỉ reset khi quái respawn.

## Phó bản

- `DUNGEON_LIST_REQUEST {}` → `DUNGEON_LIST`: điều kiện, tọa độ cổng và danh sách READY.
- `DUNGEON_READY_SET {ready:boolean}`; revision party thay đổi làm mất READY. Bắt đầu kiểm tra lại toàn bộ đội.
- `DUNGEON_START_REQUEST {dungeonId:"HUYEN_MOC",requestId}`. Leader, nhóm 2–4, cấp 5, còn sống, online, gần x350 ±150 Trung Châu, tất cả READY. Không nhận instanceId từ client.
- `DUNGEON_INSTANCE_CREATED`, `DUNGEON_WAVE_STARTED`, `DUNGEON_PROGRESS_UPDATED` chứa `runId,instanceId,state,wave,totalWaves,remainingMs,wipes,reason,participants`.
- `DUNGEON_FINISHED {run,rewards}`: rewards theo chính người nhận; không có API tự nhận lại. `DUNGEON_LEAVE_REQUEST {}` → `DUNGEON_LEFT` và snapshot map chung.
- 3 wave, cuối BOSS dùng AI chung; không respawn quái đã chết trong instance. 15 phút, một wipe làm lại wave sau 4 giây, wipe thứ hai fail. Grace giữ suất; dưới hai suất fail. Terminal instance dừng AI, dọn sau 60 giây.
- Khi boss chết, chốt eligibility online/đúng instance/có hoạt động trong 10 giây/chưa rời; retry DB giữ grant bất biến. Ledger khóa run, unique `(run,character,reward_key)` và cập nhật progress trong cùng transaction. Trong lúc commit chưa xác định, checkpoint/REST mutation của người có thưởng bị chặn để không ghi đè grant; retry 5 giây. Sau commit mới báo COMPLETED.

## Duel 1v1

- `DUEL_STATE_REQUEST {}` → `NEARBY_PLAYERS` + trạng thái trận/lời mời của chính người yêu cầu.
- Có requestId: `DUEL_CHALLENGE {targetCharacterId}`, `DUEL_ACCEPT {duelInvitationId}`, `DUEL_DECLINE {duelInvitationId}`, `DUEL_FORFEIT {}`.
- Event: `DUEL_CHALLENGE_RECEIVED {id,challengerId,name,expiresAt}`, `DUEL_CHALLENGE_SENT`, `DUEL_DECLINED`, `DUEL_INVITATION_RESOLVED`, `DUEL_INVITATION_EXPIRED`.
- `DUEL_PREPARE`, `DUEL_STARTED`, `DUEL_STATE`, `DUEL_FINISHED` chứa `duelId,instanceId,state,challengerId,opponentId,winnerCharacterId|null,reason,secondsRemaining,durationMs`.
- Lời mời 30 giây, cùng map mở/cách nhau ≤800, còn sống/online/chưa ở hoạt động riêng. Chấp nhận mới tạo arena. Countdown 5 giây; combat chỉ ACTIVE. Tái sử dụng `SKILL_CAST`/`COMBAT_EVENT`, cùng pipeline MP/cooldown/hitbox/defense/crit/buff, thêm PvPDamageModifier mặc định 1.0. Không PvP ngoài arena.
- Kết thúc: `KNOCKOUT`, `FORFEIT`, `LOGOUT`, `DISCONNECT` sau 15 giây, `TIMEOUT` sau 180 giây (hòa). Lưu unique duel UUID; nếu DB lỗi giữ FINISHING, cấm combat và thử lại. Sau lưu, hai người được trả về vị trí trước trận, kể cả khi socket đang mất; HP/MP đầy, xóa buff tạm, không thay đổi EXP/tu vi/trang bị/vật phẩm. Không có phần thưởng duel.

## Lỗi, chống lặp và metrics

`ERROR`, `PARTY_ERROR`, `DUNGEON_ERROR`, `DUEL_ERROR` có `{code,message,retryable}`. Ví dụ: `INVALID_MESSAGE`, `INVALID_ID`, `INVALID_REQUEST_ID`, `REQUEST_ID_REUSED`, `CHARACTER_ALREADY_CONNECTED`, `INVALID_RESUME_REQUEST`, `PARTY_FULL`, `INVITE_EXPIRED`, `LEADER_REQUIRED`, `DUNGEON_NOT_READY`, `DUNGEON_ENTRY_REQUIRED`, `DUEL_BUSY`, `DUEL_NOT_ACTIVE`, `PVP_NOT_ALLOWED`, `INVALID_TARGET`, `SKILL_COOLDOWN`, `REWARD_PENDING`, `RATE_LIMITED`.

`COMMAND_RESULT {requestId,status:"OK",replayed:boolean}` xác nhận lệnh có requestId. Receipt thành công giữ 15 phút, tối đa 10.000 trong RAM, ràng buộc actor + request UUID + fingerprint. Lặp cùng lệnh không thực thi lại; tái sử dụng ID cho payload khác bị chặn. Receipt tạm không thay unique ledger DB.

GET `/api/network/metrics` (JWT) trả online/grace, activeParties, activeInstances, allocatedInstances, disconnects/resumes/resumeFailures/exceptions, outgoingMessages/outgoingBytes, ticks/meanTickMs/maxTickMs/slowTicks (tích lũy từ lúc server chạy). GET `/api/characters/{id}/activities` kiểm tra ownership, trả tối đa 20 dungeon và 20 duel gần nhất.

## Phase 7 — protocol 3 và vùng quan sát

Frontend gửi `AUTH {ticket,protocolVersion:3,resumeToken?}`. Giữ nguyên vé một lần/proof/epoch/envelope stateVersion của Phase 6. AUTH_RESULT xác nhận protocolVersion. Protocol 1/2 được hỗ trợ bằng ENTITY_UPDATE đầy đủ **trong AOI**, không còn danh sách toàn map. Chat khu vực vẫn cùng map/instance, party/control/activity giữ scope riêng.

`WORLD_SNAPSHOT` thêm `interestVersion`, map metadata, `bots`, `tickDurationMs`. Baseline chứa players/monsters/bots/loot trong ±900 ngang/±550 dọc (config server), luôn có self; loot còn kiểm tra ownership. `mapId` và `instanceId` nằm trong mọi state. Số đạo hữu trên HUD là số người thấy trong AOI.

Sau baseline protocol 3 nhận `ENTITY_DELTA`: `baseInterestVersion`, `interestVersion` kế tiếp, `mapId`, `instanceId`, tick/worldTick/serverTime/tickDurationMs; bốn trường players/monsters/bots/loot đều có `{upsert:[],remove:[]}`. Upsert chứa nguyên view của entity mới hoặc thay đổi; remove chứa ID ra AOI/đã xóa. Entity state/FSM/telegraph thay đổi nằm trong upsert, không cần stream riêng MONSTER_STATE_CHANGED/BOT_STATE_CHANGED. Snapshot mới thay thế toàn bộ bốn tập. Client ghép thành state trước khi render, không tự đoán delta mất base. Mismatch → khóa gửi gameplay, gửi RESUME_WORLD một lần, chờ full snapshot rồi tiếp tục. Không replay skill/chat. Chuyển map/instance và reconnect luôn có baseline mới. Input sequence tiếp tục tăng qua snapshot của cùng controller; binding mới server reset sequence.

Sự kiện chiến đấu có vị trí lọc cùng AOI. CHAT map-wide; party/run/connection events là control plane, không dùng để tạo entity hiện trên scene. Quái vẫn mô phỏng ngoài AOI. Cache delta theo connection được xóa khi close; callback cũ không xóa baseline/controller mới.

## Map, quái và NPC

- JWT GET `/api/world/maps`: summary vùng, khoảng cấp đề xuất, cổng và điều kiện; `/maps/{mapId}`: geometry/collision/spawn/nav; `/monsters`: id/name/level/tier. Không trả loot seed/drop table hoặc dữ liệu tài khoản.
- `USE_PORTAL {portalId}` hoặc INTERACT rỗng gần cổng. Server kiểm tra scope, alive/activity, triggerBounds, level/quest, enabled/capacity/cooldown 1 giây; tự chọn targetSpawn. `MAP_ENTERED`, WORLD_SNAPSHOT, PLAYER_LEFT/JOINED theo scope; chưa thêm MAP_LEFT riêng. `PORTAL_REJECTED {code,message}` cho lỗi cổng.
- `CHECKPOINT_REACHED {mapId,spawnId}` thông báo save checkpoint thay đổi. Reconnect trong grace giữ vị trí/runtime; fresh join sanitize save không hợp lệ hoặc fallback về checkpoint khi proof cũ hết lease.
- `MONSTER_DEFEATED {eventId,entityId,monsterDefinitionId,mapId,instanceId,ordinal,timestamp,x,y}` chỉ một lần mỗi vòng đời. `BOSS_PHASE_CHANGED` và `BOSS_TELEGRAPH` có vị trí/scope; telegraph cũng nằm trong monster view tới khi resolve. Hit resolve kiểm tra lại vị trí thật. Personal loot view có quantity; INTERACT nhặt nguyên tử.
- `bots[]` là AI registry riêng: id/isAI/definitionId/name/botType/race/factionId/attitude/mapId/instanceId/x/y/HP/MP/level/facing/state/interactable/interactionRadius/perception/leashRadius/homeX/homeY. Không có user/controller/ticket, không tăng onlinePlayers. Frontend ghi [AI].
- `TRAINING_STATE_REQUEST {}` → `NPC_LIST` với payload là mảng bot trong scope/AOI, kèm trạng thái bài tập gần nhất nếu có. `INTERACT {entityId}` → `DIALOGUE_STARTED {entityId,name,isAI,botType,lines,canTrain}`; phải ở gần NPC hợp lệ. INTERACT không có entityId vẫn giữ cổng/loot/Guide cũ.

## Bài đối luyện với AI

`START_TRAINING {botEntityId,difficulty:"EASY"|"HARD"}` và `CANCEL_TRAINING {}` dùng requestId theo receipt chung. `TRAINING_STATE_REQUEST {}` đọc lại trạng thái/kết quả gần nhất. Server kiểm tra CHALLENGER/consent/phạm vi/không bận trước khi tạo instance, không nhận mapId/winner/damage từ client.

`TRAINING_STARTED`, `TRAINING_STATE` chứa runId/instanceId/botId/name/difficulty/state/reason/secondsRemaining/rewardGranted; chuẩn bị 3 giây, tối đa 180 giây. Sau ACTIVE dùng ATTACK/CAST_SKILL chung, targetCharacterId nếu có phải là đối thủ AI. Không có TRAINING_ACTION tính damage riêng. Hai instance tách hoàn toàn.

`TRAINING_RESULT` có reason VICTORY/DEFEAT/CANCELED/TIMEOUT/LOGOUT/ABANDONED và `rewardGranted:false`. Trả về anchor với HP/MP đầy, giữ cooldown, xóa NPC/instance; không thay EXP/tu vi/inventory/quest. Grace 20 giây; còn lease thì nối lại cùng opponent, hết hạn ABANDONED và dọn run. Kết quả RAM giữ 60 giây, không ghi lịch sử activity DB. TRAINING_ERROR có code/message/retryable; chưa hỗ trợ TRAINER/ESCORT/SCOUT.

Metrics thêm `deltaClients`, `interestConnections`, `npcEntities`, `trainingNpcEntities`, `monsterSlots`, `aliveMonsters`. Tick duration đo thời gian thân simulate; không bao gồm chờ world lock, DB của REST, broadcast hoặc thời gian chờ scheduler. Khi phân tích tải cần lấy hiệu counters/ticks trước-sau cùng CPU/RAM và bytes thực nhận ở client.
