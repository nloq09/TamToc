import {races,type Character} from './types';
const esc=(s:string)=>s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));

export function gameShell(c:Character){return `
<main class="game-layout">
 <section class="viewport" aria-label="Thế giới Tam Tộc">
  <div id="world"></div>
  <button class="player-hud" id="character-button" aria-label="Thông tin nhân vật">
   <span class="portrait ${c.race.toLowerCase()}"><span>${races[c.race].name.slice(0,1)}</span></span>
   <div><strong>${esc(c.name)} <small>Lv.${c.level}</small></strong><span>${races[c.race].name}</span>
    <div class="bar hp"><span></span><small>HP · Đang tải</small></div><div class="bar mp"><span></span><small>MP · Đang tải</small></div>
   </div>
  </button>
  <div class="world-top"><div><span class="eyebrow">TAM TỘC · NGHỊCH THIÊN</span><h2 id="map-name">Đang kết nối…</h2></div><div class="world-meta"><small id="map-detail"></small><span id="population">0 đạo hữu</span><span id="connection-status">Đang kết nối</span></div></div>
  <div class="target-hud" id="target-hud" hidden><span>YÊU VẬT GẦN NHẤT</span><strong id="target-name"></strong><div class="bar"><span id="target-fill"></span><small id="target-hp"></small></div></div>
  <button id="menu-button" class="round-button menu-button" title="Mở bảng chức năng" aria-label="Mở menu nhân vật">☰</button>
  <div class="map-hud"><canvas id="minimap" width="260" height="74" aria-label="Bản đồ vị trí người chơi"></canvas><button id="map-button" title="Bản đồ [M]">Bản đồ <kbd>M</kbd></button></div>
  <aside class="quest-tracker"><div class="panel"><div class="eyebrow">NHIỆM VỤ THEO DÕI</div><h3>Tu luyện nhập môn</h3><p>Đến gặp Tiếp Dẫn Sứ để nhận nhiệm vụ.</p><div class="objective">◇ Khám phá Tam Giới</div></div></aside>
  <div class="world-caption">A / D di chuyển · SPACE nhảy · E tương tác</div>
  <div class="chat-panel"><div class="chat-title">KÊNH <select id="chat-channel" aria-label="Kênh chat"><option value="AREA">Khu vực</option><option value="PARTY">Tổ đội</option></select><button id="toggle-chat" aria-label="Thu gọn chat" aria-expanded="true">−</button></div><div id="chat-log" role="log" aria-live="polite"></div><form id="chat-form"><input name="message" id="chat-input" maxlength="200" autocomplete="off" aria-label="Tin nhắn khu vực" placeholder="Enter để trò chuyện…"><button aria-label="Gửi tin nhắn">↗</button></form></div>
  <div class="controls-panel"></div><div id="training-hud" class="activity-hud" hidden></div><button id="npc-notice" class="activity-notice" style="bottom:175px" hidden></button><div id="duel-hud" class="activity-hud" hidden></div><button id="duel-notice" class="activity-notice" hidden></button><div id="activity-hud" class="activity-hud" hidden></div><button id="activity-notice" class="activity-notice" hidden></button><div id="party-hud" class="party-hud" hidden></div><button id="party-notice" class="party-notice" hidden></button>
  <div id="network-banner" class="network-banner" role="status" hidden></div>
  <button class="round-button interact-button" id="interact-button" title="Tương tác [E]">E<small>Tương tác</small></button>
  <p id="error" class="error game-toast" role="alert" hidden></p>
  <dialog id="info-dialog" aria-label="Bảng nhân vật" tabindex="-1">
   <div class="drawer-profile"><span class="portrait ${c.race.toLowerCase()}"><span>${races[c.race].name.slice(0,1)}</span></span><div><strong>${esc(c.name)}</strong><span id="drawer-level">${races[c.race].name} · Cấp ${c.level}</span><span id="drawer-vitals">Đang tải chỉ số…</span><span id="drawer-realm">Luyện Khí Sơ Kỳ</span></div><button id="close-dialog" aria-label="Đóng bảng nhân vật">×</button></div>
   <nav class="drawer-tabs" aria-label="Chức năng nhân vật"><button data-panel="quests">Nhiệm vụ</button><button data-panel="inventory">Hành trang</button><button data-panel="skills">Kỹ năng</button><button data-panel="cultivation">Tu luyện</button><button data-panel="functions">Chức năng</button></nav>
   <div id="dialog-content"></div><div class="drawer-wallet"><span>◈ <strong id="wallet-stones">—</strong> linh thạch</span><button id="drawer-map">Bản đồ ↗</button></div>
  </dialog>
 </section>
</main>`;}
