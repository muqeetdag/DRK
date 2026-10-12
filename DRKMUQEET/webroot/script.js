const $=s=>document.querySelector(s);const endpoint=a=>`/cgi-bin/control?action=${a}`;
const dash=v=>(v===null||v===undefined||v==='')?'—':v;
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const level=q=>q<0?0:Math.max(1,Math.min(4,Math.ceil(q/25)));
async function get(a){return fetch(endpoint(a),{cache:'no-store'}).then(r=>r.json())}
async function stats(){try{const s=await get('stats');$('#profile').textContent=s.profile||'balanced';$('#battery').textContent=s.battery??'—';$('#thermal').textContent=s.temperature??'—';$('#uptime').textContent=s.uptime??'—'}catch(e){$('#profile').textContent='Dashboard unavailable'}}
function paintWifi(w){const on=w.enabled==='on';const radio=$('#wifi-radio');radio.textContent=on?'On':(w.enabled==='off'?'Off':'Unknown');radio.dataset.state=w.enabled;
  $('#wifi-ssid').textContent=(w.ssid&&w.ssid!=='unknown')?w.ssid:(on?'Not connected':'Wi-Fi off');
  $('#wifi-state').textContent=dash(w.state);$('#wifi-ip').textContent=dash(w.ip);$('#wifi-gateway').textContent=dash(w.gateway);
  $('#wifi-dns').textContent=dash(w.dns);$('#wifi-band').textContent=dash(w.band);$('#wifi-security').textContent=dash(w.security);$('#wifi-mac').textContent=dash(w.mac);
  $('#wifi-link').textContent=w.link_speed==null?'—':`${w.link_speed} Mbps`;$('#wifi-rssi').textContent=w.rssi==null?'—':`${w.rssi} dBm`;
  const q=w.quality==null?-1:w.quality;$('#wifi-quality').textContent=q<0?'—':`${q}%`;$('#wifi-bars').dataset.level=level(q);
  $('#wifi-toggle').textContent=on?'Disable Wi-Fi':'Enable Wi-Fi'}
async function wifiStatus(){try{paintWifi(await get('wifi-status'))}catch(e){$('#wifi-ssid').textContent='Wi-Fi status unavailable'}}
function row(inner){return `<li>${inner}</li>`}
function bars(q){return `<span class="sig" data-level="${level(q)}"><i></i><i></i><i></i><i></i></span>`}
async function wifiList(action){const panel=$('#wifi-list');panel.hidden=false;$('#wifi-list-title').textContent=action==='wifi-scan'?'Nearby networks':'Saved networks';$('#wifi-results').innerHTML=row('<span class="loading">Reading…</span>');
  try{const d=await get(action);const nets=d.networks||[];
    if(action==='wifi-scan'){$('#wifi-results').innerHTML=nets.length?nets.map(n=>row(`<div><strong>${esc(n.ssid)}</strong><small>${esc(n.security||'open')} · ${esc(n.band||'?')}${n.frequency?` · ${n.frequency} MHz`:''}</small></div>${bars(n.quality)}<em>${n.rssi==null?'—':esc(n.rssi)+' dBm'}</em>`)).join(''):row(`<span class="loading">${esc(d.message||'No scan results reported by this ROM.')}</span>`)}
    else{$('#wifi-results').innerHTML=nets.length?nets.map(n=>row(`<div><strong>${esc(n.ssid)}</strong><small>network id ${esc(n.id)}</small></div><button class="danger" data-forget="${esc(n.id)}">Forget</button>`)).join(''):row(`<span class="loading">${esc(d.message||'No saved networks reported by this ROM.')}</span>`);
      document.querySelectorAll('[data-forget]').forEach(b=>b.onclick=async()=>{b.disabled=true;try{await get('wifi-forget&id='+b.dataset.forget)}finally{await wifiList('wifi-saved')}})}}
  catch(e){$('#wifi-results').innerHTML=row('<span class="loading">Console unavailable.</span>')}}
document.querySelectorAll('button[data-action]').forEach(b=>b.onclick=async()=>{b.disabled=true;const a=b.dataset.action;
  try{await get(a);if(a.indexOf('wifi-')===0){await wifiStatus();if(a==='wifi-scan'||a==='wifi-saved')await wifiList(a)}else{await stats()}}finally{b.disabled=false}});
stats();wifiStatus();setInterval(stats,5000);setInterval(wifiStatus,20000);
