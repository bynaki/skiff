#!/bin/bash
# The personal information check of the commit skill (SKILL.md, steps 2 and 5).
#
#   PRIVATE='<values from ~/private/skiff.md>' .claude/skills/commit/check.sh changes
#   PRIVATE='<values from ~/private/skiff.md>' .claude/skills/commit/check.sh pushed
#
# `changes` scans what is about to be committed (tracked changes and new files), `pushed` the
# commits ahead of the upstream, messages included. Run it from the repo root.
#
# It lives here rather than in SKILL.md because a skill's text has `$0`, `$1`, … replaced by the
# arguments it was invoked with, which turned awk's and perl's own `$0` and `$1` into those words.
# PRIVATE comes from the environment and is never written into this file.

set -u
cd "$(git rev-parse --show-toplevel)" || exit 1
export PATH=/opt/homebrew/share/android-commandlinetools/platform-tools:$PATH

SELF=.claude/skills/commit/check.sh   # holds the patterns themselves
SERIALS=$(adb devices 2>/dev/null | awk 'NR>1 && $1 {split($1,a,":"); print a[1]}' | paste -sd'|' -)

# Every added line as "<file>\t<line>".
diff_lines() { awk '/^\+\+\+ b\//{f=substr($0,7)} /^\+[^+]/{print f"\t"substr($0,2)}'; }

changes() {
  git diff HEAD -U0 --no-color -- . ":(exclude)$SELF" | diff_lines
  git ls-files --others --exclude-standard | grep -vxF "$SELF" | while read -r f; do
    if file -b --mime "$f" | grep -q text; then awk -v f="$f" '{print f"\t"$0}' "$f"
    else printf '%s\t<binary>\n' "$f"; fi
  done
}

pushed() {
  git log -p --no-color --format= @{u}..HEAD -- . ":(exclude)$SELF" | diff_lines
  git log --format=%B @{u}..HEAD | awk '{print "commit message\t"$0}'
}

scan() {
  HOST=$(hostname -s) SERIALS="$SERIALS" PRIVATE="${PRIVATE:-}" perl -ne '
    BEGIN {
      @m = (["host name", qr/\Q$ENV{HOST}\E/i], ["home path", qr{/(Users|home)/[^/\s]+}],
            # bounded, so the package path com.naki.skiff / com/naki/skiff does not match
            ["user name", qr/(?<![.\/\w])\Q$ENV{USER}\E(?![.\/\w])/]);
      push @m, ["device serial", qr/$ENV{SERIALS}/] if $ENV{SERIALS};
      push @m, ["private value", qr/$ENV{PRIVATE}/i] if $ENV{PRIVATE};
    }
    chomp; my ($f, $t) = split /\t/, $_, 2; next unless defined $t;
    if ($t eq "<binary>") { print "$f  [binary: open it before it goes in]\n"; next }
    for (@m) { print "$f  [$_->[0]]  $t\n" if $t =~ $_->[1] }
    while ($t =~ /(?<![\d.])(\d{1,3}(?:\.\d{1,3}){3})(?![\d.])/g) {
      print "$f  [IPv4 $1]  $t\n"
        unless $1 =~ /^(192\.0\.2\.|198\.51\.100\.|203\.0\.113\.|127\.0\.0\.1$|0\.0\.0\.0$)/ }
    while ($t =~ /([\w.%+-]+\@[\w.-]+\.[A-Za-z]{2,})/g) {
      print "$f  [e-mail $1]  $t\n" unless $1 =~ /\@example\.|\.example$|^noreply\@anthropic\.com$/ }
    print "$f  [key or secret]  $t\n"
      if $t =~ /BEGIN [A-Z ]*PRIVATE KEY|ssh-(rsa|ed25519|dss) AAAA|SHA256:[A-Za-z0-9+\/]{30}|(password|passwd|secret|token)\s*[:=]/i;
  '
}

[ -n "${PRIVATE:-}" ] || echo "warning: PRIVATE is empty, so the values in ~/private/skiff.md are not checked" >&2

case "${1:-}" in
  changes)
    echo "--- added lines that match";   hits=$(changes | scan); echo "${hits:-none}"
    echo "--- new images, logs, dumps";   git ls-files --others --exclude-standard | grep -Ei '\.(png|jpe?g|webp|log|hprof|xml|json|pem|key|jks|keystore)$' || echo none
    echo "--- local.properties tracked";  git ls-files | grep -E '(^|/)local\.properties$' || echo no
    ;;
  pushed)
    echo "--- commits"; git log --oneline @{u}..HEAD
    echo "--- added lines that match";   hits=$(pushed | scan); echo "${hits:-none}"
    ;;
  *)
    echo "usage: PRIVATE='…' $0 changes|pushed" >&2
    exit 2
    ;;
esac
