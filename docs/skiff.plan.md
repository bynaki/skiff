# Skiff 할 일

배경과 설계는 [`skiff.spec.md`](skiff.spec.md)에 있다.

시작하기 전에 `AGENTS.md`를 먼저 읽을 것 — 거기 적힌 툴체인 제약들은 하나같이 실제로 빌드를
한 번씩 깨뜨린 것들이다.

---

## 1. 미리보기 뷰어 → Skiff Code로 넘어감

뷰어는 Skiff 안에 넣지 않고 독립 앱 **Skiff Code**로 만든다. 설계와 할 일은
[`skiffcode.spec.md`](skiffcode.spec.md)와 [`skiffcode.plan.md`](skiffcode.plan.md)에 있다.
여기 있던 읽기 규칙(크기 상한, NUL 바이트로 바이너리 판별, EUC-KR 폴백)은 그 설계의
`TextLoader`로 옮겼다. Skiff 쪽에 남는 일은 `onOpen`에서 Skiff Code로
인텐트를 보내는 것 하나다(Skiff Code M2).

---

## 2. 충돌 정책을 UI에서 고를 수 있게 한다 — 끝남 (2026-09-25)

전송은 이제 `ASK`로 시작하고, 이름이 겹치는 파일마다 `ConflictDialog`가 묻는다. 정한 것은
[`skiff.spec.md`](skiff.spec.md)의 2절에 있다. 탭에서 기기 안 복사·이동으로 세 선택지, "나머지에도
똑같이 적용", 전송 취소, 앱이 뒤에 있을 때의 알림 문구를 확인했다. **서버를 낀 전송으로는 아직 확인하지
않았다**(엔진은 같은 경로다).

이 일을 하다 알게 된 것, 아직 고치지 않은 것:

- **진행 중인 작업을 취소하면 대기 중인 작업이 멈춘다.** `TransferQueue.cancel`이 러너 코루틴을
  통째로 취소해서, 뒤에 `QUEUED`로 남은 작업은 다음 `enqueue`가 올 때까지 돌지 않는다.
- **파일과 폴더가 같은 이름으로 부딪히면 덮어쓰기가 실패한다.** 대상에 같은 이름의 폴더가 있는데
  파일을 덮어쓰거나, 같은 이름의 파일이 있는데 폴더를 합치려 하면 `openWrite`/쓰기가 실패하고 작업이
  "실패"로 끝난다. 둘 다 유지와 건너뛰기는 된다.

---

## 3. 쓰이지 않는 표면: 연결하든 지우든 결정할 것

존재하는데 아무도 안 쓰는 게 둘 있다. **각각 배선하거나 삭제해야 한다 — 지금처럼 두는 게 둘
중 어느 쪽보다도 나쁘다.**

- **`FileSystem.freeSpace`** — 호출자가 0개다. 원래 의도는 사전 점검이었다. 큰 전송이 90%에서
  실패하는 대신 시작하자마자 실패하도록. 참고로 `SftpFileSystem.freeSpace`는 의도적으로
  null을 반환한다(SFTP 기본 프로토콜에 해당 호출이 없다). 따라서 이 점검은 **로컬이 목적지일
  때만** 동작하는데, 그게 곧 흔한 다운로드 경우이므로 여전히 값어치가 있다.
  `CopyEngine.plan`에 넣고, 바이트가 움직이기 전에 `FsError.NoSpace`를 던지게 한다.
- **`androidx.window`** — 뺐다(2026-09-25, 사용자 결정). 분할선을 폴드 힌지에 스냅시키려던
  의존성이었는데 import한 곳이 없었다. 스냅을 다시 하게 되면 `ui/workspace/SplitContainer.kt`에서
  `WindowInfoTracker`/`FoldingFeature`로 하고, 그때 의존성을 다시 넣는다.

---

## 4. 자잘한 것들

- **`.kotlin/`이 gitignore에 없다.** 빌드 산출물이니 추가할 것.
- **원격 심링크 아이콘이 틀렸다.** `SftpFileSystem`은 모든 항목에
  `linkTargetIsDirectory = false`를 넣는다. 링크마다 해석하면 행당 왕복이 한 번씩 더 들기
  때문이다. 탭했을 때 제대로 들어가는 건 앞서 고쳤지만, 심링크된 디렉토리가 여전히 파일
  아이콘으로 보인다. 선택지: 행이 화면에 들어올 때 지연 해석하거나, 심링크를 별도로 표시하고
  타입을 암시하는 걸 그만두거나.
- **권한 편집이 없다.** `setPermissions`는 원래 설계에 있었는데 빠졌다. `FileNode`는 이미
  `mode`를 갖고 있고 속성 다이얼로그가 표시도 한다. 편집 가능하게 만드는 건 인터페이스에
  메서드 하나 추가 + `SFTPClient.chmod` / `Files.setPosixFilePermissions` 정도다.
- **원격 속성에 소유자·그룹이 없다.** SFTP는 uid/gid를 알려주는데 다이얼로그가 안 보여준다.
- **폴더블 커버 화면에서 툴바가 빠듯하다.** 아이콘 7개가 잘림 없이 들어가지만 제목 "Skiff" 오른쪽
  여백이 거의 없어서, 전송 배지(숫자)가 붙으면 모자랄 수 있다(2026-09-24에 봤다, 사용자가 확인은 생략하라고 했다).

---

## 검증 방법

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
```

- **뷰어**: `skiffcode.plan.md`의 단계별 "확인" 항목으로 옮겼다.
- **충돌 정책**: `CopyEngineTest`(묻기, 나머지에 적용, 폴더 합치기, 이동 중 건너뛴 파일 남기기)와
  `TransferQueueTest`(같은 파일시스템 이동에서 rename과 묻기가 섞이는 경우).
- **실기기**: 반대편에 이미 같은 이름이 있는 파일을 전송해서 세 선택지가 각각 제대로
  동작하는지 확인한다. 마크다운 파일, 소스 파일, 큰 로그를 양쪽 패널에서 열어본다.
  `adb logcat -s Skiff:V`를 켜둘 것 — **UI가 메시지 한 줄로 삼킨 실패는 전부 여기 찍히므로,
  조용하다는 게 곧 신호다.**
