FROM docker.io/library/node:24-bookworm-slim@sha256:dc26ae92c9fb15cf6b1d63e20f0944b2efe0f115b20e00be48fb23fceb061c54 AS android-sdk

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
    ANDROID_HOME=/opt/android-sdk
ENV PATH="${JAVA_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${PATH}"

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl git jq openjdk-17-jdk-headless unzip zip \
    && rm -rf /var/lib/apt/lists/*

RUN set -eu; \
    curl --fail --silent --show-error --location --retry 3 \
        https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip \
        --output /tmp/android-tools.zip; \
    echo '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  /tmp/android-tools.zip' | sha256sum -c; \
    mkdir -p "$ANDROID_HOME/cmdline-tools"; \
    unzip -q /tmp/android-tools.zip -d /tmp/android-tools; \
    mv /tmp/android-tools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"; \
    yes | sdkmanager --licenses >/dev/null; \
    sdkmanager --install 'platform-tools' 'platforms;android-37.0' 'build-tools;36.0.0' 'build-tools;37.0.0'; \
    rm -r /tmp/android-tools /tmp/android-tools.zip

FROM android-sdk AS signer
RUN apt-get update \
    && apt-get install --no-install-recommends -y python3 python3-cryptography python3-jwt \
    && rm -rf /var/lib/apt/lists/*
COPY signer.py /opt/quantoot/signer.py
ENTRYPOINT ["python3", "/opt/quantoot/signer.py"]

FROM android-sdk AS runner
