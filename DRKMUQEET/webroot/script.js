const $=s=>document.querySelector(s);const endpoint=a=>`/cgi-bin/control?action=${a}`;
const get=async a=>fetch(endpoint(a),{cache:'no-store'}).then(r=>r.json());
const pad=(v,n)=>String(v??'—').padEnd(n);

async function stats(){
  try{
    const s=await get('stats');
    $('#profile').textContent=s.profile||'default';
    $('#battery').textContent=s.battery??'—';
    $('#thermal').textContent=(s.max_temp_c!=null&&s.max_temp_c!=='?')?s.max_temp_c:(s.temperature??'—');
    $('#uptime').textContent=s.uptime??'—';
    const g=$('#guard'),sub=$('#guard-sub'),lvl=s.guard||'none';
    g.textContent=lvl;
    g.classList.toggle('active',lvl!=='none');
    sub.textContent=lvl==='none'?'no override':'holding the clocks back';
  }catch(e){$('#profile').textContent='Dashboard unavailable'}
}

async function guardLog(){
  const out=$('#guardlog');
  try{
    const g=await get('guard-log');
    const lines=g.lines||[];
    out.hidden=false;
    out.textContent=lines.length?lines.join('\n'):'guard journal is empty — it has not had to intervene yet.';
  }catch(e){out.hidden=false;out.textContent='guard journal unavailable: '+e}
}

async function audit(){
  const out=$('#audit');
  out.hidden=false;out.classList.remove('bad');out.textContent='scanning kernel nodes…';
  try{
    const a=await get('audit'),v=a.verdict||{},rows=[];
    (a.cpufreq||[]).forEach(n=>{
      rows.push(`${pad(n.policy,9)} governor   ${pad(n.current,12)} -> ${pad(n.expected,12)} offered:${pad(n.offered,7)} [${n.state}]`);
      rows.push(`${pad('',9)} freq       ${pad(n.scaling_min_freq,8)}..${pad(n.scaling_max_freq,9)} hw ${pad(n.cpuinfo_min_freq,8)}..${pad(n.cpuinfo_max_freq,9)} [${n.freq_limits}]`);
      rows.push(`${pad('',9)} cpus       ${pad(n.affected_cpus,12)} energy_aware:${n.energy_aware}`);
    });
    (a.blockio||[]).forEach(n=>rows.push(`${pad(n.device,9)} read_ahead ${pad(n.current,12)} -> ${pad(n.expected,12)} ${' '.repeat(8)}[${n.state}]`));
    (a.thermal||[]).forEach(n=>rows.push(`${pad(n.zone,14)} ${pad(n.type,18)} ${pad(n.temp_c,6)} C`));
    const an=a.animations||{};
    rows.push(`${pad('settings',9)} animations ${pad(an.window,6)}/${pad(an.transition,6)}/${pad(an.animator,6)} -> ${pad(an.expected,6)}`);
    const th=v.guard_thresholds||{};
    rows.push('','— verdict —',
      `cpufreq nodes    : ${v.cpufreq_nodes??0}`,
      `block nodes      : ${v.blockio_nodes??0}`,
      `write blocked    : ${v.write_blocked??0}`,
      `not applied      : ${v.not_applied??0}`,
      `freq violations  : ${v.freq_limit_violations??0}`,
      `max temp         : ${v.max_temp_c??'?'} C`,
      `thermal guard    : ${v.guard??'none'}  (soft ${th.soft_c??'?'} / hard ${th.hard_c??'?'} / cool ${th.cool_c??'?'} C)`,
      `status           : ${v.status??'unknown'}`);
    out.textContent=`profile: ${a.profile??'?'}   device: ${a.device||'?'}   guard: ${a.guard||'none'}\n\n${rows.join('\n')}`;
    if(v.status==='degraded'||v.status==='unsafe'||v.status==='blocked')out.classList.add('bad');
  }catch(e){out.textContent='audit unavailable: '+e;out.classList.add('bad')}
}

const afterAction={guard:guardLog,unguard:guardLog};
document.querySelectorAll('button').forEach(b=>b.onclick=async()=>{
  b.disabled=true;
  try{
    const a=b.dataset.action;
    if(a==='audit'){await audit()}
    else{
      await fetch(endpoint(a));
      await stats();
      if(afterAction[a])await afterAction[a]();
    }
  }finally{b.disabled=false}
});

stats();guardLog();setInterval(stats,5000);
