# syntax=docker/dockerfile:1

# ── 1단계: 빌드. JDK 와 Gradle 이 필요하지만 최종 이미지에는 들어가지 않는다 ──
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 변경이 드문 빌드 설정을 먼저 복사한다 (소스만 바뀌면 이 레이어는 캐시 재사용)
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# Windows 에서 만든 파일의 줄바꿈(CRLF) 때문에 gradlew 가 리눅스에서 깨지는 것을 방지
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src ./src

# CI 가 이미지 태그를 넘기면 /actuator/info 의 build.version 이 된다
ARG APP_VERSION=0.0.1-SNAPSHOT
ENV APP_VERSION=${APP_VERSION}

# 테스트는 워크플로의 별도 단계에서 이미 돌렸으므로 이미지 빌드에서는 생략
RUN ./gradlew --no-daemon bootJar -x test


# ── 2단계: 실행. JRE 만 있는 작은 이미지에 jar 하나만 옮긴다 ──
FROM eclipse-temurin:21-jre
WORKDIR /app

# root 로 실행하지 않는다 (컨테이너 탈출 시 피해 축소)
RUN groupadd --gid 1000 app && useradd --uid 1000 --gid app --no-create-home app
USER 1000:1000

COPY --from=build --chown=1000:1000 /workspace/build/libs/app.jar app.jar

EXPOSE 8080 8081

# 컨테이너 메모리 한도의 75% 를 힙으로, OOM 이면 즉시 종료해서 쿠버네티스가 재시작하게 한다
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]