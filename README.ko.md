# HB Render Engine (HB3D) — 마인크래프트 고품질 3D 모델 및 애니메이션 렌더링 엔진

Minecraft Java Edition 1.21.4 Fabric용 MIT 라이선스 오픈소스 3D 렌더링 엔진이다. Blender에서 내보낸 GLB(glTF 2.0 바이너리) 모델을 모드 JAR에 포함해 커스텀 캐릭터, 애니메이션 보스, 3D 월드 배경을 구현하는 개발자를 대상으로 한다.

**HB3D의 핵심 강점은 고품질 RPG 캐릭터와 보스의 인게임 렌더링 및 애니메이션이다.** 실제 개발 테스트에서 복잡한 캐릭터 메시, 정교한 날개 텍스처, 세밀한 의상과 장식물을 표현했으며, 개발자가 안정적인 모델 렌더링과 스켈레탈 애니메이션 구동을 확인했다. 기존 마인크래프트의 블록형 모델 표현을 넘어 고품질 3D RPG 비주얼을 구현할 수 있다.

모델 로딩, 스켈레탈 애니메이션, GPU 스키닝, CPU 폴백, 소켓 조회, 애니메이션 이벤트, 서버 동기화 및 월드 배경 기능을 제공한다. Iris 호환 코드가 포함되어 있으나 모든 셰이더팩의 호환성을 보장하지 않는다.

현재 실험적 버전(v0.1.0)이며 캐릭터 및 배경 모델은 포함하지 않는다.

## 인게임 시연 영상

### 고품질 렌더링 및 애니메이션 시연

[렌더링 및 애니메이션 시연 영상 보기](https://www.youtube.com/watch?v=4qZ8JpoBcFA)

Complementary Shaders를 활성화한 실제 마인크래프트 환경에서 정교한 GLB 캐릭터 모델의 렌더링 품질과 자연스러운 스켈레탈 애니메이션을 확인하기 위한 시연 영상이다.

### 레이드 플레이 시연

[기존 레이드 데모 영상 보기](https://www.youtube.com/watch?v=dkFnCKsmeX0)

커스텀 레이드 콘텐츠에 엔진을 적용한 초기 개발 시연이다. 시연에 사용된 제3자 게임 에셋은 공개 소스 및 배포 빌드에 포함되지 않는다.

## 셰이더 호환성

HB3D는 Iris 1.8.8 및 Sodium 0.6.9 환경에서 Complementary Unbound Shaders를 주 개발 기준으로 삼았다. 초기 개발 시연 영상에서도 Complementary Shaders를 활성화한 상태로 GLB 모델 렌더링 및 애니메이션을 테스트했다. 공개용 프리뷰의 게임 실행 및 월드 진입은 해당 셰이더 환경에서 검증했다. 초기 테스트에는 GLB 에셋이 포함되지 않았으나, 이후 공개 빌드의 인게임 테스트에서 GLB 모델이 정상적으로 렌더링되는 것을 확인했다. Complementary Reimagined 및 기타 셰이더팩은 별도로 검증하지 않았으며, 모든 버전과 설정에서의 렌더링 호환성을 보장하지 않는다.

## 빌드

Java 21 환경에서 Windows는 `gradlew.bat build`, Linux/macOS는 `./gradlew build`를 실행한다. 결과물은 `build/libs/`에 생성된다.

Paper 연동 모듈은 루트에서 Gradle의 `-p paper-integration build` 옵션으로 별도 빌드한다.

## 개발자 연동

배포 권한이 있는 GLB 모델을 `src/main/resources/assets/<namespace>/models/`에, 모델 정의 JSON을 `src/main/resources/data/hb3d/hb3d_models/`에 넣고 빌드한다. GLB는 모드 JAR의 클래스패스에서 읽으며 일반 리소스팩을 통한 GLB 로딩은 지원하지 않는다. `Hb3dMod.getVisualApi()`를 통해 모델 바인딩, 상태 변경, 소켓 조회 및 이벤트 리스너 등록이 가능하다.

## Blender 3D 모델 적용 방법

1. Blender에서 모델과 필요한 애니메이션을 GLB로 내보낸다.
2. `src/main/resources/assets/example/models/entity/character.glb`에 저장한다.
3. [모델 정의 예제](examples/model-definition.json)를 `src/main/resources/data/hb3d/hb3d_models/example_character.json`에 복사하고 실제 애니메이션 및 노드 이름에 맞게 수정한다.
4. Java 21로 모드를 빌드한 후 Fabric 환경에서 Java API를 통해 엔티티에 모델을 연결한다.

## 자주 묻는 질문

- 일반 리소스팩이나 FBX/OBJ를 바로 불러올 수 있나? 현재 파이프라인은 모드 JAR에 포함된 GLB를 사용한다. 다른 형식은 GLB로 변환해야 한다.
- Paper 서버에서도 사용할 수 있나? 서버 동기화 프로토콜과 월드 배경 플러그인을 제공하며, 클라이언트에는 Fabric 모드와 동일한 에셋이 필요하다. 보스 AI 및 전투 로직은 별도 구현해야 한다.
- 정식 안정 버전인가? 현재 실험적 공개 프리뷰다. 빌드 및 일부 테스트를 통과했으며 공개 빌드의 인게임 GLB 렌더링도 검증했다. 서버 런타임 검증은 남아 있다.

상세한 요구사항과 기술 문서는 [영문 README](README.md), 검증 범위는 [릴리스 검증 기록](docs/RELEASE_VALIDATION.md)을 참고한다.

## AI 협업 개발

HB가 ChatGPT 및 OpenAI Codex와 협업하여 약 1~2개월 동안 개발한 독립 프로젝트다. OpenAI 또는 Minecraft의 공식 프로젝트는 아니다.

## 라이선스

[MIT 라이선스](LICENSE)로 배포한다.