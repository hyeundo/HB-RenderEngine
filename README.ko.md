# HB Render Engine (HB3D)

Minecraft Fabric 1.21.4용 GLB 기반 3D 비주얼 런타임이다.

모델 로딩, 스켈레탈 애니메이션, GPU 스키닝, CPU 폴백, 소켓 조회, 애니메이션 이벤트, 서버 동기화 및 월드 배경 기능을 제공한다. Iris 호환 코드가 포함되어 있으나 모든 셰이더팩의 호환성을 보장하지 않는다.

현재 실험적 버전(v0.1.0)이며 캐릭터 및 배경 모델은 포함하지 않는다.

## 셰이더 호환성

HB3D는 Iris 1.8.8 및 Sodium 0.6.9 환경에서 Complementary Unbound Shaders를 주 개발 기준으로 삼았다. 초기 개발 시연 영상에서도 Complementary Shaders를 활성화한 상태로 GLB 모델 렌더링 및 애니메이션을 테스트했다. 공개용 프리뷰의 게임 실행 및 월드 진입은 해당 셰이더 환경에서 검증했다. 단, 이 테스트에는 GLB 에셋이 포함되지 않았다. Complementary Reimagined 및 기타 셰이더팩은 별도로 검증하지 않았으며, 모든 버전과 설정에서의 렌더링 호환성을 보장하지 않는다.

## 빌드

Java 21 환경에서 Windows는 `gradlew.bat build`, Linux/macOS는 `./gradlew build`를 실행한다. 결과물은 `build/libs/`에 생성된다.

Paper 연동 모듈은 루트에서 Gradle의 `-p paper-integration build` 옵션으로 별도 빌드한다.

## 개발자 연동

배포 권한이 있는 GLB 모델을 `src/main/resources/assets/<namespace>/models/`에, 모델 정의 JSON을 `src/main/resources/data/hb3d/hb3d_models/`에 넣고 빌드한다. GLB는 모드 JAR의 클래스패스에서 읽으며 일반 리소스팩을 통한 GLB 로딩은 지원하지 않는다. `Hb3dMod.getVisualApi()`를 통해 모델 바인딩, 상태 변경, 소켓 조회 및 이벤트 리스너 등록이 가능하다.

상세한 요구사항과 기술 문서는 [영문 README](README.md)를 참고한다.

## AI 협업 개발

HB가 ChatGPT 및 OpenAI Codex와 협업하여 약 1~2개월 동안 개발한 독립 프로젝트다. OpenAI 또는 Minecraft의 공식 프로젝트는 아니다.

## 라이선스

[MIT 라이선스](LICENSE)로 배포한다.