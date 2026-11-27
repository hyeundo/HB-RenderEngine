# HB3D Runtime Spec v1

## 목적

이 문서는 Fabric 1.21.4용 GLB 기반 몬스터 비주얼 런타임 모드의 v1 명세다.

이 모드는 다음만 담당한다.

- 엔티티에 GLB 3D 모델 바인딩
- 상태에 따라 애니메이션 재생
- 본/소켓 위치 제공
- 애니메이션 marker 이벤트 송신

이 모드는 다음을 담당하지 않는다.

- AI
- 전투 규칙
- 데미지 계산
- 실제 피격 판정
- 스폰 규칙

## 핵심 원칙

- 외부 모드는 무엇이 일어났는지만 보낸다.
- 런타임 모드는 그것을 어떻게 보일지만 담당한다.
- 모델 데이터는 공유한다.
- 엔티티별 재생 상태는 분리한다.
- 외부에는 소켓 이름만 공개하고 실제 본 이름은 숨긴다.
- 상태명과 클립명은 분리한다.

## MVP 범위

포함:

- 모델 바인딩
- 상태 기반 애니메이션 전환
- GLB 1개 이상 로드
- 소켓 조회
- marker 이벤트 송신

MVP 상태:

- `idle`
- `walk`
- `run`
- `attack_primary`
- `hit`
- `death`

MVP 소켓:

- `root`
- `head`
- `right_hand`
- `weapon`

## 상태 구조

상태는 2층 구조로 고정한다.

### Base State

- `idle`
- `walk`
- `run`

Base State는 루프 재생한다.

### Action State

- `none`
- `attack_primary`
- `hit`
- `death`

Action State는 원샷 재생한다.

### 우선순위

1. `death`
2. `hit`
3. `attack_primary`
4. base state

### 재진입 규칙

- `death`는 항상 즉시 전환
- `hit`는 현재 액션 인터럽트 가능
- `attack_primary`는 동일 요청이 다시 오면 재시작 가능
- 액션 종료 후 base state로 복귀

## 상태명과 클립명

외부 모드는 상태명만 사용한다.

예:

- `attack_primary`
- `hit`
- `death`

실제 GLB 클립명은 모델 정의 파일에서 매핑한다.

예:

- `attack_primary -> attack_1`
- `hit -> hit`
- `death -> death`

## 소켓 명세

외부 공개 표준 소켓명:

- `root`
- `head`
- `chest`
- `right_hand`
- `left_hand`
- `weapon`
- `mouth`
- `projectile_origin`

MVP 필수 소켓:

- `root`
- `head`
- `right_hand`
- `weapon`

## 이벤트 명세

이벤트 타입:

- `animation_started`
- `marker`
- `animation_finished`

`marker`가 핵심이다.

예:

- `attack_1` 0.42초 시점 `hit`

규칙:

- 같은 재생 사이클에서 같은 marker는 한 번만 발행
- 재시작 시 marker 발행 기록 초기화

## 외부 API

고수준 API:

- `bindModel(entityId, modelId)`
- `clearModel(entityId)`
- `setBaseState(entityId, stateName)`
- `setActionState(entityId, stateName, actionSequence)`
- `clearActionState(entityId)`
- `getSocketTransform(entityId, socketName)`

저수준 API:

- `playClip(entityId, clipName, loop, transitionTicks)`
- `stopClip(entityId)`
- `getCurrentClip(entityId)`
- `isClipPlaying(entityId, clipName)`

이벤트 API:

- `registerAnimationListener(listener)`
- `unregisterAnimationListener(listener)`

## 동기화 필드

서버에서 클라이언트로 전달하는 최소 필드:

- `entityId`
- `modelId`
- `baseState`
- `actionState`
- `actionSequence`

권장 추가 필드:

- `yaw`
- `pitch`
- `locomotionSpeed`

## 모델 정의 JSON

```json
{
  "modelId": "goblin",
  "glb": "hb3d:models/entity/goblin.glb",
  "states": {
    "base": {
      "idle": "idle",
      "walk": "walk",
      "run": "run"
    },
    "action": {
      "attack_primary": "attack_1",
      "hit": "hit",
      "death": "death"
    }
  },
  "sockets": {
    "root": "Root",
    "head": "Head",
    "right_hand": "RightHandSocket",
    "weapon": "WeaponSocket"
  },
  "events": {
    "attack_1": [
      { "time": 0.42, "name": "hit" }
    ]
  },
  "playback": {
    "loop": ["idle", "walk", "run"],
    "holdLastFrame": ["death"]
  },
  "transitions": {
    "defaultTicks": 4,
    "actionTicks": 2,
    "deathTicks": 1
  }
}
```

## 로딩/캐시

- 모델 정의는 리소스 로드 시 읽는다.
- GLB는 first-use lazy load를 기본으로 한다.
- 로드한 자산은 `modelId` 기준으로 캐시한다.
- 동일 모델 자산은 여러 엔티티가 공유한다.

## 엔티티 런타임 상태

엔티티마다 별도 Visual 상태를 가진다.

필수 필드:

- `entityId`
- `modelId`
- `baseState`
- `actionState`
- `actionSequence`
- `resolvedBaseClip`
- `resolvedActionClip`
- `activeClip`
- `previousClip`
- `clipTimeSeconds`
- `blendTicksRemaining`
- `firedMarkers`
- `socketPoseCache`

## v1 성공 기준

- 엔티티에 `modelId`를 바인딩할 수 있다.
- `idle`, `walk`, `run`, `attack_primary`, `hit`, `death` 상태 전환이 된다.
- GLB 애니메이션이 재생된다.
- `right_hand`, `head`, `weapon` 소켓 조회가 가능하다.
- `attack_primary`에서 `marker:hit` 이벤트가 발생한다.
- 같은 액션이 연속 재생될 때 `actionSequence`로 구분된다.
