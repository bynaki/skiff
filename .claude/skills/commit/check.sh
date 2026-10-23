#!/bin/bash
# The personal information check of the commit skill (SKILL.md, steps 2 and 5).
#
#   .claude/skills/commit/check.sh changes            # what is about to be committed
#   .claude/skills/commit/check.sh pushed [<base>]    # what a push would send
#
# `changes` scans every tracked change against HEAD, staged or not, and every new file. `pushed`
# scans the commits ahead of the upstream (or of <base>): their diffs, merges included, their
# messages, and who wrote and committed them. A file git shows no lines for — a binary, or one
# `.gitattributes` calls binary — is read whole, and reported to be opened when it is not text.
#
# The private values come from ~/private/skiff.values (SKIFF_PRIVATE_VALUES names another file):
# one value per line, taken literally, `#` for comments. They are never written into the repo.
#
# It fails closed. A check that cannot run to the end says so and exits 2, and only a run that
# reached the end prints `--- checked to the end`, so an error is never read as "none".
#
# It lives here rather than in SKILL.md because a skill's text has `$0`, `$1`, … replaced by the
# arguments it was invoked with, which turned awk's and perl's own `$0` and `$1` into those words.
# check.test.sh beside it holds the case each rule is there for; run it after changing a rule.
#
# macOS's bash is 3.2, where `set -e` does not reliably reach into functions on the left of a pipe
# or into `$(…)`, so every step that can fail says so itself with `|| die`.

set -uo pipefail
die() { echo "check FAILED: $*. The check did not run to the end, so nothing it printed counts as clean." >&2; exit 2; }
cd "$(git rev-parse --show-toplevel)" || die "not in a git repository"
export PATH=/opt/homebrew/share/android-commandlinetools/platform-tools:$PATH

DIR=.claude/skills/commit
# These two hold the patterns and the cases for them, so only the rules about who and where read them.
OWN="$DIR/check.sh $DIR/check.test.sh"

git() { command git -c core.quotePath=false "$@"; }
DIFF=(--no-renames --no-ext-diff --no-textconv --no-color --src-prefix=a/ --dst-prefix=b/)
MEDIA='\.(png|jpe?g|gif|webp|heic|bmp|tiff?|svg|pdf|mp4|mov|webm|log|txt|hprof|trace|pb|db|sqlite3?|har|pcap|xml|json|pem|key|p12|jks|keystore|zip|tar|gz)$|\.corrupt-[0-9]+$'

VALUES_FILE=${SKIFF_PRIVATE_VALUES:-$HOME/private/skiff.values}
[ -r "$VALUES_FILE" ] || die "there is no private values file at $VALUES_FILE"
VALUES=$(grep -v -e '^[[:space:]]*#' -e '^[[:space:]]*$' "$VALUES_FILE")
[ -n "$VALUES" ] || die "$VALUES_FILE lists no values"
HOSTS=$( { hostname -s; scutil --get LocalHostName; scutil --get ComputerName; } 2>/dev/null | sort -u)
SERIALS=$( { adb devices 2>/dev/null || true; } | awk 'NR>1 && $1 {split($1,a,":"); print a[1]}')
OWN_EMAIL=$(git config user.email || true)

# Every added line of a diff as "<file>\t<line>". Inside a hunk every `+` line is content, `+++` too.
diff_lines() {
  awk '
    /^diff --git / { hunk = 0; next }
    !hunk && /^\+\+\+ / { f = substr($0, 5); gsub(/^"|"$/, "", f); sub(/^b\//, "", f); next }
    /^@@/ { hunk = 1; next }
    hunk && /^\+/ { print f "\t" substr($0, 2) }'
}

# stdin as "<label>\t<line>", or one "<label>\t<binary>" when it is not text.
emit() {
  local tmp enc
  tmp=$(mktemp) || return 1
  cat > "$tmp" || { rm -f "$tmp"; return 1; }
  enc=$(file -b --mime-encoding "$tmp") || { rm -f "$tmp"; return 1; }
  case $enc in
    binary) if [ -s "$tmp" ]; then printf '%s\t<binary>\n' "$1"; fi ;;
    utf-16*|utf-32*) iconv -f "$enc" -t utf-8 "$tmp" | awk -v f="$1" '{print f "\t" $0}' || { rm -f "$tmp"; return 1; } ;;
    *) awk -v f="$1" '{print f "\t" $0}' "$tmp" ;;
  esac
  rm -f "$tmp"
}

changes() {
  git diff HEAD -U0 "${DIFF[@]}" | diff_lines || die "git diff failed"
  git diff HEAD --numstat -z --diff-filter=d "${DIFF[@]}" |
    while IFS=$'\t' read -r -d '' added deleted path; do
      if [ "$added" = - ]; then emit "$path" < "$path" || die "cannot read $path"; fi
    done || die "git diff --numstat failed"
  git ls-files -z --others --exclude-standard |
    while IFS= read -r -d '' path; do emit "$path" < "$path" || die "cannot read $path"; done || die "git ls-files failed"
}

pushed() {
  git log -p --diff-merges=first-parent "${DIFF[@]}" --format= "$RANGE" | diff_lines || die "git log -p failed"
  git log --diff-merges=first-parent "${DIFF[@]}" --numstat --diff-filter=d --format=@%H "$RANGE" |
    while IFS=$'\t' read -r added deleted path; do
      case $added in
        @*) commit=${added#@} ;;
        -) git cat-file blob "$commit:$path" | emit "$path (${commit:0:7})" || die "cannot read $path in $commit" ;;
      esac
    done || die "git log --numstat failed"
  git log --format='author%x09%an <%ae>%ncommitter%x09%cn <%ce>' "$RANGE" || die "git log failed"
  git log --format=%B "$RANGE" | awk '{print "commit message\t" $0}' || die "git log failed"
}

scan() {
  HOSTS="$HOSTS" SERIALS="$SERIALS" VALUES="$VALUES" OWN="$OWN" OWN_EMAIL="$OWN_EMAIL" USER="${USER:-$(id -un)}" perl -ne '
    BEGIN {
      # A value is matched as written, in any case; at an end that is a letter or digit it must not
      # run on into more of them, so box-1 does not match box-12.
      sub literal { my $v = shift; my $r = quotemeta $v;
                    $r = "(?<!\\w)$r" if $v =~ /^\w/; $r .= "(?!\\w)" if $v =~ /\w$/; qr/$r/i }
      push @who, ["host name", qr/\Q$_\E/i] for grep { length } split /\n/, $ENV{HOSTS};
      # alice and bob are the placeholder people the docs and tests use.
      push @who, ["home path", qr{/(?:Users|home)/(?!(?:alice|bob)(?![\w.-]))[^/\s]+|\b[A-Za-z]:\\Users\\[^\\\s]+}];
      # bounded, so the package path com.naki.skiff / com/naki/skiff does not match
      push @who, ["user name", qr/(?<![.\/\w])\Q$ENV{USER}\E(?![.\/\w])/] if length $ENV{USER};
      push @who, ["device serial", literal($_)] for grep { length } split /\n/, $ENV{SERIALS};
      push @who, ["private value", literal($_)] for grep { length } split /\n/, $ENV{VALUES};
      %own = map { $_ => 1 } split / /, $ENV{OWN};
    }
    chomp; my ($f, $t) = split /\t/, $_, 2; next unless defined $t;
    if ($t eq "<binary>") { print "$f  [binary: open it before it goes in]\n"; next }
    my @hit = map { $_->[0] } grep { $t =~ $_->[1] } @who;
    unless ($own{$f}) {
      while ($t =~ /(?<!\d)(?<!\d\.)(\d{1,3}(?:\.\d{1,3}){3})(?!\.?\d)/g) {
        push @hit, "IPv4 $1" unless $1 =~ /^(?:192\.0\.2\.|198\.51\.100\.|203\.0\.113\.|127\.|0\.0\.0\.0$|255\.255\.)/ }
      while ($t =~ /(?<![\w:.-])((?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2})(?![\w:.-])/gi) { push @hit, "MAC $1" }
      while ($t =~ /(?<![\w:.])((?:[0-9a-f]{1,4}:){4,7}[0-9a-f]{1,4}|(?:[0-9a-f]{1,4}:){1,6}(?::[0-9a-f]{1,4}){1,6}|(?:[0-9a-f]{1,4}:){1,7}:)(?![\w:])/gi) {
        my $a = $1;
        push @hit, "IPv6 $a" unless $a =~ /^2001:0?db8:/i || $a =~ /^(?:[0-9a-f]{2}:){5}[0-9a-f]{2}$/i }
      while ($t =~ /(?<![\w.-])([\w-]+(?:\.[\w-]+)*\.ts\.net)(?![\w.-])/gi) { push @hit, "network name $1" }
      # An mDNS name is one label before a lowercase .local; a package path (fs.local), a call
      # (Link.local()), an enum (SourceId.Local) and the theme key syntax.local are not.
      while ($t =~ /(?<![\w.-])([\w-]+\.local)(?![\w.(-])/g) { push @hit, "network name $1" unless lc $1 eq "syntax.local" }
      while ($t =~ /([\w.%+-]+\@[\w.-]+\.[A-Za-z]{2,})/g) {
        my $e = $1;
        # Kotlin labels (this@Outer) and SSH extension names (statvfs@openssh.com) are not addresses.
        next if $e =~ /\@example\.|\.example$|^noreply\@anthropic\.com$|\@users\.noreply\.github\.com$|^(?:this|super|return|break|continue)\@|\@openssh\.com$/i;
        next if ($f eq "author" || $f eq "committer") && length $ENV{OWN_EMAIL} && lc $e eq lc $ENV{OWN_EMAIL};
        push @hit, "e-mail $e" }
      push @hit, "key or secret"
        if $t =~ /BEGIN [A-Z ]*PRIVATE KEY|(?:ssh-(?:rsa|ed25519|dss)|ecdsa-sha2-nistp\d+|sk-(?:ssh-ed25519|ecdsa-sha2-nistp256)\@openssh\.com) AAAA|SHA256:[A-Za-z0-9+\/]{30}|(?:password|passwd|passphrase|secret|token|api[_-]?key|access[_-]?key|client[_-]?secret)["\x27]?\s*[:=]/i;
      push @hit, "host key fingerprint (MD5)" if $t =~ /(?<![\w:])(?:[0-9a-f]{2}:){15}[0-9a-f]{2}(?![\w:])/i;
      push @hit, "token"
        if $t =~ /\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_\w{20,}|sk-ant-[\w-]{10,}|sk-(?:proj-)?[A-Za-z0-9]{20,}|tskey-[\w-]{10,}|AKIA[0-9A-Z]{16}|xox[abprs]-[\w-]{10,}|AIza[\w-]{30,})/;
    }
    print "$f  [$_]  $t\n" for @hit;
  '
}

# New files whose kind is worth opening before it goes in: images, logs, dumps, keys.
media() { grep -Ei "$MEDIA" <<< "$1" || true; }

case "${1:-}" in
  changes)
    hits=$(changes | scan) || die "the scan failed"
    new=$( { git diff HEAD --name-only --diff-filter=A "${DIFF[@]}" && git ls-files --others --exclude-standard; } ) || die "listing new files failed"
    tracked=$(git ls-files) || die "git ls-files failed"
    echo "--- added lines that match";   echo "${hits:-none}"
    echo "--- new images, logs, dumps";   m=$(media "$new"); echo "${m:-none}"
    echo "--- local.properties tracked";  grep -E '(^|/)local\.properties$' <<< "$tracked" || echo no
    ;;
  pushed)
    if [ -n "${2:-}" ]; then base=$2
    else base=$(git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null) ||
      die "this branch has no upstream; name what it is compared against, e.g. check.sh pushed origin/main"
    fi
    git rev-parse --verify -q "$base^{commit}" > /dev/null || die "no such commit: $base"
    RANGE="$base..HEAD"
    commits=$(git log --oneline "$RANGE") || die "git log failed"
    hits=$(pushed | scan) || die "the scan failed"
    new=$(git log --diff-merges=first-parent "${DIFF[@]}" --name-only --diff-filter=A --format= "$RANGE") || die "listing new files failed"
    echo "--- commits ($RANGE)"; echo "${commits:-none}"
    echo "--- added lines that match";   echo "${hits:-none}"
    echo "--- new images, logs, dumps";   m=$(media "$new"); echo "${m:-none}"
    ;;
  *)
    echo "usage: check.sh changes | check.sh pushed [<base>]" >&2
    exit 2
    ;;
esac
echo "--- checked to the end"
