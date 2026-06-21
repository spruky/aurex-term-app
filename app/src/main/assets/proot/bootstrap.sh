#!/system/bin/sh
# bootstrap.sh — first-run setup for the embedded Debian rootfs.
#
# Runs once. Unpacks the rootfs tarball that CI dropped into assets, lays down
# the files proot needs (resolv.conf, hosts), and stamps a marker so we never
# do this again. Everything lives under the app's private files dir, which the
# app passes in as $PREFIX.
#
# Env (exported by TerminalActivity before exec):
#   PREFIX     app filesDir (writable, app-private)
#   ROOTFS     $PREFIX/debian            target rootfs dir
#   TARBALL    path to the rootfs tarball copied out of assets
#   MARKER     $ROOTFS/.installed
set -eu

log() { printf '\033[2m[bootstrap]\033[0m %s\n' "$1"; }

if [ -f "$MARKER" ]; then
    log "rootfs already installed"
    exit 0
fi

log "preparing Debian rootfs (first run, one time)…"
rm -rf "$ROOTFS"
mkdir -p "$ROOTFS"

# The tarball is a Debian rootfs (proot-distro style). It may be .tar.xz or
# .tar.gz; busybox tar autodetects compression with -a / by extension.
log "unpacking $(basename "$TARBALL")…"
tar -xf "$TARBALL" -C "$ROOTFS" 2>/dev/null || \
    tar -xJf "$TARBALL" -C "$ROOTFS"

# Network plumbing — proot mounts these over the guest's versions.
mkdir -p "$ROOTFS/etc"
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$ROOTFS/etc/resolv.conf"
cat > "$ROOTFS/etc/hosts" <<'EOF'
127.0.0.1 localhost
::1 localhost ip6-localhost ip6-loopback
EOF

# A friendly first-boot banner.
cat > "$ROOTFS/etc/motd" <<'EOF'

  AUREX TERM — Debian (proot)
  Unprivileged userspace container. No root device access.
  Try:  apt update && apt install -y neofetch && neofetch

EOF

touch "$MARKER"
log "done."
