#!/system/bin/sh
# DRKMUQEET — advanced network (TCP/IP tuning, IPv6, DNS, VPN killswitch, per-app firewall)

net_usage() {
  cat <<'EOF'
NETWORK:
  drk net tcp <bbr|cubic|reno>      congestion control (verified)
  drk net fastopen <0-3>            TCP Fast Open
  drk net sack <0|1>                selective ACK
  drk net ecn <0|1>                 explicit congestion notification
  drk net timestamps <0|1>          TCP timestamps
  drk net window <0|1>              TCP window scaling
  drk net mtuprobe <0|1>            MTU probing (RFC 4821)
  drk net keepalive <time> <intvl> <probes>   TCP keepalive
  drk net syn <retries> [backlog]   SYN retries + backlog
  drk net fin <sec>                 FIN timeout
  drk net tw <0|1>                  TIME_WAIT reuse
  drk net orphan <n>                max orphans
  drk net mem <min> <press> <max>   TCP memory (pages)
  drk net buf <rmem> <wmem>         TCP r/w buffers (bytes)
  drk net udp <min> <press> <max>   UDP memory (pages)
  drk net forward <0|1>             IP forwarding (routing/NAT)
  drk net redirect <0|1>            accept/send ICMP redirects
  drk net srcroute <0|1>            accept source route
  drk net rpfilter <0|1|2>          reverse path filter
  drk net ipv6 <0|1>                enable/disable IPv6 (all interfaces)
  drk net v6priv <0|1|2>            IPv6 privacy extensions (use_tempaddr)
  drk net v6ra <0|1>                IPv6 router advertisements
  drk net v6dad <n>                 IPv6 duplicate address detection
  drk net v6hop <n>                 IPv6 hop limit
  drk net v6mtu <n>                 IPv6 MTU
  drk net dns dot <host>|off        Private DNS (DNS over TLS)
  drk net dns set <ip> [ip2]        legacy DNS server props
  drk net vpnks <0|1>               VPN killswitch (iptables, blocks traffic when VPN is down)
  drk net fw <block|allow> <pkg|uid>  per-app network firewall (iptables owner match)
  drk net info                      current network state
EOF
}

net_main() {
  local a="$1"; [ -n "$a" ] || { net_usage; return 1; }
  shift
  case "$a" in
    tcp) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net tcp <algo>"; return 1; }
      net_tcp_algo "$1" ;;
    fastopen) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net fastopen <0-3>"; return 1; }
      sc net.ipv4.tcp_fastopen "$1" "tcp_fastopen" ;;
    sack) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net sack <0|1>"; return 1; }
      sc net.ipv4.tcp_sack "$1" "tcp_sack"
      [ -e /proc/sys/net/ipv4/tcp_fack ] && sc net.ipv4.tcp_fack "$1" "tcp_fack"
      [ -e /proc/sys/net/ipv4/tcp_dsack ] && sc net.ipv4.tcp_dsack "$1" "tcp_dsack" ;;
    ecn) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net ecn <0|1>"; return 1; }
      sc net.ipv4.tcp_ecn "$1" "tcp_ecn"
      [ -e /proc/sys/net/ipv4/tcp_ecn_fallback ] && sc net.ipv4.tcp_ecn_fallback "$1" "tcp_ecn_fallback" ;;
    timestamps) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net timestamps <0|1>"; return 1; }
      sc net.ipv4.tcp_timestamps "$1" "tcp_timestamps" ;;
    window) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net window <0|1>"; return 1; }
      sc net.ipv4.tcp_window_scaling "$1" "tcp_window_scaling" ;;
    mtuprobe) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net mtuprobe <0|1>"; return 1; }
      sc net.ipv4.tcp_mtu_probing "$1" "tcp_mtu_probing" ;;
    keepalive) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] && [ -n "$3" ] || { bad "usage: drk net keepalive <time> <intvl> <probes>"; return 1; }
      sc net.ipv4.tcp_keepalive_time "$1" "tcp_keepalive_time"
      sc net.ipv4.tcp_keepalive_intvl "$2" "tcp_keepalive_intvl"
      sc net.ipv4.tcp_keepalive_probes "$3" "tcp_keepalive_probes" ;;
    syn) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net syn <retries> [backlog]"; return 1; }
      sc net.ipv4.tcp_syn_retries "$1" "tcp_syn_retries"
      [ -e /proc/sys/net/ipv4/tcp_synack_retries ] && sc net.ipv4.tcp_synack_retries "$1" "tcp_synack_retries"
      [ -n "$2" ] && sc net.ipv4.tcp_max_syn_backlog "$2" "tcp_max_syn_backlog" ;;
    fin) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net fin <sec>"; return 1; }
      sc net.ipv4.tcp_fin_timeout "$1" "tcp_fin_timeout" ;;
    tw) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net tw <0|1>"; return 1; }
      sc net.ipv4.tcp_tw_reuse "$1" "tcp_tw_reuse" ;;
    orphan) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net orphan <n>"; return 1; }
      sc net.ipv4.tcp_max_orphans "$1" "tcp_max_orphans" ;;
    mem) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] && [ -n "$3" ] || { bad "usage: drk net mem <min> <press> <max> (pages)"; return 1; }
      sc net.ipv4.tcp_mem "$1 $2 $3" "tcp_mem" ;;
    buf) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk net buf <rmem> <wmem> (bytes)"; return 1; }
      sc net.ipv4.tcp_rmem "4096 $1 $1" "tcp_rmem"
      sc net.ipv4.tcp_wmem "4096 $2 $2" "tcp_wmem" ;;
    udp) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] && [ -n "$3" ] || { bad "usage: drk net udp <min> <press> <max> (pages)"; return 1; }
      sc net.ipv4.udp_mem "$1 $2 $3" "udp_mem" ;;
    forward) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net forward <0|1>"; return 1; }
      sc net.ipv4.ip_forward "$1" "ip_forward" ;;
    redirect) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net redirect <0|1>"; return 1; }
      net_conf_all accept_redirects "$1"
      net_conf_all send_redirects "$1" ;;
    srcroute) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net srcroute <0|1>"; return 1; }
      net_conf_all accept_source_route "$1" ;;
    rpfilter) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net rpfilter <0|1|2>"; return 1; }
      net_conf_all rp_filter "$1" ;;
    ipv6) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net ipv6 <0|1>"; return 1; }
      net_conf6_all disable_ipv6 "$1" ;;
    v6priv) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net v6priv <0|1|2>"; return 1; }
      net_conf6_all use_tempaddr "$1" ;;
    v6ra) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net v6ra <0|1>"; return 1; }
      net_conf6_all accept_ra "$1" ;;
    v6dad) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net v6dad <n>"; return 1; }
      net_conf6_all dad_transmits "$1" ;;
    v6hop) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net v6hop <n>"; return 1; }
      sc net.ipv6.conf.all.hop_limit "$1" "ipv6 hop_limit" ;;
    v6mtu) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net v6mtu <n>"; return 1; }
      net_conf6_all mtu "$1" ;;
    dns) need_root || return 1
      case "$1" in
        dot) [ -n "$2" ] || { bad "usage: drk net dns dot <host>|off"; return 1; }
          if [ "$2" = "off" ]; then
            ss global private_dns_mode off "private DNS"
          else
            ss global private_dns_mode hostname "private DNS mode"
            ss global private_dns_specifier "$2" "private DNS server"
          fi ;;
        set) [ -n "$2" ] || { bad "usage: drk net dns set <ip> [ip2]"; return 1; }
          pp net.dns1 "$2" "net.dns1"
          [ -n "$3" ] && pp net.dns2 "$3" "net.dns2"
          warn "legacy DNS props; modern Android uses private_dns (drk net dns dot)" ;;
        *) bad "usage: drk net dns <dot|set> ..."; return 1 ;;
      esac ;;
    vpnks) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk net vpnks <0|1>"; return 1; }
      case "$1" in 1) net_vpnks_on ;; 0) net_vpnks_off ;; *) bad "usage: drk net vpnks <0|1>"; return 1 ;; esac ;;
    fw) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk net fw <block|allow> <pkg|uid>"; return 1; }
      net_firewall "$1" "$2" ;;
    info) net_info ;;
    *) net_usage; return 1 ;;
  esac
}

net_tcp_algo() {
  local algo="$1" avail
  avail=$(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null)
  if [ -n "$avail" ]; then
    case " $avail " in
      *" $algo "*) sc net.ipv4.tcp_congestion_control "$algo" "tcp_congestion_control" ;;
      *) bad "congestion control '$algo' not in kernel (available: $avail)"; return 1 ;;
    esac
  else
    # availability list unreadable — try directly and let verification judge
    sc net.ipv4.tcp_congestion_control "$algo" "tcp_congestion_control"
  fi
}

# net_conf_all <key> <value> — apply to all/ + every interface in ipv4 conf
net_conf_all() {
  local key="$1" v="$2" f n=0
  for f in /proc/sys/net/ipv4/conf/*/$key; do
    [ -e "$f" ] || continue
    sv "$f" "$v" "ipv4 $key ($(basename "$(dirname "$f")"))" && n=$((n+1))
  done
  [ "$n" -gt 0 ] || { bad "no ipv4 conf interfaces for $key"; return 1; }
}

net_conf6_all() {
  local key="$1" v="$2" f n=0
  for f in /proc/sys/net/ipv6/conf/*/$key; do
    [ -e "$f" ] || continue
    sv "$f" "$v" "ipv6 $key ($(basename "$(dirname "$f")"))" && n=$((n+1))
  done
  [ "$n" -gt 0 ] || { bad "no ipv6 conf interfaces for $key"; return 1; }
}

net_ipt() { # net_ipt — resolve iptables binary
  has iptables && { echo iptables; return 0; }
  has iptables-nft && { echo iptables-nft; return 0; }
  return 1
}

net_vpnks_on() {
  local ip=$(net_ipt) || { bad "no iptables binary"; return 1; }
  "$ip" -N DRKKS 2>/dev/null
  "$ip" -F DRKKS 2>/dev/null
  "$ip" -A DRKKS -o lo -j ACCEPT 2>/dev/null
  "$ip" -A DRKKS -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT 2>/dev/null \
    || "$ip" -A DRKKS -m state --state ESTABLISHED,RELATED -j ACCEPT 2>/dev/null
  "$ip" -A DRKKS -o tun+ -j ACCEPT 2>/dev/null
  "$ip" -A DRKKS -j REJECT 2>/dev/null
  "$ip" -C OUTPUT -j DRKKS 2>/dev/null || "$ip" -I OUTPUT -j DRKKS 2>/dev/null
  if "$ip" -L DRKKS -n >/dev/null 2>&1; then
    ok "VPN killswitch ACTIVE (only local + established + tun+ traffic)"
    warn "while active, all non-VPN internet traffic is blocked — turn off with: drk net vpnks 0"
  else
    bad "killswitch chain not created"; return 1
  fi
}

net_vpnks_off() {
  local ip=$(net_ipt) || { bad "no iptables binary"; return 1; }
  "$ip" -D OUTPUT -j DRKKS 2>/dev/null
  "$ip" -F DRKKS 2>/dev/null
  "$ip" -X DRKKS 2>/dev/null
  ok "VPN killswitch removed"
}

net_firewall() {
  local action="$1" target="$2" ip uid rule
  ip=$(net_ipt) || { bad "no iptables binary"; return 1; }
  case "$target" in
    *[!0-9]*) uid=$(uid_of "$target")
      [ -n "$uid" ] || { bad "cannot resolve uid for $target"; return 1; }
      say "  $target → uid $uid" ;;
    *) uid=$target ;;
  esac
  rule="-m owner --uid-owner $uid -j REJECT"
  case "$action" in
    block)
      "$ip" -C OUTPUT $rule 2>/dev/null || "$ip" -I OUTPUT $rule 2>/dev/null
      if "$ip" -C OUTPUT $rule 2>/dev/null; then ok "network BLOCKED for $target (uid $uid)"; else bad "iptables failed"; return 1; fi ;;
    allow)
      "$ip" -D OUTPUT $rule 2>/dev/null
      if ! "$ip" -C OUTPUT $rule 2>/dev/null; then ok "network ALLOWED for $target (uid $uid)"; else bad "iptables failed"; return 1; fi ;;
    *) bad "usage: drk net fw <block|allow> <pkg|uid>"; return 1 ;;
  esac
}

net_info() {
  hdr "Network"
  for k in tcp_congestion_control tcp_available_congestion_control tcp_fastopen tcp_sack \
           tcp_ecn tcp_timestamps tcp_window_scaling tcp_mtu_probing tcp_keepalive_time \
           tcp_fin_timeout tcp_tw_reuse ip_forward tcp_max_orphans; do
    [ -r "/proc/sys/net/ipv4/$k" ] && say "  $k = $(cat "/proc/sys/net/ipv4/$k" 2>/dev/null)"
  done
  say "  private_dns_mode = $(settings get global private_dns_mode 2>/dev/null)"
  say "  vpn killswitch = $([ "$(net_ipt 2>/dev/null)" ] && { $(net_ipt) -L DRKKS -n >/dev/null 2>&1 && echo ACTIVE || echo off; } || echo n/a)"
}

net_probe() {
  [ -r /proc/sys/net/ipv4/tcp_congestion_control ] && ok "tcp sysctls" || bad "tcp sysctls"
  [ -w /proc/sys/net/ipv4/ip_forward ] && ok "ip_forward writable" || warn "ip_forward not writable"
  local ip=$(net_ipt); [ -n "$ip" ] && ok "iptables ($ip)" || warn "iptables unavailable"
  return 0
}

net_status() {
  hdr "Network"
  say "  congestion control: $(cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null)"
  say "  fastopen: $(cat /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null) | sack: $(cat /proc/sys/net/ipv4/tcp_sack 2>/dev/null) | ecn: $(cat /proc/sys/net/ipv4/tcp_ecn 2>/dev/null)"
  say "  timestamps: $(cat /proc/sys/net/ipv4/tcp_timestamps 2>/dev/null) | window scaling: $(cat /proc/sys/net/ipv4/tcp_window_scaling 2>/dev/null)"
  say "  ipv4 forward: $(cat /proc/sys/net/ipv4/ip_forward 2>/dev/null)"
  say "  ipv6: $(cat /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null) (1=disabled)"
}
