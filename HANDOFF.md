# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일 목록과 설계: [`plan.md`](plan.md)의 `# Skiff Code` 섹션
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 두 문서에 없는 **직전 세션의 맥락**(무엇을 했고, 왜 그렇게 정했고, 무엇이 아직 확인되지 않았는지)

---

## 마지막 세션 (2026-09-18): M1 완료, M2 셋째 항목까지 완료

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). **아직 push하지 않았다.** main에는 합치지 않았다.
- **M2의 `SkiffCodeUri`, `SkiffCodeStore`, 인텐트와 여는 흐름까지 끝났다. 다음은 M2 넷째 항목
  `TextLoader`다.** M1은 실기기에서 실제 SSH 서버로 탐색과 전송까지 확인했다(아래 "실서버 확인 결과").
- 모듈은 셋이다: `:core`(라이브러리), `:app`(Skiff), `:code`(Skiff Code). 무엇이 어디 있는지는
  `AGENTS.md`의 "Three modules"에 있다. `:code`의 WebView 페이지는 아직 M0 스파이크다.
- 테스트: `:core` 35개, `:app` 24개, `:code` 49개(`SkiffCodeUriTest` 27, `OpenRequestTest` 12,
  `SkiffCodeStoreTest` 7, M0 ktoml 스파이크 3). 실패 없음.
- lint 경고: `:core` 9개, `:app` 4개, `:code` 12개. "새 라이브러리 버전" 경고는 그때그때 달라지니
  개수보다 종류를 비교한다. `:code`의 12개는 M0 스파이크 `MainActivity`와 `allowBackup`에서 10개,
  BouncyCastle jar의 `TrustAllX509TrustManager` 1개, `MANAGE_EXTERNAL_STORAGE`의 `ScopedStorage` 1개다.
  예전에 적혀 있던 `:core` 10개, `:app` 5개는 오래된 수치였다(변경 전 커밋을 다시 돌려 확인).

### 이번 세션에서 내린 판단
**`SkiffCodeUri`**
- 직접 파싱한다. `android.net.Uri`는 유닛 테스트 JVM에서 스텁이고, `java.net.URI`는 인코딩되지 않은
  한글 경로를 거부한다.
- user는 없어도 된다(alias만으로 서버를 가리킬 수 있다). 포트 기본 22, host 소문자, IPv6 괄호 제거.
  `+`는 공백으로 바꾸지 않는다. 깨진 퍼센트 인코딩, UTF-8이 아닌 바이트, NUL은 거부한다.

**`SkiffCodeStore`와 `jsonDataStore`**
- Skiff와 같은 모양이고 파일은 따로다(`skiffcode.json`). Keystore 키가 앱마다 따로라 암호문을 공유할 수 없다.
- **읽을 수 없는 저장 파일은 `<이름>.corrupt-<밀리초>`로 복사해 두고 빈 데이터로 시작한다. 두 앱을
  같이 고쳤다**(사용자 지시, `:core`의 `jsonDataStore`). 전에는 첫 쓰기가 원래 파일을 덮어써서
  프로필과 호스트키를 모두 잃었고, 모든 서버가 "바뀐 서버"가 아니라 "처음 보는 서버"로 보였다.
  현실적인 원인은 `AuthMethod` 하위 클래스 이름 변경이다. 실기기의 Skiff에서 기존 `skiff.json`이
  그대로 읽히는 것을 확인했다. "초기화했다"는 알림은 아직 없다.

**인텐트와 여는 흐름**
- **alias가 맞으면 그 프로필의 host를 쓴다.** 링크가 아는 alias를 다른 기계로 돌릴 수 없다. 대신
  경로는 링크의 것이라, 링크를 받은 사람이 자기 서버의 임의 경로를 열게 된다. 읽기만 하는 동안은
  문제가 아니지만 editor가 생기면 다시 볼 것.
- user 없는 링크는 (host, port)가 프로필 하나에만 맞을 때만 쓴다. host 비교는 대소문자를 무시한다.
- 알 수 없는 서버의 확인창은 주소와 경로를 보여 주고 비밀번호를 받는다. "서버로 저장"은 **기본 켬**
  이다(링크로 여는 서버는 다시 열 가능성이 높다고 봤다). 끄면 프로세스가 끝날 때까지 메모리에만 둔다.
  링크에 user가 있으면 확인창에서 바꿀 수 없다.
- 대화상자는 WebView가 아니라 네이티브 `AlertDialog`다. 비밀번호와 호스트키 결정이 원격 내용을
  렌더링하는 페이지를 지나가지 않게 하려는 것이다. 문구는 영어와 한국어 리소스로 뒀다.
- 흐름은 `stat`과 최근 파일 기록에서 끝나고 토스트를 띄운다. `TextLoader`와 viewer가 이어받는다.
- **파서의 거부 이유는 영어 그대로 대화상자에 나온다**(예: "a password in the link is not accepted").
  viewer에서 오류를 페이지에 보여 줄 때 현지화할 것.

**`SshClientFactory` 인증 (별도 커밋 `18347b5`)**
- 실기기 확인 중에 **틀린 비밀번호가 33초 뒤에야 실패하는 것**을 찾았다. sshj의 `authPassword`는
  `password`가 거부되면 같은 연결에서 `keyboard-interactive`를 다시 시도하는데, macOS sshd는 그
  두 번째 시도에 답하지 않는다. OpenSSH 클라이언트도 같은 순서면 멈추는 것을 `expect`로 확인했다.
  이제 `password`만 시도하고, 서버가 `password`를 아예 안 받을 때만 `keyboard-interactive`를 쓴다.
  Skiff도 같이 고쳐진다. 실기기에서 3초로 줄었다.

### M1에서 내린 판단
- **M1의 1번과 2번 항목은 같이 할 수밖에 없다.** `HostKeyGate`를 `:core`로 옮기려면 `SkiffStore` 의존을
  먼저 `KnownHostStore`로 끊어야 컴파일이 된다. 그래서 한 커밋에 넣었다.
- **`SftpTestServer`는 `:core`의 testFixtures로 내보냈다.** `SftpFileSystemTest` 19개 중 3개가
  `CopyEngine`(`:app`에 남는다)을 쓰고 있었다. 그 3개를 `:app`의 `SftpTransferTest`로 옮기고, 서버는
  fixture로 공유한다. 서버를 복사하면 두 사본이 달라진다.
- **`:core`는 okio, sshj, kotlinx-serialization을 `api`로 내보낸다.** 셋 다 `:core`의 시그니처에
  들어가 있어서, `:app`이 다시 선언하지 않아도 되게 했다. `:app`에 남긴 직접 의존은 BouncyCastle
  하나인데, `SkiffApplication`이 직접 provider를 등록하기 때문이다.
- **`SshClientFactory`는 접속·인증·keepalive만 갖는다.** 오류를 `FsError`로 옮기는 것은 `SshConnection`에
  남겼다. 재시도 판단이 거기 있기 때문이다.

### 실서버 확인 결과
사용자가 서버 프로필을 등록해 줘서 탭에서 직접 확인했다. **서버는 개발 맥 자신이라 양쪽을 셸에서
대조할 수 있었다.** 접속, 홈 목록, 하위 디렉터리 이동, 새로고침, 분할 화면, 양방향 전송이 모두
동작하고 MD5가 원본과 같았다. 자세한 것은 `plan.md`의 M1 마지막 항목에 적었다.

**전송 속도의 비대칭을 오해하지 말 것.** 29.3MB에서 내려받기 7.8 MB/s, 올리기 0.9 MB/s다.
`SftpFileSystem.openRead`에만 read-ahead가 있고(`READ_AHEAD_MAX = 16`) 쓰기 쪽은 파이프라이닝이
없어서 그렇다. **버그가 아니고 M1이 만든 것도 아니다.** 올리기가 느린 게 문제가 되면 쓰기 쪽
파이프라이닝은 별개 작업으로 잡는다.

기기에 남아 있던 프로필 `testnas`는 문서용 예약 주소(`192.0.2.0/24`)를 가리켜 응답하지 않는다.
15초 뒤 `FsError.Unreachable`로 끝나는 것까지 확인했다 — 오류 경로도 정상이다.

### 원격 실행 방식을 다시 따져봤다 (코드 변경 없음)
사용자가 "SSH exec가 나은지 데몬이 나은지" 다시 보자고 해서 분석했다. **결론은 exec 유지로 그대로**
이지만 **적혀 있던 근거가 약해서 고쳤다.** "아키텍처별 빌드 부담"은 컴파일된 바이너리 데몬만
반박하고, `sh`나 `python3`로 도는 스크립트 에이전트는 그 비용이 없다. 실제로 성립하는 근거 세 개와
데몬을 다시 꺼내는 조건(M6에서 무엇을 잴지 포함)은 `plan.md`의 "정한 것" 2번과 "범위 밖"에 있다.

덤으로 `GitService` 설계에 `git cat-file --batch`를 길게 연 채널로 두는 안을 넣었다. exec의 약점인
명령당 왕복을 줄이는 자리고, 프레이밍이 `LspProcess`와 같은 모양이다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 특히 Toolchain constraints, Before committing, 보고와 알림 규칙을 본다.
2. `plan.md`의 첫 `- [ ]`인 **M2 넷째 항목(`TextLoader`)**부터 시작한다. `ui/OpenFlow`의 `Opened`가
   읽을 파일을 넘겨준다. 원격은 `RemoteSessions.get(profile)`의 `SftpFileSystem`으로 읽는다.

### 사용자와 정한 것, 그리고 이유
다시 논의하지 말고 이대로 진행한다.

| 결정 | 고른 것 | 이유 |
|---|---|---|
| 레포 구조 | 같은 레포, `:core` / `:app` / `:code` 멀티모듈 | 별도 레포는 SFTP 계층을 복사하게 되고, 두 사본이 따로 바뀌면서 달라진다 |
| 원격 실행 | 데몬 없이 SSH exec (프로젝트 모드에서만) | 2026-09-18에 다시 따져보고 근거를 고쳤다. 데몬은 exec 없이 뜨지도 못하고(SFTP는 실행을 못 한다), 남에게 배포하는 앱이 남의 서버에 상주 프로세스를 심는 것은 "서버에 아무것도 설치하지 않는다"는 근본 제약과 충돌한다. 셋째로 임의의 서버가 대상이라 단일 산출물이 없다. 자세한 것과 데몬을 다시 꺼내는 조건은 `plan.md`의 "정한 것" 2번과 "범위 밖"에 있다 |
| UI | 전부 WebView 하나 (TS + CodeMirror 6) | Compose와 WebView를 섞으면 테마를 두 곳에 적용해야 하고, 스크롤에 따른 메뉴 숨김도 브리지를 거친다 |
| 서버 정보 공유 | Skiff가 서명 보호 Provider로 프로필만 공유, 비밀번호는 각자 저장 | Keystore 키는 앱마다 따로라 암호문을 공유할 수 없고, 비밀번호를 넘기면 평문이 IPC를 지나간다 |
| 실기기 | 갤럭시탭 S10 FE(SM-X526N, Android 16/API 36, WebView 152) | 연결된 기기가 이것이다. **폴더블 폰도 지원 대상**이라 폴더블 관련 문서와 코드는 지우지 않는다 |
| 예전 시도 | 로컬 브랜치 `claude/next-steps-2afbda`와 기기의 `com.naki.skiffcode`를 삭제 | 사용자 지시. 없었던 것으로 친다. 참고하거나 되살리지 않는다 |
| CM6 버그 처리 | **upstream에 올리지 않는다.** M5 diff 레이어에서 `patch-package`로 두 줄을 패치한다 | CodeMirror는 AI가 쓴 코드를 받지 않고 이슈는 자체 트래커에서만 받는다. 사용자는 리포트를 올리지 않기로 했다 |
| TOML | ktoml을 쓴다. `smol-toml`로 옮기지 않는다 | M0에서 확인했다. 컴파일러 플러그인만 필요하고 KSP를 안 써서 Room과 다르다 |
| exec | `:app`은 금지, `:code`는 허용. 근거는 "서버에 설치하지 않기 위해"가 아니라 **"셸 없는 `internal-sftp` 계정을 지원하기 위해"** 다 | 사용자가 금지의 출처를 물었고, 사용자가 정한 것이 아니라 AI가 잘못 유도한 것으로 드러났다. 규칙은 남기고 이유를 고쳤다 |
| M0 스파이크 코드 | 확인이 끝나면 지운다. exec 하네스도 지웠다 | 사용자 지시. M0은 버리는 코드로 확인만 하는 단계다 |
| 로컬 파일 경로 | `skiffcode:///경로`(MANAGE_EXTERNAL_STORAGE)와 `content://`(ACTION_VIEW/EDIT) 둘 다 받는다 | 2026-09-18 사용자가 가정대로 진행하라고 했다. `plan.md` "정한 것" 5번 |
| 보고 방식 | 작업마다 한국어로 보고하고 푸시 알림을 보낸다 | 사용자가 자리를 비울 때가 많다. `AGENTS.md`에 적었다 |

### 사용자 확인을 아직 받지 않은 가정
해당 단계에 들어가기 전에 한 번 물어본다.
1. diff와 git 거터의 기본 비교 대상은 **HEAD와 현재 버퍼**다. "직전 커밋과 HEAD"는 두 번째 옵션이다. (M5 전)
2. 원격 LSP 서버는 자동 설치하지 않고 PATH에서 찾는다. (M6 전)

### 실제 LSP 서버 확인 (exec 규칙을 고치고 추가로 한 것)

**사용자가 exec 금지의 근거를 물었고, 문서가 틀렸다는 것이 드러났다.** "서버에 아무것도 설치하지
않는다"는 사용자가 정한 제약이지만, "그러므로 exec를 두지 않는다"는 AI가 유도한 것이고 논리가
맞지 않는다(서버에 이미 있는 `git`을 exec로 돌리는 것은 아무것도 설치하지 않는다). 실제로 그 규칙이
지키는 것은 **셸 없는 `internal-sftp` 계정 지원**이다. `AGENTS.md`를 그렇게 고쳤고, `:code`는
exec를 쓸 수 있다고 명시했다. `:app`은 여전히 금지다.

사용자가 스파이크에서 exec를 허용해서 실제 서버로 확인했다. **하네스는 사용자 지시로 지웠다.**
M6에서 다시 만들 때 필요한 것은 이것뿐이다:
- 서버 쪽: MINA `SshServer`에 `commandFactory = ProcessShellCommandFactory.INSTANCE`.
  `SftpTestServer`(이제 `:core`의 testFixtures)와 같은 구성이고 subsystem 대신 commandFactory를 둔다.
- 클라이언트 쪽: sshj `session.exec("/bin/sh -lc 'cd <root> && exec <command>'")`.
- 프레이밍: 헤더는 `\r\n\r\n`까지 한 바이트씩, 본문은 `Content-Length`만큼 채워 읽는다.
- 언어 서버: `npm i pyright` → `node_modules/.bin/pyright-langserver --stdio`. 레포에 넣지 않았다.
- **`org.json`은 유닛 테스트 JVM에서 스텁이라 전부 던진다.** `org.json:json`을 테스트 의존성으로
  넣어야 한다. Gradle의 `-D`는 테스트 JVM에 전달되지 않으니 `systemProperty`로 넘겨야 한다.
- 결과: initialize 98ms, 진단까지 346ms(로컬 루프백). `positionEncoding`은 응답에 없고(= `utf-16`),
  `textDocumentSync`는 2(incremental). `값 = "한글" + 1`의 진단이 char 4..12로 UTF-16 인덱스와
  정확히 맞았다(UTF-8이면 4..18이다).

### 아직 확인하지 않은 것
안 되는 게 나오면 `plan.md`의 설계를 먼저 고친다.
- **여는 흐름에서 실기기로 확인한 것:** 비밀번호가 든 링크 거부, 알 수 없는 서버 확인창, 첫 연결
  호스트키 창(지문을 맥의 `ssh-keygen -lf`와 대조), 틀린 비밀번호 재시도 창, 저장된 프로필을 alias로
  찾기, `onNewIntent`, 로컬 링크의 파일 접근 안내와 설정에서 돌아온 뒤 이어지기, 최근 파일 기록.
- **확인하지 못한 것:** 맞는 비밀번호로 원격 파일까지 여는 성공 경로(사용자 비밀번호가 필요하다),
  **바뀐 호스트키 경고 창**, `content://`(M2 마지막 확인 항목에서 파일 매니저로), Android 17의
  로컬 네트워크 권한 요청(탭은 Android 16이라 요청하지 않는다). 탭의 Skiff Code에는 `dev-mac`
  프로필이 틀린 비밀번호로 저장돼 있다. 성공 경로를 볼 때 비밀번호 창에 맞는 것을 넣으면 갱신된다.
- **탭에서 실제 SSH 서버로 LSP를 띄워 본 적은 아직 없다.** M1에서 SFTP 탐색과 전송은 실서버로 확인했지만, 위 LSP 확인은 이 맥 안의 MINA 루프백이라 네트워크 지연, 재접속, 끊김이 빠져 있다. exec로 원격 LSP를 띄우는 것은 M6이 처음이다.
- 진단이 수백~수천 개일 때의 비용. 스텁은 50개, pyright는 1개였다.
- 마크다운 렌더링(`markdown-it`), lezer 심볼 추출, 테마 CSS 변수, SAF import/export는 라이브러리 버전도 고르지 않았다.
- 큰 파일에서 편집 중 동기화 비용. 2MB에서 incremental이 훨씬 싸다는 것만 알고, 실제 서버가 어디서 버거워하는지는 모른다.
- pyright 말고 다른 서버(typescript-language-server, marksman)는 확인하지 않았다. TypeScript 7은 네이티브 재작성이라 `tsserver.js`가 없고, `typescript-language-server`가 그 위에서 도는지 모른다.

### 주의할 점
- **세션을 끝낼 때 `plan.md` 체크리스트를 갱신하고 이 파일을 덮어쓴다.**
- **exec 규칙은 이미 고쳤다.** `:app`은 여전히 금지, `:code`는 허용이다. M5의 체크리스트에서 `AGENTS.md` 수정은 빠졌고 `RemoteExec` 구현만 남았다. 자세한 것은 위 "실제 LSP 서버 확인" 절과 `AGENTS.md`의 "The SFTP side, and what it must not do"에 있다.
- `:code`에 kotlinx.serialization 플러그인이 붙어 있는데 main 소스에서는 아직 안 쓴다. ktoml이 요구한다. M2에서 `SkiffCodeStore`가 쓴다.
- `npm install` 때 fsevents install 스크립트는 npm 11 기본 정책으로 실행되지 않는다. macOS용 선택 의존성이라 영향이 없다.
- 기기:
  - 시리얼은 `adb devices`로 얻고 레포에 적지 않는다.
  - 화면은 2분 뒤 꺼지고, 꺼진 화면에서 `screencap`은 검은 화면을 찍는다. 측정 전에 `adb shell input keyevent KEYCODE_WAKEUP`을 보내고 `dumpsys window | grep mCurrentFocus`로 앱이 앞에 있는지 확인한다.
  - 가로 방향(2304x1440)이고 화면은 하나라 `screencap`에 display id가 필요 없다.
  - 아래 가장자리에서 시작하는 스와이프는 시스템 제스처에 먹히므로 y 250~1100 사이에서 한다.
  - adb로는 멀티터치를 만들 수 없으니, 핀치 같은 제스처는 사용자에게 손으로 확인을 부탁한다.
- 환경:
  - `node`(v24)와 `npm`은 PATH에 있다.
  - `java`는 PATH에 없어서 `JAVA_HOME=/opt/homebrew/opt/openjdk@17`이 필요하다.
  - `adb`도 PATH에 없고 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`에 있다.
  - worktree에는 gitignore된 `local.properties`가 따로 있어야 한다(메인 체크아웃에서 복사했다).
