# SDK Maven Central 배포 준비 및 실행

이 문서는 현재 6개 SDK 모듈을 외부 Maven Central에 배포하는 절차다. 로컬 준비 성공, Portal 업로드, 공개 배포, 외부 다운로드 검증을 각각 구분한다.

## 배포 대상

그룹은 `io.github.queryecho`, 버전은 `-PVERSION_NAME=...`로 지정한다.

- queryecho-core
- queryecho-jdbc
- queryecho-http-transport
- queryecho-java-sdk
- queryecho-spring-boot-3-starter
- queryecho-spring-boot-4-starter

앱·Collector·Dashboard는 SDK Maven 배포 대상이 아니다. 사용자는 Java SDK 또는 해당 Spring Starter 하나만 직접 추가하고 공통 모듈은 전이 의존성으로 내려받는다.

## 기존 자격증명 재사용

공개 저장소 조회에서 기존 `queryecho-sdk:0.1.2`를 확인했다. 새 Java SDK·Boot 3/4 Starter의 metadata는 404였다. 배포 버전은 예시를 그대로 사용하지 말고 6개 모듈 전체의 기존 공개 상태를 확인한 뒤 확정한다.

Central Portal에서 `io.github.queryecho` namespace 사용 권한을 확인한다. 과거 배포 계정의 유효한 User Token과 GPG 개인키가 있으면 재사용한다. 새 모듈을 추가했다고 계정이나 토큰을 다시 만들 필요는 없다.

기존 GitHub Actions는 `release` environment를 사용한다. 해당 environment 또는 접근 가능한 repository secrets에 다음 이름을 설정한다. 값은 문서나 Git에 넣지 않는다.

| Secret | 값 |
|---|---|
| MAVEN_CENTRAL_USERNAME | Central User Token의 username |
| MAVEN_CENTRAL_PASSWORD | 같은 User Token의 password |
| SIGNING_KEY_BASE64 또는 SIGNING_KEY | ASCII-armored GPG 개인키의 Base64 또는 원문 |
| SIGNING_PASSWORD | GPG 개인키 암호; 암호 없는 키라면 비어 있을 수 있음 |

로그인 비밀번호와 토큰 password, GPG 암호는 서로 다른 값이다. 공개키가 키 서버에 공개되어 있어야 하고, 개인키의 유효기간과 암호도 확인한다. Secret의 존재만으로 토큰 유효성을 증명할 수는 없다.

## 1. 공개하지 않고 준비하기

JDK 21을 준비한다. 아래 버전은 예시이며 배포 전 실제 기존 버전을 확인하고 사용하지 않은 버전을 선택한다.

```powershell
cd C:\springdb\QueryEcho
$env:GRADLE_USER_HOME = 'C:\springdb\.gradle'
.\gradlew.bat sdkPrepareRelease '-PVERSION_NAME=0.1.0-rc.1' --no-daemon
```

이 명령은 테스트, Java 17 바이트코드 및 공통 SDK 프레임워크 의존성 경계 검사, JAR·sources·Javadoc·POM·Gradle metadata 생성을 수행한다. Maven 업로드나 서명키가 필요하지 않다. 네트워크는 빌드 의존성 다운로드에 사용할 수 있다.

각 모듈의 `build/libs`와 `build/publications/maven`에서 산출물을 확인한다. SDK 모듈 간 POM 의존성이 동일한 배포 버전을 참조하는지, 라이선스·개발자·SCM 정보가 실제 프로젝트와 맞는지 검토한다. 이 단계는 서명이나 Portal의 서버 검증을 대신하지 않는다.

## 2. 로컬에서 Portal에 업로드할 경우

환경변수는 아래 이름으로 안전하게 주입한다. 개인키와 토큰은 채팅이나 로그에 출력하지 않는다.

```text
ORG_GRADLE_PROJECT_mavenCentralUsername
ORG_GRADLE_PROJECT_mavenCentralPassword
ORG_GRADLE_PROJECT_signingInMemoryKey
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
```

업로드를 결정한 뒤 아래 명령을 실행한다. 기존 셸 설정이 자동 공개를 활성화했을 가능성까지 차단하도록 수동 공개를 명시한다.

```powershell
.\gradlew.bat sdkPublishToMavenCentral '-PVERSION_NAME=0.1.0-rc.1' '-PsignAllPublications=true' '-PmavenCentralAutomaticPublishing=false' --no-daemon
```

Central Portal Deployments에서 모든 SDK 모듈의 검증 결과를 확인한 뒤 Publish한다. 업로드 실패·부분 성공 시 Portal 상태를 먼저 확인하고 무조건 재실행하지 않는다. Maven Central에 공개된 동일 좌표·버전은 덮어쓸 수 없다. 공개 테스트 버전도 불변이므로 내용을 바꾸면 새 버전을 쓴다.

## 3. 기존 GitHub Actions로 배포할 경우

현재 `.github/workflows/release.yml`은 `v숫자.숫자.숫자` 형태 태그 push로 시작한다. SDK 검증 이후 토큰과 서명키로 자동 공개한다. **같은 워크플로는 GHCR 서버 이미지도 게시한다.** SDK만 수동 업로드하려면 앞 절차를 사용한다.

준비만 하는 동안 태그를 push하지 않는다. 실제 배포 시에는 사용할 버전, 커밋, secrets를 확인하고 태그를 만든다. prerelease 태그는 현재 태그 패턴의 대상이 아니다.

## 4. 외부에서 다운로드 검증

Portal 업로드 성공만으로 종료하지 않고 공개 상태 및 저장소 반영을 확인한다. `external-sdk-test`에서는 아래처럼 실제 배포 버전을 지정한다.

```powershell
cd C:\springdb\external-sdk-test
$env:SDK_VERSION = '실제-공개된-버전'
Remove-Item Env:SDK_REPOSITORY_URL -ErrorAction SilentlyContinue
$env:QUERYECHO_COLLECTOR_URL = 'https://테스트-Collector-주소'
# QUERYECHO_INGEST_API_KEY를 별도로 주입
.\verify.ps1 -FreshCache
```

이 테스트는 Java SDK 설치와 수집을 확인한다. Spring Boot 3·4 Starter는 각각의 소비자 앱에서 추가 검증해야 한다. Maven 공개와 Collector 외부 접속 설정은 별도 작업이다. 조회 API 인증은 수집 API 키와 같다고 가정하지 않는다.

## 완료 기준

- SDK 준비 검사 통과 및 6개 모듈 산출물 생성
- 배포할 버전·namespace 권한·서명키·토큰 확인
- Central 검증 및 공개 완료
- 캐시 없는 외부 환경에서 Java SDK 다운로드·수집 검증
- Spring Boot 3·4 소비자 설치 및 기동 검증

참고: [사용 중인 Maven 배포 플러그인](https://vanniktech.github.io/gradle-maven-publish-plugin/central/), [Central Portal 토큰](https://central.sonatype.org/publish/generate-portal-token/)
