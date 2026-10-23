#!/bin/bash
# The cases check.sh's rules are there for. Each one runs check.sh in a throwaway repository and
# says whether it caught what it should and let through what it should. Run it after changing a rule:
#
#   .claude/skills/commit/check.test.sh
#
# Nothing here may be a real value. Who and where come from this machine at run time, the private
# values from a file made here, and each token is put together at run time, so this file holds none
# of them whole. check.sh reads this file with its rules about who and where only.

set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d) || exit 2
trap 'rm -rf "$WORK"' EXIT
VALUES=$WORK/values
printf '%s\n' '# a comment' 'zz-private-77' 'box-1' > "$VALUES"
passed=0 failed=0

check() { SKIFF_PRIVATE_VALUES=$VALUES .claude/skills/commit/check.sh "$@" 2>&1; }
section() { awk -v s="$1" 'index($0, "--- " s) == 1 {on = 1; next} /^--- /{on = 0} on'; }
tok() { printf '%s%s' "$1" "$2"; }

result() {
  if [ "$1" = 1 ]; then passed=$((passed + 1)); printf 'ok    %s\n' "$2"
  else failed=$((failed + 1)); printf 'FAIL  %s\n%s\n' "$2" "$3" | sed '2,$s/^/        /'; fi
}

# expect <name> <section> <grep -E pattern, or none> <check.sh output>
expect() {
  local got ok=1
  got=$(section "$2" <<< "$4")
  if [ "$3" = none ]; then [ "$got" = none ] || ok=0
  else grep -qE -- "$3" <<< "$got" || ok=0; fi
  grep -qx -- '--- checked to the end' <<< "$4" || ok=0
  result $ok "$1" "$4"
}

# fails <name> <check.sh output> <its exit status>: it has to stop, not report.
fails() {
  local ok=1
  [ "$3" = 2 ] || ok=0
  grep -qx -- '--- checked to the end' <<< "$2" && ok=0
  result $ok "$1" "exit $3: $2"
}

# line <name> <pattern, or none> <text>: <text> as a staged new file.
line() {
  printf '%s\n' "$3" > case.txt && git add case.txt
  expect "$1" "added lines" "$2" "$(check changes)"
  git rm -q --cached case.txt && rm case.txt
}

repo() {
  rm -rf "$WORK/repo" "$WORK/remote"
  git init -q --bare -b main "$WORK/remote" && git init -q -b main "$WORK/repo" && cd "$WORK/repo" || exit 2
  git config user.name owner && git config user.email owner@mail.test
  mkdir -p .claude/skills/commit && cp "$HERE/check.sh" .claude/skills/commit/ || exit 2
  echo base > README && git add -A && git commit -qm base && git remote add origin "$WORK/remote" && git push -qu origin main 2>/dev/null || exit 2
}

repo
u=Users
host=$(hostname -s)

echo "-- addresses"
line "IPv4"                                   'IPv4 100'  'addr 100.99.1.2 here'
line "IPv4 at the end of a sentence"          'IPv4 100'  '주소는 100.99.1.2.'
line "IPv4 on a line that starts with +"      'IPv4 100'  '+ 100.99.1.2 in a diff block'
line "IPv4 on a line that starts with +++"    'IPv4 100'  '+++ 100.99.1.2'
line "documentation IPv4 passes"              none        'use 192.0.2.10 or 203.0.113.5'
line "a version passes"                       none        'vite 8.3.5 and 1.2.15'
line "Tailscale IPv6"                         'IPv6'      'fd7a:115c:a1e0::1234:5678'
line "full IPv6"                              'IPv6'      'fe80:0:0:0:1a2b:3c4d:5e6f:7a8b'
line "documentation IPv6 passes"              none        'use 2001:db8::1'
line "Kotlin's :: passes"                     none        'String::class and Foo::bar'
line "a time passes"                          none        'Start at 23:13:25'
line "MAC address"                            'MAC'       'wlan a4:c3:f0:12:34:56'
line "MagicDNS name"                          'network name' 'ssh phone.tail1234ab.ts.net'
line "mDNS name"                              'network name' 'connect otherbox.local'
line "mDNS name in an e-mail-like pairing name" 'network name' "paired as someone@otherbox.local"
line "settings.local.json passes"             none        'see .claude/settings.local.json'
line "a package ending in .local passes"      none        'package com.example.fs.local'
line "a call to .local() passes"              none        'val link = SkiffCodeLink.local(path)'
line "the theme key syntax.local passes"      none        '"syntax.local": { "value": "#ffffff" }'
line "an enum named Local passes"             none        'sourceId = SourceId.Local,'

echo "-- keys and secrets"
line "JSON password"                          'key or secret' '"password": "hunter2"'
line "api_key"                                'key or secret' 'api_key: abcdef123456'
line "ecdsa public key"                       'key or secret' 'ecdsa-sha2-nistp256 AAAAE2VjZHNh'
line "SHA256 host key fingerprint"            'key or secret' 'SHA256:abcdefghijklmnopqrstuvwxyz0123456789ABCD'
line "MD5 host key fingerprint"               'MD5'       '43:51:43:a1:b5:fc:8b:b7:0a:3a:a9:b1:0f:66:73:a8'
line "GitHub token"                           'token'     "GH=$(tok gh p_abcdefghijklmnopqrstuvwxyz0123456789)"
line "Anthropic key"                          'token'     "KEY=$(tok sk- ant-api03-abcdefghijklmnop)"
line "Tailscale auth key"                     'token'     "$(tok tskey -auth-abcdefghijklmnop)"
line "AWS access key"                         'token'     "$(tok AK IAABCDEFGHIJKLMNOP)"

echo "-- who and where"
line "host name"                              'host name' "built on $host"
line "home path"                              'home path' "/$u/someone/projects"
line "the docs' placeholder home passes"      none        'startPath = "/home/alice/demo"'
line "the placeholder home in quotes passes"  none        'store.add("domain", "/home/alice")'
line "a home that only starts like one"       'home path' "/$u/alicesmith/x"
line "user name"                              'user name' "logged in as $USER today"
line "package path passes"                    none        "com.$USER.app"
line "e-mail"                                 'e-mail'    'write to someone@mail.test'
line "attribution e-mail passes"              none        'Co-Authored-By: Claude <noreply@anthropic.com>'
line "a Kotlin label passes"                  none        'this@SftpTestServer.username'
line "an SSH extension name passes"           none        'statvfs@openssh.com'
line "private value"                          'private value' 'zz-private-77'
line "private value in another case"          'private value' 'BOX-1'
line "private value running on with a dash"   'private value' 'zz-private-77-v2'
line "private value running on as a word passes" none     'box-12'

echo "-- what is read"
printf '\x89PNG\r\n\x1a\n\0\0\0\rIHDR' > shot.png
expect "untracked image is listed"   "new images" 'shot\.png' "$(check changes)"
git add shot.png
out=$(check changes)
expect "staged image is listed"      "new images" 'shot\.png' "$out"
expect "staged binary is reported"   "added lines" 'shot\.png.*binary' "$out"
git rm -q --cached shot.png && rm shot.png
printf '*.dat binary\n' > .gitattributes && git add .gitattributes && git commit -qm attr && git push -q 2>/dev/null
echo 'addr 100.99.1.2' > x.dat && git add x.dat
expect "text file .gitattributes calls binary" "added lines" 'x\.dat.*IPv4' "$(check changes)"
git rm -q --cached x.dat && rm x.dat
printf 'addr 100.99.1.2\n' | iconv -f utf-8 -t utf-16 > u16.txt
expect "UTF-16 file"                 "added lines" 'u16\.txt.*IPv4' "$(check changes)"
rm u16.txt
echo 'addr 100.99.1.2' > notes.md && git add notes.md && git commit -qm notes && echo 'and 100.99.9.9' >> notes.md
expect "unstaged change to a tracked file" "added lines" 'IPv4 100\.99\.9\.9' "$(check changes)"
git checkout -q notes.md
echo '# 100.99.1.2' >> .claude/skills/commit/check.sh
expect "check.sh's own lines: no address rule" "added lines" none "$(check changes)"
echo '# zz-private-77' >> .claude/skills/commit/check.sh
expect "check.sh's own lines: private value" "added lines" 'check\.sh.*private value' "$(check changes)"
git checkout -q .claude/skills/commit/check.sh

echo "-- failing closed"
out=$(SKIFF_PRIVATE_VALUES=$WORK/missing .claude/skills/commit/check.sh changes 2>&1); fails "no values file" "$out" $?
printf '# only a comment\n\n' > "$WORK/empty"
out=$(SKIFF_PRIVATE_VALUES=$WORK/empty .claude/skills/commit/check.sh changes 2>&1); fails "values file with no values" "$out" $?
echo secret > locked.txt && chmod 000 locked.txt
out=$(check changes); fails "an untracked file that cannot be read" "$out" $?
chmod 600 locked.txt && rm locked.txt

echo "-- pushed"
git push -q 2>/dev/null
echo 'clean' > c.txt && git add c.txt && git commit -qm 'clean commit'
expect "own e-mail as author and committer passes" "added lines" none "$(check pushed)"
git push -q 2>/dev/null
echo y > y.txt && git add y.txt && git commit -qm y --author "t <$USER@$host.local>"
expect "author e-mail that names this machine" "added lines" '^author .*(host name|network name)' "$(check pushed)"
git push -q 2>/dev/null
echo z > z.txt && git add z.txt && git commit -qm 'see 100.99.1.2'
expect "address in a commit message" "added lines" '^commit message.*IPv4' "$(check pushed)"
git push -q 2>/dev/null
printf '\x89PNG\r\n\x1a\n\0\0\0\rIHDR' > p.png && git add p.png && git commit -qm png
out=$(check pushed)
expect "binary in a commit is reported" "added lines" 'p\.png.*binary' "$out"
expect "image in a commit is listed" "new images" 'p\.png' "$out"
git push -q 2>/dev/null
git checkout -qb side && echo s > s.txt && git add s.txt && git commit -qm s && git checkout -q main
git merge -q --no-ff --no-commit side 2>/dev/null; echo 'evil 100.99.5.6' > evil.txt && git add evil.txt && git commit -qm merge
expect "content only in a merge commit" "added lines" 'evil\.txt.*IPv4' "$(check pushed)"
git push -q 2>/dev/null
git checkout -qb feature && echo f > f.txt && git add f.txt && git commit -qm f
out=$(check pushed); fails "a branch with no upstream" "$out" $?
expect "a branch with no upstream, against a base" "added lines" none "$(check pushed main)"
out=$(check pushed no-such-ref); fails "a base that does not exist" "$out" $?

echo
echo "$passed passed, $failed failed"
[ "$failed" = 0 ]
