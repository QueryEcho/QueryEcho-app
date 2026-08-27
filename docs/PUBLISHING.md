# QueryEcho 배포 준비

## 1. Maven Central namespace

Central Portal에서 `io.github.queryecho` namespace를 등록하고 GitHub 조직 소유권을 검증합니다. 조직 namespace는 개인 계정 namespace처럼 자동 검증되지 않을 수 있습니다.

SDK의 공개 좌표는 다음과 같습니다.

```text
io.github.queryecho:queryecho-sdk:<version>
```

## 2. Central Portal과 GPG 준비

Central Portal user token과 ASCII-armored GPG private key를 발급합니다. GitHub 저장소의 `release` environment에 다음 Secret을 등록합니다.

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
SIGNING_KEY
SIGNING_PASSWORD
```

`SIGNING_KEY`에는 `-----BEGIN PGP PRIVATE KEY BLOCK-----`를 포함한 private key 전체를 저장합니다.

## 3. GHCR 권한

릴리스 워크플로는 GitHub가 제공하는 `GITHUB_TOKEN`과 `packages: write` 권한으로 다음 이미지를 게시합니다.

```text
ghcr.io/queryecho/queryecho-app
```

첫 게시 후 GitHub Packages 화면에서 이미지 공개 범위를 public으로 설정해야 익명 사용자가 로그인 없이 pull할 수 있습니다.

## 4. 로컬 검증

```bash
./gradlew test
./gradlew :queryecho-sdk:publishToMavenLocal -PVERSION_NAME=0.1.0-SNAPSHOT
docker build -t queryecho-app:local .
```

로컬 Maven 저장소에는 main jar, sources jar, javadoc jar, POM이 생성되어야 합니다. 실제 Maven Central 게시물은 GPG 서명이 추가됩니다.

## 5. 릴리스

```bash
git tag v0.1.0
git push origin v0.1.0
```

태그 버전에서 `v`를 제외한 `0.1.0`이 Maven 버전과 Docker 태그로 사용됩니다. Maven Central의 릴리스 버전은 수정하거나 덮어쓸 수 없으므로 같은 버전을 다시 게시하지 않습니다.

