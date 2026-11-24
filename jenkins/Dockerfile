# Jenkins with everything this project's pipeline actually invokes.
#
# Four reasons this image is built rather than pulled, each found by watching a stage fail:
#
#   1. PLUGINS. Mounting plugins.txt at /usr/share/jenkins/ref/plugins.txt installs nothing on a
#      modern jenkins/jenkins image — that path is only a reference directory. With it mounted the
#      container came up with the image's bundled plugins and neither configuration-as-code nor
#      job-dsl among them, so JCasC never ran and the admin user was never created.
#      jenkins-plugin-cli is what installs them, at build time.
#
#   2. JDK 25. The lts-jdk21 tag cannot build this project: Gradle resolves a Java 25 toolchain and
#      fails with "Cannot find a Java installation ... matching {languageVersion=25}". The backend
#      targets Java 25 (see docs/adr/ADR-005), so the agent needs it.
#
#   3. NODE. The frontend stage runs npm ci, vitest, eslint and vite. The image ships neither node
#      nor npm.
#
#   4. DOCKER CLI AND COMPOSE. The pipeline builds images and brings compose stacks up. The image
#      ships no docker client — the mounted socket is useless without one. Only the CLI is
#      installed; the daemon is the host's, reached through that socket.
#
# python3 is here because the pipeline asserts on vitest's JSON report with it, rather than parsing
# terminal output that carries ANSI escapes inside the phrase it would have to match.
FROM jenkins/jenkins:lts-jdk25

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt

# --plugin-file resolves each plugin's transitive dependencies, so plugins.txt lists only what this
# project names.
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

USER root

# Node 22 to match the version the project is developed against (the lockfile was produced by
# npm 10 on Node 22), python3 for the report assertions, and the Docker CLI plus the Compose v2
# plugin — client only, since the daemon is the host's over the mounted socket.
ARG NODE_MAJOR=22
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates curl gnupg python3; \
    install -m 0755 -d /etc/apt/keyrings; \
    curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
      | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg; \
    echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_${NODE_MAJOR}.x nodistro main" \
      > /etc/apt/sources.list.d/nodesource.list; \
    curl -fsSL https://download.docker.com/linux/debian/gpg \
      | gpg --dearmor -o /etc/apt/keyrings/docker.gpg; \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
      > /etc/apt/sources.list.d/docker.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends nodejs docker-ce-cli docker-compose-plugin; \
    rm -rf /var/lib/apt/lists/*; \
    node --version; npm --version; docker --version; docker compose version; python3 --version

# The compose file runs this container as root so it can use the mounted Docker socket, so this
# line is informational rather than load-bearing — it keeps the image usable without that override.
USER jenkins
