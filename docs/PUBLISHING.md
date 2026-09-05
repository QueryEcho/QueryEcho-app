# QueryEcho 배포 준비

## 1. Maven Central namespace

Central Portal에서 `io.github.queryecho` namespace를 등록하고 GitHub 조직 소유권을 검증합니다. 조직 namespace는 개인 계정 namespace처럼 자동 검증되지 않을 수 있습니다.

사용자가 선택하는 SDK 진입 모듈의 배포 예정 좌표는 다음과 같습니다.

```text
io.github.queryecho:queryecho-java-sdk:<version>
io.github.queryecho:queryecho-spring-boot-3-starter:<version>
io.github.queryecho:queryecho-spring-boot-4-starter:<version>
```

하위 의존성인 `queryecho-core`, `queryecho-jdbc`, `queryecho-http-transport`까지
같은 버전으로 총 여섯 모듈을 배포합니다. 로컬 배포 검증과 실제 Central 공개는 별개입니다.

## 2. Central Portal과 GPG 준비

Central Portal user token과 ASCII-armored GPG private key를 발급합니다. GitHub 저장소의 `release` environment에 다음 Secret을 등록합니다.

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
SIGNING_KEY_BASE64
SIGNING_PASSWORD
```

`SIGNING_KEY_BASE64`에는 ASCII-armored private key 전체를 UTF-8 Base64로 변환한 값을 저장합니다. Base64를 사용하면 Windows PowerShell에서 여러 줄 private key를 GitHub Secret으로 옮길 때 발생할 수 있는 줄바꿈과 문자 인코딩 손상을 피할 수 있습니다.

PowerShell에서는 다음과 같이 등록합니다. `<GPG_KEY_ID>`는 `gpg --list-secret-keys --keyid-format=long`에서 확인한 private key ID로 바꿉니다.

```powershell
$QueryEchoGpgKeyId = "<GPG_KEY_ID>"
$QueryEchoSigningKey = (& gpg --batch --armor --export-secret-keys $QueryEchoGpgKeyId) -join "`n"

if ($LASTEXITCODE -ne 0 -or -not $QueryEchoSigningKey.Contains("-----BEGIN PGP PRIVATE KEY BLOCK-----")) {
    throw "GPG private key export failed."
}

$QueryEchoSigningKeyBase64 = [Convert]::ToBase64String(
    [Text.Encoding]::UTF8.GetBytes($QueryEchoSigningKey)
)

$QueryEchoSigningKeyBase64 | gh secret set SIGNING_KEY_BASE64 --env release --repo QueryEcho/QueryEcho-app
```

`SIGNING_PASSWORD`에는 GPG 키를 생성할 때 직접 정한 비밀번호를 저장합니다. Maven Central 웹사이트에서 발급되는 값이 아닙니다.

기존 `SIGNING_KEY`도 호환을 위해 사용할 수 있지만, 이 값에는 `-----BEGIN PGP PRIVATE KEY BLOCK-----`부터 `-----END PGP PRIVATE KEY BLOCK-----`까지 private key 전체가 들어가야 합니다. Release 워크플로는 게시 전에 키가 실제 PGP private key로 파싱되는지 검사합니다.

## 3. GHCR 권한

릴리스 워크플로는 GitHub가 제공하는 `GITHUB_TOKEN`과 `packages: write` 권한으로 다음 이미지를 게시합니다.

```text
ghcr.io/queryecho/queryecho-app
```

첫 게시 후 GitHub Packages 화면에서 이미지 공개 범위를 public으로 설정해야 익명 사용자가 로그인 없이 pull할 수 있습니다.

## 4. 로컬 검증

```bash
./gradlew test
./gradlew sdkPublishToMavenLocal -PVERSION_NAME=0.1.0-SNAPSHOT
docker build -t queryecho-app:local .
```

로컬 Maven 저장소에는 main jar, sources jar, javadoc jar, POM이 생성되어야 합니다. 실제 Maven Central 게시물은 GPG 서명이 추가됩니다.

## 5. 릴리스

```bash
git tag v0.1.0
git push origin v0.1.0
```

태그 버전에서 `v`를 제외한 `0.1.0`이 Maven 버전과 Docker 태그로 사용됩니다. Maven Central의 릴리스 버전은 수정하거나 덮어쓸 수 없으므로 같은 버전을 다시 게시하지 않습니다.
