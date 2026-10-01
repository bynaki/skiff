---
name: commit
description: Use only when the user asks to commit in this repo ("커밋해", "커밋하자", "commit this", /commit) — never on your own when work is finished; ask "커밋할까요?" instead. Checks what is about to be committed for personal information and security weaknesses, reports the result in Korean, and commits on its own when every check is clean — it asks only when one is not. Also use when the user asks whether privacy or security was checked. Also use when the user asks to push ("push해", "푸시하자", "push this", or a yes to "push할까요?") — then only step 5 runs; never push without that request.
model: sonnet
effort: medium
---

# Commit: check, report, commit

The rules behind this are `AGENTS.md`'s "Before committing". This skill is the order to carry
them out in. **Every check below is run in this turn, and its output is what the report is made
of — never report a check from memory or from an earlier turn.** Only the user's commit request
starts this procedure: finished work, or a "go ahead" for it, is not one — that ends in asking
whether to commit (user's decision, 2026-09-27). When every check comes back clean it ends in the
commit, reported; when one does not, it ends in a question (user's decision, 2026-09-26).

## 1. What goes in

```bash
git status --short
git diff HEAD --stat
git ls-files --others --exclude-standard     # new files that would be added
```

If it is unclear which changes belong in this commit (several unrelated pieces, or files you did
not touch this session), propose a split and ask, rather than committing everything. Read the
whole diff (`git diff HEAD`, plus every new file) before going on.

## 2. Personal information

Run `check.sh` (next to this file) from the repo root. It prints every added line (tracked
changes and new files) that matches, with its file and what it matched. Before running it, read
`~/private/skiff.md` and put every value there (addresses, MagicDNS names, pairing names) into
`PRIVATE`, `|`-separated with dots escaped — **in the command only, never in a file in the repo.**

```bash
PRIVATE='<values from ~/private/skiff.md>' .claude/skills/commit/check.sh changes
```

The patterns are in the script, not here, because a skill's text has its positional references
replaced by the arguments it was invoked with — awk's and perl's own ones came out as the user's
words. Keep shell code that needs them in the script.

A hit is fixed at the source (`192.0.2.x`, `.example`, a placeholder) and the whole check run
again — never committed around. A password in a test is fine only when it authenticates nothing
outside the test JVM, like `SftpTestServer`'s. Open every image, log and dump before it goes in:
screenshots and logcat carry the same details as source. The patterns cannot see a value written
in a shape they do not match, so the diff you read in step 1 is part of this check too.

## 3. Security weaknesses

Read the diff for these, and name the file and line for anything found. Say "해당 없음" for a
category the diff does not touch, rather than skipping it.

- **Connection guards** (`AGENTS.md` says none of them fails a test when undone): the host key gate
  (`HostKeyGate`, `KnownHostStore`, never `PromiscuousVerifier`), `SecretStore`'s Keystore
  encryption, `exec` only in `RemoteExec` with every argument through `ShellQuote` and never in
  `:app`, and `SshConnection`'s handling of `SFTPException`. Any change under `fs/sftp/` or
  `data/crypto/` gets a second read for that reason alone.
- **Links from outside**: a `skiffcode://` path is never opened without `OpenFlow.confirmPath`
  unless the sender is Skiff, the sender is identified with `ComponentCaller` and
  `checkSignatures`, never `getReferrer()`.
- **The WebView bridge**: one `addWebMessageListener`, the single allowed origin, no
  `addJavascriptInterface`, assets only through `WebViewAssetLoader`, and what arrives from the page
  checked before it is believed.
- **The manifest**: new permissions, `exported` components, intent filters, `allowBackup`.
- **General**: paths built from input that could leave the intended directory, deletes or writes
  that follow a symlink out of the tree, secrets or server details written to logs, `localStorage`
  holding anything that identifies a server or a person, TLS or host verification relaxed, a
  dependency added or bumped (say which, and why).

## 4. Report, then commit or ask

**Clean** means all of these: step 1 found nothing unclear about what belongs in the commit, step 2
printed `none` for every check (or its hits were fixed at the source and the rerun came back
`none`), step 3 found nothing in any category, and nothing was left unchecked — no binary, image or
log went in unopened. Anything else is not clean.

In Korean, in this shape:

```
커밋 전 점검 결과
- 커밋할 것: <files> — <one-line summary>  (메시지 초안: "<subject>")
- 개인정보: <없음 | 무엇이 어디에 있었고 어떻게 고쳤는지>
- 보안: <범주별로 해당 없음 | 발견한 것과 판단>
- 돌린 것: <the commands above, and anything not checked, e.g. a binary not opened>
<clean: 커밋했습니다: <hash> "<subject>" | not clean: what is wrong, and 커밋할까요?>
```

**Clean:** commit exactly what the report lists, with the attribution lines the session gives,
and put the hash in the report. **Not clean:** do not commit — say what was found and ask, then
**stop**; commit only on an explicit yes to that question, which covers this commit only. Either
way, send a push notification with a one-line version (the user often steps away). If the user
asks for changes, make them and run steps 1–4 again.

## 5. Push

**Invoked for a push request, start here and skip steps 1–4.** The request itself is the explicit
yes `AGENTS.md` asks for; it covers the commits ahead now, not later ones.

After a commit, say how many commits the branch is ahead of its upstream and **ask whether to
push** — a commit, whether it asked or not, is not a yes to push. Before a push, run step 2
against the commits being pushed, messages included:

```bash
PRIVATE='<values from ~/private/skiff.md>' .claude/skills/commit/check.sh pushed
```

Then the same rule as a commit: when that check prints `none`, `git push` and report in Korean which
commits went up (`git log --oneline` of them) and to where; when it finds something, say what and
where, ask, and push only on a yes. Either way, send a push notification with a one-line version.
