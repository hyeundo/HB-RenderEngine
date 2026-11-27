# HB3D Implementation Plan v1

## 현재 상태

작업 디렉터리는 비어 있다. 따라서 기존 코드에 붙이는 작업이 아니라 새 모드 뼈대를 세우는 흐름으로 진행해야 한다.

## 우선 구현 순서

### 1. 프로젝트 뼈대 생성

- Fabric 1.21.4 모드 기본 구조 생성
- Gradle 설정
- `src/main/java`
- `src/main/resources`

### 2. API 층 생성

패키지:

- `api`

핵심 타입:

- `Hb3dVisualApi`
- `Hb3dAnimationListener`
- `SocketTransform`

목표:

- 외부에서 사용할 공개 표면 먼저 고정

### 3. 자산 층 생성

패키지:

- `asset`

핵심 타입:

- `ModelDefinition`
- `ModelDefinitionLoader`
- `ModelRegistry`
- `RuntimeModelAsset`
- `ModelAssetCache`

목표:

- JSON 정의와 GLB 자산 구조를 먼저 고정

### 4. 런타임 상태 층 생성

패키지:

- `runtime`

핵심 타입:

- `VisualInstance`
- `VisualInstanceManager`
- `VisualController`
- `SocketPoseCache`

목표:

- 엔티티별 상태 저장 구조 완성

### 5. 애니메이션 층 생성

패키지:

- `animation`

핵심 타입:

- `AnimationPlayer`
- `AnimationStateResolver`
- `AnimationTransitionPolicy`
- `AnimationEventTracker`

목표:

- 상태 입력을 클립 재생으로 연결

### 6. 렌더 층 생성

패키지:

- `render`

핵심 타입:

- `Hb3dModelRenderer`
- `SkeletonPoseCalculator`
- `SocketResolver`

목표:

- 본 계산 및 렌더링 연결

### 7. 동기화 층 생성

패키지:

- `network`

핵심 타입:

- `VisualStateSyncPayload`
- `VisualStateSyncHandler`

목표:

- 서버 상태를 클라이언트 비주얼 상태로 반영

## 첫 구현 마일스톤

### M1

- `ModelDefinition`
- `ModelDefinitionLoader`
- `ModelRegistry`
- 모델 정의 JSON 1개 샘플

완료 기준:

- JSON 정의를 읽고 검증 가능

### M2

- `VisualInstance`
- `VisualInstanceManager`
- `Hb3dVisualApi`
- `VisualController`

완료 기준:

- 엔티티에 모델 바인딩 및 상태 저장 가능

### M3

- `AnimationStateResolver`
- `AnimationPlayer`
- `AnimationEventTracker`

완료 기준:

- `idle -> attack_primary -> idle` 흐름 재생 가능

### M4

- `SkeletonPoseCalculator`
- `SocketResolver`

완료 기준:

- `right_hand`, `head`, `weapon` 소켓 조회 가능

### M5

- `Hb3dModelRenderer`
- GLB 자산 연결

완료 기준:

- 엔티티에 GLB 외형 렌더 가능

### M6

- `VisualStateSyncPayload`
- `VisualStateSyncHandler`

완료 기준:

- 외부 상태 동기화 입력 반영 가능

## 첫 테스트 시나리오

1. `goblin` 모델 바인딩
2. `idle` 재생 확인
3. `walk` 전환 확인
4. `attack_primary` 재생 확인
5. `attack_primary`의 `marker:hit` 이벤트 수신 확인
6. `death` 전환 및 마지막 프레임 유지 확인
7. `right_hand` 소켓 좌표 조회 확인

## 지금 바로 해야 할 일

다음 작업은 새 Fabric 모드 뼈대를 만들고, API와 자산 정의 구조부터 생성하는 것이다.
