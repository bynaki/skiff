# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일, 아직 확인하지 않은 것, 알려진 문제: [`docs/skiffcode.plan.md`](docs/skiffcode.plan.md)
  (끝난 마일스톤은 [`docs/skiffcode.plan.done.md`](docs/skiffcode.plan.done.md), 시작할 때 읽지 않는다)
- 설계와 사용자와 정한 것: [`docs/skiffcode.spec.md`](docs/skiffcode.spec.md)(목차). 주제별 파일은 `docs/skiffcode.spec/`에
  있고, 할 일 항목의 `읽을 spec:` 줄에 적힌 것만 읽는다.
- 기기와 환경: [`docs/devices.md`](docs/devices.md)
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 문서들에 없는 **직전 세션의 맥락**(무엇을 했고, 무엇이 남았는지)뿐이다.
  세션이 지나도 남는 것은 위 문서들의 제자리에 적는다.

---

## 마지막 세션 (2026-10-25)

### 지금 상태
- **M14 `/theme-convert`를 닫고 `plan.done.md`로 옮겼다.** 테마 저장소에서 연 세션이 Nord(VS Code 원본)와 Dawnfox(Neovim
  원본, 이름 `Light (Dawnfox)`)를 끝까지 돌렸고, 이 저장소의 `SKIFF_THEMES_DIR` 테스트로 두 파일을 읽었으며, 탭에서
  `Import Theme`으로 들여 견본을 찍었다. 사용자가 견본을 보고 괜찮다고 했다. 탭은 되돌렸다(받은 두 테마와 `Download/`의
  두 파일을 지우고 `settings.toml`을 md5로). 두 파일은 테마 저장소의 `user-themes/`에 남아 있다(커밋되지 않음).
- 탭 확인 중에 찾은 diff 레이어 버그를 고쳤다(`layers/pane.ts`의 `compareStep`, 자세한 것은 `plan.done.md`의 M14).
  새 빌드를 탭에 깔아 두 재현 순서로 확인했다. web 230, JVM 353, lint 경고 11(그대로).
- `code/build.gradle.kts`: 테스트 입력에 `user-themes/*.toml`을 더했다(앞 커밋 `fd35e84`).
- 이 맥의 셸에는 `java`가 PATH에 없다. Gradle은 `JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home`으로 돌렸다.
- 한 파일 모드의 라이선스 전문은 **파일 끝에 주석으로** 붙이는 것으로 사용자가 정했다(2026-10-25, spec에 적음).
- **M13도 닫아 옮겼다.** plan의 성공 조건 셋(탭, 견본 모음, 폴드8 커버)이 spec과 달리 ⬜로 남아 있던 것을, 확인 항목의 기록과
  사용자의 마무리 결정(2026-10-25)을 가리켜 ✅로 고쳤다(사용자 확인). 이제 plan의 "할 일"은 비어 있다.

### 다음 세션이 할 일
1. plan의 "할 일"이 비어 있다. 다음 마일스톤은 사용자에게 묻는다(원래 요구사항은 `docs/my.next.spec.md`, 남은 확인은 plan의
   "아직 확인하지 않은 것"). 커밋도 푸시도 사용자가 말할 때만 한다.
