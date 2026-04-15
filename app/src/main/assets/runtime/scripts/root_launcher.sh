#!/bin/bash

set -eu
trap 'code=$?; echo "astrbot_napcat_entry exit code: ${code}"' EXIT

export DEBIAN_FRONTEND=noninteractive
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export HOME=/root
export NAPCAT_DISABLE_PIPE=1
export NAPCAT_DISABLE_MULTI_PROCESS=1
export NAPCAT_WEBUI_PREFERRED_PORT=6099

NAPCAT_HOME="/root/napcat"
NAPCAT_CONFIG_DIR="$NAPCAT_HOME/config"
NAPCAT_ENTRY="/root/launcher.sh"
NAPCAT_INSTALLER_CACHE="/root/napcat-install.sh"
NAPCAT_INSTALLER_URL="https://raw.githubusercontent.com/NapNeko/napcat-linux-installer/refs/heads/main/install.sh"
NAPCAT_CONFIG_BACKUP="/root/napcat_config_backup"
ASTRBOT_DPKG_DIR="/var/lib"
ASTRBOT_APT_STATE_DIR="/var/lib/apt"
ASTRBOT_APT_CACHE_DIR="/var/cache/apt"
DPKG_ADMINDIR="/var/lib/dpkg"
TARGET_PROXY=""
RUNTIME_SECRET_FILE="${ASTRBOT_APP_HOME:-}/runtime/config/runtime-secrets.env"

cd /root 2>/dev/null || cd / 2>/dev/null || true

write_progress() {
  local percent="$1"
  local label="$2"
  local mode="${3:-0}"

  if [ -n "${PROGRESS_FILE:-}" ]; then
    printf "%s\n" "$percent" > "$PROGRESS_FILE" 2>/dev/null || true
  fi
  if [ -n "${PROGRESS_LABEL_FILE:-}" ]; then
    printf "%s\n" "$label" > "$PROGRESS_LABEL_FILE" 2>/dev/null || true
  fi
  if [ -n "${PROGRESS_MODE_FILE:-}" ]; then
    printf "%s\n" "$mode" > "$PROGRESS_MODE_FILE" 2>/dev/null || true
  fi
}

log_config_snapshot() {
  local label="$1"
  local file="$2"

  if [ ! -f "$file" ]; then
    echo "${label}: missing (${file})"
    return 0
  fi

  local snapshot=""
  snapshot="$(tr '\n' ' ' < "$file" | sed 's/[[:space:]]\+/ /g' | cut -c1-600)"
  snapshot="$(printf '%s' "$snapshot" | sed -E 's/("token"[[:space:]]*:[[:space:]]*")[^"]+(")/\1<redacted>\2/g; s/("secret[^"]*"[[:space:]]*:[[:space:]]*")[^"]+(")/\1<redacted>\2/g; s/("jwt[^"]*"[[:space:]]*:[[:space:]]*")[^"]+(")/\1<redacted>\2/g')"
  echo "${label}: ${snapshot}"
}

load_runtime_secrets() {
  if [ -z "${ASTRBOT_APP_HOME:-}" ]; then
    echo "runtime secret source missing: ASTRBOT_APP_HOME is empty" >&2
    exit 6
  fi
  if [ ! -f "$RUNTIME_SECRET_FILE" ]; then
    echo "runtime secret file missing: $RUNTIME_SECRET_FILE" >&2
    exit 6
  fi

  # shellcheck disable=SC1090
  . "$RUNTIME_SECRET_FILE"

  if [ -z "${NAPCAT_WEBUI_SECRET_KEY:-}" ] || [ -z "${NAPCAT_WEBUI_JWT_SECRET_KEY:-}" ]; then
    echo "runtime secret file is incomplete: $RUNTIME_SECRET_FILE" >&2
    exit 6
  fi

  export NAPCAT_WEBUI_SECRET_KEY
  export NAPCAT_WEBUI_JWT_SECRET_KEY
  echo "runtime secrets loaded: source=$RUNTIME_SECRET_FILE webui_len=${#NAPCAT_WEBUI_SECRET_KEY} jwt_len=${#NAPCAT_WEBUI_JWT_SECRET_KEY}"
}

mark_installer_cached() {
  local cached="$1"

  if [ -n "${INSTALLER_CACHE_FILE:-}" ]; then
    printf "%s\n" "$cached" > "$INSTALLER_CACHE_FILE" 2>/dev/null || true
  fi
}

network_test() {
  local timeout=12
  local proxy
  local status
  local check_url="https://raw.githubusercontent.com/NapNeko/NapCatQQ/main/package.json"

  TARGET_PROXY=""
  echo "testing github proxy connectivity"
  for proxy in "https://ghfast.top" "https://gh.wuliya.xin" "https://gh-proxy.com" "https://github.moeyy.xyz"; do
    status="$(curl -L --connect-timeout "$timeout" --max-time $((timeout * 2)) -o /dev/null -s -w "%{http_code}" "${proxy}/${check_url}" || true)"
    if [ "$status" = "200" ]; then
      TARGET_PROXY="$proxy"
      echo "selected github proxy: $proxy"
      return
    fi
  done

  status="$(curl --connect-timeout "$timeout" --max-time $((timeout * 2)) -o /dev/null -s -w "%{http_code}" "$check_url" || true)"
  if [ "$status" = "200" ]; then
    echo "using direct github connectivity"
  else
    echo "github connectivity unavailable, trying direct download anyway"
  fi
}

download_napcat_installer() {
  local target_file="$1"
  local download_url="$NAPCAT_INSTALLER_URL"

  if [ -s "$target_file" ]; then
    echo "using cached napcat installer script"
    mark_installer_cached 1
    write_progress 55 "installer-cached" 0
    return 0
  fi

  write_progress 45 "download-installer" 1
  network_test
  if [ -n "${TARGET_PROXY:-}" ]; then
    download_url="${TARGET_PROXY}/${NAPCAT_INSTALLER_URL}"
  fi

  echo "downloading napcat installer from ${download_url}"
  curl -fsSL --connect-timeout 20 --max-time 240 --retry 2 --retry-delay 2 "$download_url" -o "$target_file"
  chmod +x "$target_file"
  mark_installer_cached 1
  write_progress 55 "installer-downloaded" 0
}

dpkg_cmd() {
  if [ "$DPKG_ADMINDIR" = "/var/lib/dpkg" ]; then
    dpkg "$@"
  else
    dpkg --admindir="$DPKG_ADMINDIR" "$@"
  fi
}

apt_cmd() {
  if [ "$DPKG_ADMINDIR" = "/var/lib/dpkg" ] \
    && [ "$ASTRBOT_APT_STATE_DIR" = "/var/lib/apt" ] \
    && [ "$ASTRBOT_APT_CACHE_DIR" = "/var/cache/apt" ]; then
    apt-get -o APT::Sandbox::User=root "$@"
  else
    apt-get \
      -o APT::Sandbox::User=root \
      -o "Dir::State=$ASTRBOT_APT_STATE_DIR" \
      -o "Dir::State::status=$DPKG_ADMINDIR/status" \
      -o "Dir::Cache=$ASTRBOT_APT_CACHE_DIR" \
      -o "DPkg::Options::=--admindir=$DPKG_ADMINDIR" \
      "$@"
  fi
}

prepare_writable_paths() {
  chmod 1777 /tmp || true
  [ -f "$DPKG_ADMINDIR/status" ] || cp /var/lib/dpkg/status "$DPKG_ADMINDIR/status" || true
  [ -f "$DPKG_ADMINDIR/available" ] || touch "$DPKG_ADMINDIR/available" || true
  [ -f "$DPKG_ADMINDIR/status-old" ] || cp "$DPKG_ADMINDIR/status" "$DPKG_ADMINDIR/status-old" || true
  touch "$DPKG_ADMINDIR/lock" "$DPKG_ADMINDIR/lock-frontend" || true
  chmod 666 "$DPKG_ADMINDIR/status" "$DPKG_ADMINDIR/status-old" "$DPKG_ADMINDIR/available" || true
  chmod 600 "$DPKG_ADMINDIR/lock" "$DPKG_ADMINDIR/lock-frontend" || true
}

ensure_sudo_shim() {
  if command -v sudo >/dev/null 2>&1; then
    return 0
  fi

  mkdir -p /usr/local/bin
  cat > /usr/local/bin/sudo <<'EOF'
#!/bin/sh
if [ "$#" -eq 0 ]; then
  exit 0
fi
exec "$@"
EOF
  chmod +x /usr/local/bin/sudo
  echo "installed sudo compatibility shim at /usr/local/bin/sudo"
}

repair_dpkg_state() {
  if ! command -v dpkg >/dev/null 2>&1; then
    return 0
  fi

  if dpkg_cmd --configure -a >/dev/null 2>&1; then
    return 0
  fi

  echo "dpkg recovery: first configure pass failed"
  rm -f "$DPKG_ADMINDIR/lock" "$DPKG_ADMINDIR/lock-frontend" "$ASTRBOT_APT_CACHE_DIR/archives/lock" || true
  apt_cmd update || true
  apt_cmd install -f -y || true
  dpkg_cmd --configure -a >/dev/null 2>&1 || true
}

ensure_base_packages() {
  if command -v curl >/dev/null 2>&1 \
    && command -v unzip >/dev/null 2>&1 \
    && command -v Xvfb >/dev/null 2>&1 \
    && command -v screen >/dev/null 2>&1 \
    && command -v xauth >/dev/null 2>&1 \
    && command -v g++ >/dev/null 2>&1; then
    write_progress 30 "base-ready" 0
    return 0
  fi

  write_progress 15 "install-base" 1
  echo "installing base dependencies from network"
  apt_cmd update
  if apt_cmd install -y curl ca-certificates unzip xvfb screen xauth procps g++; then
    write_progress 30 "base-ready" 0
    return 0
  fi

  echo "base package install failed, retrying after dpkg recovery"
  repair_dpkg_state || true
  apt_cmd update
  apt_cmd install -y curl ca-certificates unzip xvfb screen xauth procps g++
  write_progress 30 "base-ready" 0
}

backup_napcat_config() {
  if [ -d "$NAPCAT_CONFIG_DIR" ]; then
    write_progress 38 "backup-config" 0
    echo "backing up napcat config"
    log_config_snapshot "existing webui.json before backup" "$NAPCAT_CONFIG_DIR/webui.json"
    log_config_snapshot "existing onebot11.json before backup" "$NAPCAT_CONFIG_DIR/onebot11.json"
    rm -rf "$NAPCAT_CONFIG_BACKUP"
    mkdir -p "$NAPCAT_CONFIG_BACKUP"
    cp -R "$NAPCAT_CONFIG_DIR"/. "$NAPCAT_CONFIG_BACKUP"/ 2>/dev/null || true
  fi
}

restore_napcat_config() {
  if [ -d "$NAPCAT_CONFIG_BACKUP" ]; then
    write_progress 82 "restore-config" 0
    echo "restoring napcat config"
    mkdir -p "$NAPCAT_CONFIG_DIR"
    cp -R "$NAPCAT_CONFIG_BACKUP"/. "$NAPCAT_CONFIG_DIR"/ 2>/dev/null || true
    log_config_snapshot "restored webui.json" "$NAPCAT_CONFIG_DIR/webui.json"
    log_config_snapshot "restored onebot11.json" "$NAPCAT_CONFIG_DIR/onebot11.json"
    rm -rf "$NAPCAT_CONFIG_BACKUP"
  fi
}

resolve_qq_binary() {
  local candidate
  for candidate in /opt/QQ/qq /usr/bin/qq /usr/local/bin/qq; do
    if [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

ensure_virtual_display() {
  export DISPLAY="${DISPLAY:-:1}"
  export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/runtime-root}"
  export QT_X11_NO_MITSHM=1
  export LIBGL_ALWAYS_SOFTWARE=1
  export ELECTRON_OZONE_PLATFORM_HINT=x11

  mkdir -p "$XDG_RUNTIME_DIR"
  chmod 700 "$XDG_RUNTIME_DIR" 2>/dev/null || true

  if pgrep -f "Xvfb ${DISPLAY}" >/dev/null 2>&1; then
    echo "virtual display already running on ${DISPLAY}"
    return 0
  fi

  rm -f "/tmp/.X${DISPLAY#:}-lock" "/tmp/.X11-unix/X${DISPLAY#:}" 2>/dev/null || true
  mkdir -p /tmp/.X11-unix
  chmod 1777 /tmp/.X11-unix 2>/dev/null || true

  echo "starting Xvfb on ${DISPLAY}"
  nohup Xvfb "$DISPLAY" -screen 0 1280x720x24 -nolisten tcp -ac >/tmp/xvfb.log 2>&1 &

  local retries=20
  while [ "$retries" -gt 0 ]; do
    if [ -S "/tmp/.X11-unix/X${DISPLAY#:}" ]; then
      echo "virtual display ready on ${DISPLAY}"
      return 0
    fi
    sleep 0.2
    retries=$((retries - 1))
  done

  echo "failed to start Xvfb on ${DISPLAY}" >&2
  return 1
}

ensure_qq_launcher_compat() {
  local qq_bin=""
  if ! qq_bin="$(resolve_qq_binary)"; then
    echo "qq launcher compatibility: binary not found"
    return 1
  fi

  if [ -d /usr/local/bin ] || mkdir -p /usr/local/bin 2>/dev/null; then
    ln -sf "$qq_bin" /usr/local/bin/qq || true
  fi

  if [ -f "$NAPCAT_ENTRY" ] && grep -q 'LD_PRELOAD=./libnapcat_launcher.so qq --no-sandbox' "$NAPCAT_ENTRY"; then
    sed -i "s|LD_PRELOAD=./libnapcat_launcher.so qq --no-sandbox|LD_PRELOAD=./libnapcat_launcher.so ${qq_bin} --no-sandbox|g" "$NAPCAT_ENTRY"
    echo "patched legacy napcat launcher to use ${qq_bin}"
  fi

  return 0
}

ensure_napcat_installed() {
  if [ -f "$NAPCAT_ENTRY" ] && [ -d "$NAPCAT_HOME" ]; then
    echo "existing napcat installation detected"
    mark_installer_cached 1
    return 0
  fi

  mark_installer_cached 0
  backup_napcat_config
  rm -rf "$NAPCAT_HOME"
  rm -f "$NAPCAT_ENTRY"

  echo "downloading napcat installer"
  download_napcat_installer "$NAPCAT_INSTALLER_CACHE"

  write_progress 65 "run-installer" 1
  echo "running upstream napcat installer"
  /bin/bash "$NAPCAT_INSTALLER_CACHE"

  restore_napcat_config

  if [ ! -f "$NAPCAT_ENTRY" ]; then
    echo "napcat installer finished but launcher.sh is missing" >&2
    return 1
  fi

  ensure_qq_launcher_compat
}

write_runtime_config() {
  write_progress 90 "write-config" 0
  mkdir -p "$NAPCAT_CONFIG_DIR"
  echo "writing runtime config: preferred_port=${NAPCAT_WEBUI_PREFERRED_PORT} secret_source=$RUNTIME_SECRET_FILE secret_key_len=${#NAPCAT_WEBUI_SECRET_KEY} jwt_key_len=${#NAPCAT_WEBUI_JWT_SECRET_KEY}"
  cat > "$NAPCAT_CONFIG_DIR/webui.json" <<EOF
{
  "host": "127.0.0.1",
  "port": 6099,
  "token": "${NAPCAT_WEBUI_SECRET_KEY}",
  "disableWebUI": false
}
EOF
  log_config_snapshot "final webui.json after write" "$NAPCAT_CONFIG_DIR/webui.json"

  if [ ! -f "$NAPCAT_CONFIG_DIR/onebot11.json" ] || ! grep -q 'ws://127.0.0.1:6199/ws' "$NAPCAT_CONFIG_DIR/onebot11.json"; then
    echo "writing onebot11.json for ElymBot bridge"
    cat > "$NAPCAT_CONFIG_DIR/onebot11.json" <<'EOF'
{
  "network": {
    "httpServers": [],
    "httpClients": [],
    "websocketServers": [],
    "websocketClients": [
      {
        "name": "WsClient",
        "enable": true,
        "url": "ws://127.0.0.1:6199/ws",
        "messagePostFormat": "array",
        "reportSelfMessage": false,
        "reconnectInterval": 5000,
        "token": "astrbot_android_bridge",
        "debug": false,
        "heartInterval": 30000
      }
    ]
  },
  "musicSignUrl": "",
  "enableLocalFile2Url": false,
  "parseMultMsg": false
}
EOF
  else
    echo "preserving existing onebot11.json"
  fi
  log_config_snapshot "final onebot11.json after write" "$NAPCAT_CONFIG_DIR/onebot11.json"
}

write_progress 10 "prepare-container" 0
load_runtime_secrets
prepare_writable_paths
ensure_sudo_shim
ensure_base_packages
ensure_napcat_installed
ensure_virtual_display
write_runtime_config

write_progress 96 "start-napcat" 1
echo "starting napcat launcher"
exec /bin/bash "$NAPCAT_ENTRY"
