#!/bin/sh
set -eu

network=quantoot-ci
bridge=$(docker network inspect --format '{{index .Options "com.docker.network.bridge.name"}}' "$network")
if [ -z "$bridge" ]; then
    bridge=$(docker network inspect --format 'br-{{slice .Id 0 12}}' "$network")
fi
case "$bridge" in
    ''|*[!a-zA-Z0-9_.-]*) exit 1 ;;
esac

{
    if nft list table inet quantoot_ci_cache >/dev/null 2>&1; then
        printf 'delete table inet quantoot_ci_cache\n'
    fi
    cat <<EOF
table inet quantoot_ci_cache {
    chain input {
        type filter hook input priority -5; policy accept;
        tcp dport 49232 iifname != "lo" drop
        tcp dport 49231 iifname != { "lo", "$bridge" } drop
    }
}
EOF
} | nft -f -
