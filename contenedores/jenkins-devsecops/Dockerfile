FROM jenkins/jenkins:lts

USER root

# Dependencias base 
RUN apt-get update && apt-get install -y \
    curl wget unzip git \
    python3 python3-pip \
    maven \
    docker.io \        
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# SAST: Semgrep 
RUN pip3 install semgrep --break-system-packages

# SCA + Image Check: Trivy 
RUN curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh \
    | sh -s -- -b /usr/local/bin

# PaC: OWASP Dependency-Check 
RUN curl -sL \
    "https://github.com/jeremylong/DependencyCheck/releases/download/v10.0.4/dependency-check-10.0.4-release.zip" \
    -o /tmp/depcheck.zip \
    && unzip /tmp/depcheck.zip -d /opt/ \
    && rm /tmp/depcheck.zip \
    && ln -s /opt/dependency-check/bin/dependency-check.sh /usr/local/bin/dependency-check

# DAST: OWASP ZAP 2.16.1 
RUN curl -L \
    "https://github.com/zaproxy/zaproxy/releases/download/v2.16.1/ZAP_2.16.1_Linux.tar.gz" \
    -o /tmp/zap.tar.gz \
    && tar -xzf /tmp/zap.tar.gz -C /opt/ \
    && mv /opt/ZAP_2.16.1 /opt/zap \
    && rm /tmp/zap.tar.gz \
    && ln -s /opt/zap/zap.sh /usr/local/bin/zap.sh

USER jenkins

# Plugins Jenkins 
RUN jenkins-plugin-cli --plugins \
    git \
    github \
    workflow-aggregator \
    credentials-binding \
    pipeline-stage-view