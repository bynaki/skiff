# git (프로젝트 모드)

[목차](../skiffcode.spec.md)

- **`RemoteExec`:**
  - 프로젝트별로 exec 전용 SSHClient를 둔다(`SshClientFactory`). SFTP의 단일 스레드 dispatcher와 분리해서 오래 도는 채널이 탐색을 막지 않게 한다.
  - **명령은 argv 리스트로만 만들고 `ShellQuote`로 인자마다 작은따옴표로 감싼다.** 경로는 URI에서 오는 외부 입력이다.
  - **exec가 되는 계정인지는 `supported()`가 정해 둔 문자열을 `printf`로 돌려받는지로 본다.** 요청 거부만 보면 모자라다: 강제된 `internal-sftp`는 요청을 받고 명령 대신 자기를 돌리며(빈 출력, 0), `nologin`은 거절 문구를 찍고 1로 끝난다. 실제 OpenSSH 10.0(Debian 13)의 `ForceCommand internal-sftp`는 요청을 받고 "This service allows sftp connections only."를 찍고 1로 끝났다(2026-10-04 확인). 빈 출력에 0으로 끝나는 서버는 실제로는 보지 못했고 MINA 테스트로만 흉내 냈다. 그래서 `command -v git` 하나로는 "git 없음"과 "exec 안 됨"을 가를 수 없다.
  - stdin은 쓰지 않으면 바로 닫는다(EOF). 강제된 `internal-sftp`는 stdin에서 SFTP 패킷을 기다리므로, 닫지 않으면 시간 초과까지 채널을 붙잡는다.
  - 명령마다 시간 상한이 있다(기본 30초). sshj의 채널 스트림은 스스로 시간 제한이 없다.
  - 링크가 끊기면 SFTP처럼 한 번 다시 접속해 같은 명령을 다시 돌린다. 그래서 여기서 돌리는 명령은 두 번 돌아도 되는 것(읽기)이어야 한다.
- **`GitService`:**
  - `git -C <root> show HEAD:<rel>`: 비교 기준 텍스트.
  - `git log -n 2 --format=%H -- <rel>`: 두 번째 비교 대상.
  - `git ls-files -co --exclude-standard -z`: 파일 검색.
  - 결과는 커밋 해시를 키로 캐시한다. 해시 하나는 내용 하나를 영영 가리키므로 캐시가 낡지 않고, 매번 묻는 것은 HEAD가 어느 커밋인지(`cat-file`에 `HEAD`) 하나뿐이다. 내용과 이력을 각각 최근 30개(열린 파일 상한과 같다)까지 둔다.
  - **`ls-files`는 캐시하지 않는다.** 추적하지 않는 파일은 HEAD가 그대로여도 생기고 사라진다. 팔레트를 열 때마다 한 번 부른다([팔레트](palette.md)의 파일 검색).
  - `log`는 `--literal-pathspecs`로 돌린다. `*.md`나 `:(glob)…` 같은 이름의 파일이 패턴으로 읽히지 않게 한다.
  - **기준 텍스트는 `git -C <root> cat-file --batch` 채널 하나를 길게 열어 읽는다.** exec의 진짜
    약점은 명령마다 채널 왕복인데, 파일마다 `show`를 부르면 왕복이 파일 수만큼 는다. `--batch`는
    stdin에 `HEAD:<rel>`을 쓰면 `<sha> <type> <size>\n<내용>`을 돌려주므로 왕복이 사라진다.
    길이를 먼저 읽고 그만큼 채워 읽는 모양이라 `LspProcess`의 프레이밍과 같고, 유휴일 때
    `LspManager`처럼 닫는다. 데몬 없이 데몬의 이점 하나를 가져오는 자리다.
    - 채널은 `RemoteExec.start`가 연다. `run`과 달리 stdin을 닫지 않고 돌려준다. `cat-file`은 stderr를 읽지 않는다 — 실패할 때만 쓰기 때문이다. 거기에 많이 쓰는 명령은 읽지 않으면 멈춘다(`LspProcess`).
    - **60초 동안 쓰지 않으면 닫고**, 다음에 물을 때 다시 연다. 답을 읽다가 실패하면(상한 초과, 시간 초과, 끊김) 스트림이 객체 중간에 있으므로 그 채널은 버린다. 끊긴 것이면 한 번 다시 열어 같은 것을 묻는다.
    - 상한을 넘는 객체는 읽지 않는다(`GitObjectTooLarge`). 작업 파일은 작아도 HEAD의 같은 경로가 클 수 있다.
- **git 거터:** Web에서 `Chunk.build(기준, 버퍼)`로 추가, 수정, 삭제를 줄 번호 옆의 가는 세로줄로 표시한다. 세 레이어 모두에 적용한다.
  - 기준은 브리지 `baseline(id)`가 준다. Kotlin이 `head()` 다음 `show(HEAD, 상대 경로, 파일 크기 상한)`로 읽고, **그 파일을 읽은 방식 그대로**(`TextFormat`의 인코딩, BOM, 줄바꿈은 `\n`으로) 디코딩한다(`TextLoader.decodeLike`). 그래야 EUC-KR이나 CRLF 파일이 줄마다 다르게 보이지 않는다. 그렇게 디코딩되지 않거나, HEAD에 없거나(새 파일, 커밋 전 레포), 상한을 넘으면 기준이 없고 거터도 없다. 추적하지 않는 새 파일도 거터가 없다.
  - 기준은 레이어 compartment 밖의 `StateField`에 들고, 받을 때 한 번 `Chunk.build`하고 그 뒤로는 편집 주변만 `Chunk.updateB`로 고친다. 같은 기준을 다시 받으면 다시 만들지 않는다. diff 설정은 diff 레이어와 같은 줄 단위(`lineDiffConfig`)다.
  - 표시: 추가는 `--diff-added`, 수정은 `--diff-changed`(테마의 `[diff] changed`, 2026-10-02 사용자 결정 — 번들 값은 각 모드의 accent 색이다), 지운 줄은 그 다음 줄 위 경계에 `--diff-removed` 쐐기. 렌더된 markdown은 CodeMirror가 아니라 거터가 없고, 그 파일은 editor에서 보인다.
  - **기준을 다시 묻는 때**(2026-10-02 사용자 결정): 파일이 화면에 올 때(열기, 전환), 화면의 파일이 바뀐 것을 감시가 잡았을 때(checkout·pull은 파일과 HEAD를 같이 바꾼다), 앱이 앞으로 돌아올 때(`gitChanged`). 폴링마다 묻지는 않는다 — 그러면 `cat-file` 채널이 유휴로 닫히지 않는다. 그래서 앱을 보는 중에 서버에서 커밋만 하면 다음 전환이나 복귀까지 거터가 낡아 있다.
  - **지운 프로젝트의 열린 파일은 거터를 잃는다**(2026-10-02 사용자 결정). 프로젝트를 지우면 세션과 `GitService`가 같이 닫히고(`ProjectSession.close`), 그 파일 때문에 다시 접속하지 않는다.
  - `@codemirror/merge`의 `diff`는 양쪽 다 비어 있는 변경을 가끔 내놓는다. 거터는 그런 청크를 건너뛴다. 아니면 지운 줄로 그려진다.

## 성공 조건

이 주제가 됐다고 말할 수 있는 조건과 확인 방법이다(`AGENTS.md`). ✅는 충족, ⬜는 아직이다. 확인한 때와 자세한 내역은
[`skiffcode.plan.done.md`](../skiffcode.plan.done.md)의 그 마일스톤에 있다.

- ✅ exec가 되는 계정인지를 `supported()`가 가린다(internal-sftp는 `NoExec`, git 없는 bash 계정은 `Missing`) — 확인: `RemoteExecTest`, `CheckGitTest`, Docker 계정 셋으로 탭에서(M5).
- ✅ `GitService`가 실제 `git`으로 HEAD 내용·이력·`ls-files`를 읽고, `cat-file --batch` 채널 하나를 다시 쓴다 — 확인: `GitServiceTest`(MINA + 실제 git).
- ✅ git 거터가 세 레이어에서 서버의 `git diff -U0`와 같다 — 확인: vitest `gitGutter.test.ts`, 탭(M5).
- ✅ 기준을 다시 묻는 때(화면에 올 때, 감시가 바뀐 것을 잡았을 때, 앱으로 돌아올 때)에 거터가 새로 그려진다 — 확인: 탭(M5).
