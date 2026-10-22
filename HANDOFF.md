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

## 마지막 세션 (2026-10-22)

### 지금 상태
- **코드는 바꾸지 않았다. 계획만 세웠다:** M13(커뮤니티 테마 45개와 팔레트의 `Theme...` 목록)과 M14(테마 변환 스킬
  `/theme-convert`). 설계는 새 주제 파일 `docs/skiffcode.spec/community-themes.md`, 할 일은 `skiffcode.plan.md`의 M13·M14다.
  목차, `settings-theme.md`, `palette.md`에 그리로 가는 한 줄씩을 넣었다.
- `docs/my.next.spec.md`의 "커뮤니티 테마들 .toml 제공" 절은 사용자가 쓴 것이고, 이 세션은 "정한 것" 한 줄만 더했다.
- 이번 세션의 사용자 결정은 모두 spec과 계획에 적었다: 모든 변형(대비 단계 포함), ocean은 폴드8의 `ocean copy`, 목록은 팔레트 안,
  `system`의 테마 고르기는 안 함, 같은 이름이면 사용자 테마가 이김, 스킬은 레포 `.claude/skills/`·명령으로만·결과는 레포
  `user-themes/<이름>.toml`을 만드는 것까지(건네기·커밋 없음).
- 15개 저장소의 라이선스는 GitHub API로 봤다(Tokyo Night만 Apache-2.0, Gruvbox는 API가 못 찾지만 README·`package.json`이 MIT).
  **변형 이름은 저장소의 파일 목록만 보고 적었다** — 원본 파일은 아직 열지 않았으니 구현 때 맞춘다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. `skiffcode.plan.md`의 첫 `- [ ]`, M13의 "번들 목록과 이름 (Kotlin)"부터 한다. 읽을 spec은 M13 머리의 줄이다.
3. Ocean 항목은 폴드8에서 `run-as`로 파일을 꺼내야 하니 그때 사용자에게 묻는다. 폴드8 연결은 `~/.claude/skills/fold8-install` 스킬.
4. 푸시는 사용자에게 묻는다(커밋도 푸시도 사용자가 말할 때만).
