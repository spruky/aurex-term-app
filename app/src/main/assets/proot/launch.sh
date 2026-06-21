#!/system/bin/sh
# launch.sh — drop into the embedded Debian shell via proot.
#
# Assumes bootstrap.sh has already populated $ROOTFS. Wires up the standard
# proot bind mounts so /dev, /proc, /sys and DNS work inside the guest, then
# execs login as the guest's shell.
#
# Env (exported by TerminalActivity):
#   PREFIX     app filesDir
#   ROOTFS     $PREFIX/debian
#   PROOT      path to the native proot binary (from jniLibs)
#   TMPDIR     writable temp dir
set -eu

: "${TERM:=xterm-256color}"
export TERM
export HOME=/root
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export LANG=C.UTF-8

# proot needs a loader path and a writable tmp for its own bookkeeping.
export PROOT_TMP_DIR="$TMPDIR"
export PROOT_LOADER="${PROOT_LOADER:-$PREFIX/libloader.so}"

exec "$PROOT" \
    --kill-on-exit \
    -r "$ROOTFS" \
    -0 \
    -w /root \
    -b /dev \
    -b /proc \
    -b /sys \
    -b "$ROOTFS/etc/resolv.conf:/etc/resolv.conf" \
    -b /data/data \
    /usr/bin/env -i \
        HOME=/root \
        TERM="$TERM" \
        LANG=C.UTF-8 \
        PATH="$PATH" \
        /bin/login -f root 2>/dev/null \
    || exec "$PROOT" --kill-on-exit -r "$ROOTFS" -0 -w /root \
        -b /dev -b /proc -b /sys \
        -b "$ROOTFS/etc/resolv.conf:/etc/resolv.conf" \
        /bin/bash -l
