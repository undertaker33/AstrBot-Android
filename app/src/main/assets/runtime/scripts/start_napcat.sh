#!/system/bin/sh

set -eu

APP_HOME="$1"
NATIVE_LIB_DIR="$2"
. "$(dirname "$0")/container_env.sh" "$APP_HOME" "$NATIVE_LIB_DIR"
/system/bin/sh "$(dirname "$0")/bootstrap_container.sh" "$APP_HOME" "$NATIVE_LIB_DIR"

mkdir -p "$LOG_DIR"
: > "$LOG_FILE"
cd / 2>/dev/null || true
echo "start_napcat: PROOT_NO_SECCOMP=${PROOT_NO_SECCOMP:-0}" >> "$LOG_FILE"
echo "start_napcat: host cwd=$(pwd 2>/dev/null || echo unknown)" >> "$LOG_FILE"

echo "5" > "$PROGRESS_FILE"
echo "preparing-start" > "$PROGRESS_LABEL_FILE"
echo "0" > "$PROGRESS_MODE_FILE"
if [ -f "$ROOT_HOME_DIR/launcher.sh" ] || [ -d "$ROOT_HOME_DIR/napcat" ] || [ -f "$ROOT_HOME_DIR/napcat-install.sh" ] || [ -f "$ROOTFS_DIR/root/launcher.sh" ] || [ -d "$ROOTFS_DIR/root/napcat" ] || [ -f "$ROOTFS_DIR/root/napcat-install.sh" ]; then
  echo "1" > "$INSTALLER_CACHE_FILE"
else
  echo "0" > "$INSTALLER_CACHE_FILE"
fi

WRITABLE_TMP="$TMP_COMPAT_DIR"
CONTAINER_HOME="${ELYMBOT_CONTAINER_HOME:-/root}"
CONTAINER_WORKDIR="/"
APT_MIRROR="${ELYMBOT_APT_MIRROR:-http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports}"
PROOT_BIND_ARGS=""

add_bind_arg() {
  if [ -n "$PROOT_BIND_ARGS" ]; then
    PROOT_BIND_ARGS="$PROOT_BIND_ARGS -b $1:$2"
  else
    PROOT_BIND_ARGS="-b $1:$2"
  fi
}

write_fake_proc_file() {
  target_file="$1"
  if [ -f "$target_file" ]; then
    return 0
  fi

  target_dir="$(dirname "$target_file")"
  mkdir -p "$target_dir"
  cat > "$target_file"
}

setup_fake_sysdata() {
  mkdir -p "$ROOTFS_DIR/proc" "$ROOTFS_DIR/sys" "$ROOTFS_DIR/sys/.empty" "$ROOTFS_DIR/storage/emulated"
  chmod 700 "$ROOTFS_DIR/proc" "$ROOTFS_DIR/sys" "$ROOTFS_DIR/sys/.empty" 2>/dev/null || true

  write_fake_proc_file "$ROOTFS_DIR/proc/.loadavg" <<'EOF'
0.12 0.07 0.02 2/165 765
EOF

  write_fake_proc_file "$ROOTFS_DIR/proc/.stat" <<'EOF'
cpu  1957 0 2877 93280 262 342 254 87 0 0
cpu0 31 0 226 12027 82 10 4 9 0 0
cpu1 45 0 664 11144 21 263 233 12 0 0
cpu2 494 0 537 11283 27 10 3 8 0 0
cpu3 359 0 234 11723 24 26 5 7 0 0
cpu4 295 0 268 11772 10 12 2 12 0 0
cpu5 270 0 251 11833 15 3 1 10 0 0
cpu6 430 0 520 11386 30 8 1 12 0 0
cpu7 30 0 172 12108 50 8 1 13 0 0
intr 127541 38 290 0 0 0 0 4 0 1 0 0 25329 258 0 5777 277 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
ctxt 140223
btime 1680020856
processes 772
procs_running 2
procs_blocked 0
softirq 75663 0 5903 6 25375 10774 0 243 11685 0 21677
EOF

  write_fake_proc_file "$ROOTFS_DIR/proc/.uptime" <<'EOF'
124.08 932.80
EOF

  write_fake_proc_file "$ROOTFS_DIR/proc/.version" <<'EOF'
Linux version 6.2.1-proot-distro (proot@termux) (gcc (GCC) 13.3.0, GNU ld (GNU Binutils) 2.42) #1 SMP PREEMPT_DYNAMIC Wed Mar 29 00:00:00 UTC 2023
EOF

  write_fake_proc_file "$ROOTFS_DIR/proc/.vmstat" <<'EOF'
nr_free_pages 1743136
nr_zone_inactive_anon 179281
nr_zone_active_anon 7183
nr_zone_inactive_file 22858
nr_zone_active_file 51328
nr_zone_unevictable 642
nr_zone_write_pending 0
nr_mlock 0
nr_bounce 0
nr_zspages 0
nr_free_cma 0
numa_hit 1259626
numa_miss 0
numa_foreign 0
numa_interleave 720
numa_local 1259626
numa_other 0
nr_inactive_anon 179281
nr_active_anon 7183
nr_inactive_file 22858
nr_active_file 51328
nr_unevictable 642
nr_slab_reclaimable 8091
nr_slab_unreclaimable 7804
nr_isolated_anon 0
nr_isolated_file 0
workingset_nodes 0
workingset_refault_anon 0
workingset_refault_file 0
workingset_activate_anon 0
workingset_activate_file 0
workingset_restore_anon 0
workingset_restore_file 0
workingset_nodereclaim 0
nr_anon_pages 7723
nr_mapped 8905
nr_file_pages 253569
nr_dirty 0
nr_writeback 0
nr_writeback_temp 0
nr_shmem 178741
nr_shmem_hugepages 0
nr_shmem_pmdmapped 0
nr_file_hugepages 0
nr_file_pmdmapped 0
nr_anon_transparent_hugepages 1
nr_vmscan_write 0
nr_vmscan_immediate_reclaim 0
nr_dirtied 0
nr_written 0
nr_throttled_written 0
nr_kernel_misc_reclaimable 0
nr_foll_pin_acquired 0
nr_foll_pin_released 0
nr_kernel_stack 2780
nr_page_table_pages 344
nr_sec_page_table_pages 0
nr_swapcached 0
pgpromote_success 0
pgpromote_candidate 0
nr_dirty_threshold 356564
nr_dirty_background_threshold 178064
pgpgin 890508
pgpgout 0
pswpin 0
pswpout 0
pgalloc_dma 272
pgalloc_dma32 261
pgalloc_normal 1328079
pgalloc_movable 0
pgalloc_device 0
allocstall_dma 0
allocstall_dma32 0
allocstall_normal 0
allocstall_movable 0
allocstall_device 0
pgskip_dma 0
pgskip_dma32 0
pgskip_normal 0
pgskip_movable 0
pgskip_device 0
pgfree 3077011
pgactivate 0
pgdeactivate 0
pglazyfree 0
pgfault 176973
pgmajfault 488
pglazyfreed 0
pgrefill 0
pgreuse 19230
pgsteal_kswapd 0
pgsteal_direct 0
pgsteal_khugepaged 0
pgdemote_kswapd 0
pgdemote_direct 0
pgdemote_khugepaged 0
pgscan_kswapd 0
pgscan_direct 0
pgscan_khugepaged 0
pgscan_direct_throttle 0
pgscan_anon 0
pgscan_file 0
pgsteal_anon 0
pgsteal_file 0
zone_reclaim_failed 0
pginodesteal 0
slabs_scanned 0
kswapd_inodesteal 0
kswapd_low_wmark_hit_quickly 0
kswapd_high_wmark_hit_quickly 0
pageoutrun 0
pgrotated 0
drop_pagecache 0
drop_slab 0
oom_kill 0
numa_pte_updates 0
numa_huge_pte_updates 0
numa_hint_faults 0
numa_hint_faults_local 0
numa_pages_migrated 0
pgmigrate_success 0
pgmigrate_fail 0
thp_migration_success 0
thp_migration_fail 0
thp_migration_split 0
compact_migrate_scanned 0
compact_free_scanned 0
compact_isolated 0
compact_stall 0
compact_fail 0
compact_success 0
compact_daemon_wake 0
compact_daemon_migrate_scanned 0
compact_daemon_free_scanned 0
htlb_buddy_alloc_success 0
htlb_buddy_alloc_fail 0
cma_alloc_success 0
cma_alloc_fail 0
unevictable_pgs_culled 27002
unevictable_pgs_scanned 0
unevictable_pgs_rescued 744
unevictable_pgs_mlocked 744
unevictable_pgs_munlocked 744
unevictable_pgs_cleared 0
unevictable_pgs_stranded 0
thp_fault_alloc 13
thp_fault_fallback 0
thp_fault_fallback_charge 0
thp_collapse_alloc 4
thp_collapse_alloc_failed 0
thp_file_alloc 0
thp_file_fallback 0
thp_file_fallback_charge 0
thp_file_mapped 0
thp_split_page 0
thp_split_page_failed 0
thp_deferred_split_page 1
thp_split_pmd 1
thp_scan_exceed_none_pte 0
thp_scan_exceed_swap_pte 0
thp_scan_exceed_share_pte 0
thp_split_pud 0
thp_zero_page_alloc 0
thp_zero_page_alloc_failed 0
thp_swpout 0
thp_swpout_fallback 0
balloon_inflate 0
balloon_deflate 0
balloon_migrate 0
swap_ra 0
swap_ra_hit 0
ksm_swpin_copy 0
cow_ksm 0
zswpin 0
zswpout 0
direct_map_level2_splits 29
direct_map_level3_splits 0
nr_unstable 0
EOF

  write_fake_proc_file "$ROOTFS_DIR/proc/.sysctl_entry_cap_last_cap" <<'EOF'
40
EOF

  write_fake_proc_file "$ROOTFS_DIR/proc/.sysctl_inotify_max_user_watches" <<'EOF'
4096
EOF

  write_fake_proc_file "$ROOTFS_DIR/proc/.sysctl_crypto_fips_enabled" <<'EOF'
0
EOF
}

prepare_proc_bind_args() {
  setup_fake_sysdata

  if [ ! -r /proc/loadavg ] || [ ! -s /proc/loadavg ]; then
    add_bind_arg "$ROOTFS_DIR/proc/.loadavg" /proc/loadavg
  fi
  if [ ! -r /proc/stat ] || [ ! -s /proc/stat ]; then
    add_bind_arg "$ROOTFS_DIR/proc/.stat" /proc/stat
  fi
  if [ ! -r /proc/uptime ] || [ ! -s /proc/uptime ]; then
    add_bind_arg "$ROOTFS_DIR/proc/.uptime" /proc/uptime
  fi
  if [ ! -r /proc/version ] || [ ! -s /proc/version ]; then
    add_bind_arg "$ROOTFS_DIR/proc/.version" /proc/version
  fi
  if [ ! -r /proc/vmstat ] || [ ! -s /proc/vmstat ]; then
    add_bind_arg "$ROOTFS_DIR/proc/.vmstat" /proc/vmstat
  fi
  if [ ! -r /proc/sys/kernel/cap_last_cap ] || [ ! -s /proc/sys/kernel/cap_last_cap ]; then
    add_bind_arg "$ROOTFS_DIR/proc/.sysctl_entry_cap_last_cap" /proc/sys/kernel/cap_last_cap
  fi
  if [ ! -r /proc/sys/fs/inotify/max_user_watches ] || [ ! -s /proc/sys/fs/inotify/max_user_watches ]; then
    add_bind_arg "$ROOTFS_DIR/proc/.sysctl_inotify_max_user_watches" /proc/sys/fs/inotify/max_user_watches
  fi
  if [ ! -r /proc/sys/crypto/fips_enabled ] || [ ! -s /proc/sys/crypto/fips_enabled ]; then
    add_bind_arg "$ROOTFS_DIR/proc/.sysctl_crypto_fips_enabled" /proc/sys/crypto/fips_enabled
  fi
}

prepare_rootfs_network_config() {
  mkdir -p "$ROOTFS_DIR/etc/apt/apt.conf.d"
  cat > "$ROOTFS_DIR/etc/resolv.conf" <<'EOF'
nameserver 223.5.5.5
nameserver 119.29.29.29
nameserver 8.8.8.8
EOF

  cat > "$ROOTFS_DIR/etc/apt/sources.list" <<EOF
deb [trusted=yes] ${APT_MIRROR} noble main restricted universe multiverse
deb [trusted=yes] ${APT_MIRROR} noble-updates main restricted universe multiverse
deb [trusted=yes] ${APT_MIRROR} noble-security main restricted universe multiverse
deb [trusted=yes] ${APT_MIRROR} noble-backports main restricted universe multiverse
EOF

  cat > "$ROOTFS_DIR/etc/apt/apt.conf.d/99elymbot" <<'EOF'
Acquire::Retries "2";
Acquire::http::Timeout "20";
Acquire::https::Timeout "20";
Acquire::Languages "none";
APT::Sandbox::User "root";
Acquire::AllowInsecureRepositories "true";
Acquire::AllowDowngradeToInsecureRepositories "true";
APT::Get::AllowUnauthenticated "true";
EOF
}

mkdir -p "$WRITABLE_TMP"
mkdir -p "$ROOT_HOME_DIR"
/system/bin/chmod 1777 "$WRITABLE_TMP" 2>/dev/null || true
/system/bin/chmod 700 "$ROOT_HOME_DIR" 2>/dev/null || true

ANDROID_TZ="$(getprop persist.sys.timezone 2>/dev/null || true)"
if [ -z "$ANDROID_TZ" ]; then
  ANDROID_TZ="$(date +%Z 2>/dev/null || true)"
fi
if [ -z "$ANDROID_TZ" ]; then
  ANDROID_TZ="UTC"
fi

if [ -f "$PID_FILE" ]; then
  PID="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    echo "NapCat already running with pid $PID"
    exit 0
  fi
fi

if [ ! -f "$ROOT_HOME_DIR/elymbot_napcat_entry.sh" ] && [ ! -f "$ROOTFS_DIR/root/elymbot_napcat_entry.sh" ]; then
  echo "Missing elymbot_napcat_entry.sh in writable root home" >&2
  exit 4
fi

prepare_rootfs_network_config
prepare_proc_bind_args
echo "start_napcat: prepared rootfs apt mirror: ${APT_MIRROR}" >> "$LOG_FILE"
echo "start_napcat: prepared fake proc binds: ${PROOT_BIND_ARGS:-(none)}" >> "$LOG_FILE"
echo "start_napcat: container home prepared in rootfs: ${ROOT_HOME_DIR} -> ${CONTAINER_HOME}" >> "$LOG_FILE"
echo "start_napcat: container workdir: ${CONTAINER_WORKDIR}" >> "$LOG_FILE"
echo "start_napcat: proot tmp dir: ${WRITABLE_TMP}" >> "$LOG_FILE"

export PROOT_TMP_DIR="$WRITABLE_TMP"

echo "start_napcat: running proot smoke test" >> "$LOG_FILE"
if ! "$PROOT_BIN" \
  -0 \
  -r "$ROOTFS_DIR" \
  --link2symlink \
  -b /dev \
  -b /proc \
  -b /sys \
  -b /system \
  -b /apex \
  -b "$WRITABLE_TMP":"$WRITABLE_TMP" \
  -b "$WRITABLE_TMP":/dev/shm \
  $PROOT_BIND_ARGS \
  -w "$CONTAINER_WORKDIR" \
  /usr/bin/env -i \
    HOME="$CONTAINER_HOME" \
    TMPDIR="$WRITABLE_TMP" \
    TERM=xterm-256color \
    LANG=en_US.UTF-8 \
    TZ="$ANDROID_TZ" \
    PATH=/system/bin:/system/xbin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    /bin/sh -c 'cd / >/dev/null 2>&1 || true; [ -d /root ] || mkdir -p /root; mkdir -p /root/.elymbot-probe && cd /root && echo root-probe-ok' \
  >> "$LOG_FILE" 2>&1; then
  echo "proot smoke test failed" >&2
  exit 5
fi
echo "start_napcat: proot smoke test passed" >> "$LOG_FILE"

nohup "$PROOT_BIN" \
  -0 \
  -r "$ROOTFS_DIR" \
  --link2symlink \
  -b /dev \
  -b /proc \
  -b /sys \
  -b /system \
  -b /apex \
  -b /dev/pts \
  -b /proc/self/fd:/dev/fd \
  -b /proc/self/fd/0:/dev/stdin \
  -b /proc/self/fd/1:/dev/stdout \
  -b /proc/self/fd/2:/dev/stderr \
  -b /sdcard \
  -b /storage/emulated/0:/sdcard \
  -b /storage/emulated/0:/storage/emulated/0 \
  -b "$APP_HOME":"$APP_HOME" \
  -b "$WRITABLE_TMP":"$WRITABLE_TMP" \
  -b "$WRITABLE_TMP":/dev/shm \
  $PROOT_BIND_ARGS \
  -w "$CONTAINER_WORKDIR" \
  /usr/bin/env -i \
    HOME="$CONTAINER_HOME" \
    TMPDIR="$WRITABLE_TMP" \
    TERM=xterm-256color \
    LANG=en_US.UTF-8 \
    TZ="$ANDROID_TZ" \
    PATH=/system/bin:/system/xbin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    ELYMBOT_APP_HOME="$APP_HOME" \
    PROGRESS_FILE="$PROGRESS_FILE" \
    PROGRESS_LABEL_FILE="$PROGRESS_LABEL_FILE" \
    PROGRESS_MODE_FILE="$PROGRESS_MODE_FILE" \
    INSTALLER_CACHE_FILE="$INSTALLER_CACHE_FILE" \
    ELYMBOT_APT_MIRROR="$APT_MIRROR" \
    /bin/sh -c 'cd /root 2>/dev/null || cd / 2>/dev/null || true; chmod +x /root/elymbot_napcat_entry.sh && exec /bin/bash /root/elymbot_napcat_entry.sh' \
  >> "$LOG_FILE" 2>&1 &

echo $! > "$PID_FILE"
echo "NapCat started with pid $(cat "$PID_FILE")"
