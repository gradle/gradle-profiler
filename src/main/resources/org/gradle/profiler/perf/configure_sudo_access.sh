#!/bin/bash
realuser=${1:-$USER}
gradle_user_home=${2:-$HOME/.gradle-profiler}
if [ "$realuser" = "root" ]; then
    echo "usage: $0 [userid] [gradle_user_home]"
    exit 1
fi

cat >/tmp/sudoers_perf$$ <<EOF
Defaults!/usr/bin/perf use_pty
$realuser ALL=(ALL) NOPASSWD: /usr/bin/perf, /bin/kill, $gradle_user_home/tools/brendangregg/Misc/java/jmaps-updated
EOF

# verify configuration in temp file
sudo visudo -c -f /tmp/sudoers_perf$$ || { echo "sudo configuration couldn't be verified."; exit 1; }

# install to /etc/sudoers.d directory
sudo chmod 0660 /tmp/sudoers_perf$$ \
 && sudo chown root:root /tmp/sudoers_perf$$ \
 && sudo mv /tmp/sudoers_perf$$ /etc/sudoers.d/gradle-profiler_$realuser

# verify configuration
sudo visudo -c -f /etc/sudoers.d/gradle-profiler_$realuser && echo "sudo configuration successful."