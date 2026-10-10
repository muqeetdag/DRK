const $=s=>document.querySelector(s);const endpoint=a=>`/cgi-bin/control?action=${a}`;
async function stats(){try{const s=await fetch(endpoint('stats'),{cache:'no-store'}).then(r=>r.json());$('#profile').textContent=s.profile||'balanced';$('#battery').textContent=s.battery??'—';$('#thermal').textContent=s.temperature??'—';$('#uptime').textContent=s.uptime??'—'}catch(e){$('#profile').textContent='Dashboard unavailable'}}
const pad=(v,n)=>String(v??'—').padEnd(n);
async function audit(){const out=$('#audit');out.hidden=false;out.classList.remove('bad');out.textContent='scanning kernel nodes…';
try{const a=await fetch(endpoint('audit'),{cache:'no-store'}).then(r=>r.json()),v=a.verdict||{},rows=[];
(a.cpufreq||[]).forEach(n=>rows.push(`${pad(n.policy,9)} governor   ${pad(n.current,12)} -> ${pad(n.expected,12)} offered:${pad(n.offered,7)} [${n.state}]`));
(a.blockio||[]).forEach(n=>rows.push(`${pad(n.device,9)} read_ahead ${pad(n.current,12)} -> ${pad(n.expected,12)} ${' '.repeat(8)}[${n.state}]`));
(a.thermal||[]).forEach(n=>rows.push(`${pad(n.zone,14)} ${pad(n.type,18)} ${pad(n.temp_c,6)} C`));
const an=a.animations||{};rows.push(`${pad('settings',9)} animations ${pad(an.window,6)}/${pad(an.transition,6)}/${pad(an.animator,6)} -> ${pad(an.expected,6)}`);
rows.push('','— verdict —',
`cpufreq nodes : ${v.cpufreq_nodes??0}`,`block nodes   : ${v.blockio_nodes??0}`,
`write blocked : ${v.write_blocked??0}`,`not applied   : ${v.not_applied??0}`,
`max temp      : ${v.max_temp_c??'?'} C`,`status        : ${v.status??'unknown'}`);
out.textContent=`profile: ${a.profile??'?'}   device: ${a.device||'?'}\n\n${rows.join('\n')}`;
if(v.status==='degraded'||v.status==='blocked')out.classList.add('bad');
}catch(e){out.textContent='audit unavailable: '+e;out.classList.add('bad')}}
document.querySelectorAll('button').forEach(b=>b.onclick=async()=>{b.disabled=true;try{if(b.dataset.action==='audit'){await audit()}else{await fetch(endpoint(b.dataset.action));await stats()}}finally{b.disabled=false}});stats();setInterval(stats,5000);
