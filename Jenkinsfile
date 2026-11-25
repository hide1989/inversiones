// Declarative pipeline, identical on the Mac and on the VPS. The deploy target is a PARAMETER
// rather than a branch condition, so the whole pipeline can be exercised end to end before any
// production credential exists.
//
// Several commands below carry flags that look redundant and are not. Each one was learned by
// watching this project report success while verifying nothing; the comment above each says which
// failure it prevents.
pipeline {
  agent any

  parameters {
    choice(
      name: 'DEPLOY_TARGET',
      choices: ['none', 'local', 'vps'],
      description: 'none: build and test only. local: compose up on this host. vps: remote Docker context over SSH.'
    )
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 60, unit: 'MINUTES')
  }

  environment {
    IMAGE_TAG = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(12) : 'dev'}"

    // POSTGRES_PORT is deliberately NOT here. It was, and it broke the backend stage:
    // ProductionConfigurationTest asserts that the local profile still supplies
    // jdbc:postgresql://localhost:5432/inversiones with nothing set, and a pipeline-level value
    // leaks into that test's environment. That test was written in Task 1 precisely to guard this
    // regression, and it caught it four tasks later in a different file. The variable now lives on
    // the two stages that actually run docker compose.

    // The browser, the tests and the smoke script all run INSIDE the Jenkins container, where
    // localhost:8080 is Jenkins itself; the composed backend is a sibling publishing on the HOST.
    // This address was verified from inside this container to resolve to 192.168.65.254 and accept
    // connections, where the bridge gateway 172.17.0.1 has no name at all.
    BACKEND_BASE_URL = 'http://host.docker.internal:8080'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        sh 'git --no-pager log --oneline -1'
      }
    }

    stage('Backend: Build & Test') {
      steps {
        dir('backend') {
          // --rerun-tasks: without it Gradle reports "BUILD SUCCESSFUL in 2s, 51 of 52 tasks
          // up-to-date" and the tests never run, which is indistinguishable from a pass. This bit
          // during Task 1 of this very plan.
          //
          // `build` already runs validateStructure (compileJava.dependsOn) and the merged 80%
          // coverage gate (check.dependsOn jacocoMergedCoverageVerification), so neither needs
          // naming here. It no longer runs PIT: jacocoMergedReport used to depend on
          // pitestReportAggregate, which made every build pay 13 minutes for mutation analysis
          // that gates nothing, broke Gradle's configuration cache, and OOM-killed this agent at
          // both 2 GB and 4 GB. That dependency is gone (see backend/main.gradle), so this is now
          // a one-minute build with the configuration cache working.
          sh './gradlew --no-daemon --rerun-tasks build'
        }
      }
      post {
        always {
          // allowEmptyResults: false is the point. With true, a build whose tests never ran
          // publishes zero results and still goes green.
          junit testResults: 'backend/**/build/test-results/test/*.xml', allowEmptyResults: false
        }
      }
    }

    stage('Frontend: Build & Test') {
      steps {
        dir('frontend') {
          sh 'npm ci'
          sh 'npm run typecheck'
          sh 'npm run lint'
          sh 'npx vitest run --coverage --reporter=json --outputFile=.vitest/report.json'
          // Read the machine-readable report this run just wrote, rather than parsing stdout.
          // Two reasons, both learned here: vitest emits ANSI escapes inside the phrase "Test
          // Files", so a plain grep for it returns empty — which reads exactly like "no failures";
          // and a test FILE that fails to compile prints "Test Files 1 failed" while the Tests
          // line still reads all-passed.
          //
          // Note the --outputFile above: .vitest/json/output.json in this repo is written by a
          // local command proxy, NOT by vitest, and goes stale without warning. It read six weeks
          // old during Task 2. Always assert on a file the run itself produced.
          sh '''
            python3 - <<'PY'
import json, sys
report = json.load(open('.vitest/report.json'))
print(f"suites={report.get('numTotalTestSuites')} "
      f"failedSuites={report.get('numFailedTestSuites')} "
      f"tests={report.get('numTotalTests')} failed={report.get('numFailedTests')}")
if not report.get('success') or report.get('numFailedTestSuites') or report.get('numFailedTests'):
    sys.exit('vitest reported failures')
PY
          '''
          sh 'npm run build'
        }
      }
    }

    stage('Docker Build') {
      steps {
        sh 'docker build -t inversiones-backend:${IMAGE_TAG} -f backend/deployment/Dockerfile backend'
        sh 'docker image inspect inversiones-backend:${IMAGE_TAG} --format "built {{.Id}} size {{.Size}}"'
      }
    }

    stage('Acceptance Tests') {
      environment {
        // POSTGRES_PORT is set per-stage, not pipeline-wide: a global value leaks into the
        // backend tests and fails ProductionConfigurationTest, which asserts the local profile's
        // defaults with nothing set. It is needed here because deploy/.env is git-ignored and
        // absent from Jenkins' checkout, so the compose file would publish 5432 — held on this
        // host by an unrelated pgvector container.
        POSTGRES_PORT = '55432'
        // BACKEND_BASE_URL comes from the pipeline-level environment above.
        // These two point the suite's own machinery at the same address: global-setup.ts reads
        // E2E_BACKEND_URL for its precondition check, and the built bundle reads
        // VITE_API_BASE_URL for its API calls. The browser's ORIGIN stays http://localhost:5173
        // (the in-container preview server), so the backend's CORS allowlist needs no change.
        E2E_BACKEND_URL = "${BACKEND_BASE_URL}"
        VITE_API_BASE_URL = "${BACKEND_BASE_URL}"
      }
      steps {
        sh 'docker compose -f deploy/docker-compose.dev.yml up -d --wait'
        // The stack must be running the image Docker Build just produced. For seven weeks it was
        // not: the dev compose named no image, so Compose invented inversiones-dev-backend:latest
        // and `up` reused it because `up` builds only when an image is ABSENT, never when it is
        // stale. Every Playwright run and every smoke test in this pipeline was therefore talking
        // to a binary from 2026-10-08. This line is what makes that a build failure.
        sh './scripts/assert-backend-image.sh inversiones-backend:${IMAGE_TAG}'
        dir('backend') {
          // The Cucumber suite uses Testcontainers and WireMock, so it does not need the stack
          // above — that is brought up for the Playwright flows, which talk to the real API.
          // acceptanceTest declares outputs.upToDateWhen { false }, so it always re-runs.
          sh './gradlew --no-daemon :app-service:acceptanceTest'
        }
        dir('frontend') {
          sh 'npx playwright install --with-deps chromium webkit'
          // e2e/global-setup.ts refuses to run against a dev server left on port 5173, and against
          // a backend that is not answering at E2E_BACKEND_URL. Both would otherwise pass while
          // testing the wrong thing.
          sh 'npm run e2e'
        }
      }
      post {
        always {
          sh 'docker compose -f deploy/docker-compose.dev.yml down -v || true'
        }
      }
    }

    stage('Publish Reports') {
      steps {
        // Every path below was verified to exist after a real run. The plan originally named
        // backend/build/reports/jacoco/jacocoMergedReport/html, which does not exist — the merged
        // report is one directory higher. Publishing a path that is never written shows an empty
        // artifact that looks like a passing report.
        publishHTML(target: [
          reportDir: 'backend/build/reports/jacocoMergedReport/html',
          reportFiles: 'index.html', reportName: 'JaCoCo (merged)',
          keepAll: true, alwaysLinkToLastBuild: true, allowMissing: false
        ])
        // No PIT report here: the pipeline no longer runs mutation analysis, so publishing it
        // would show whatever a previous manual run left behind, or nothing. Run
        // `./gradlew pitestReportAggregate` deliberately and read it locally.
        publishHTML(target: [
          reportDir: 'backend/applications/app-service/build/reports/cucumber',
          reportFiles: 'report.html', reportName: 'Cucumber',
          // allowMissing stays false deliberately: this report exists only because
          // RunCucumberTest's @ConfigurationParameter asks for it. If someone removes that, the
          // report vanishes and this stage should say so rather than publish nothing quietly.
          keepAll: true, alwaysLinkToLastBuild: true, allowMissing: false
        ])
        publishHTML(target: [
          reportDir: 'frontend/playwright-report',
          reportFiles: 'index.html', reportName: 'Playwright',
          keepAll: true, alwaysLinkToLastBuild: true, allowMissing: false
        ])
      }
    }

    stage('Deploy Backend') {
      when { expression { params.DEPLOY_TARGET != 'none' } }
      environment {
        // Same reason as the Acceptance Tests stage: this stage runs the same compose file, and a
        // pipeline-level value would break the backend tests.
        POSTGRES_PORT = '55432'
      }
      steps {
        script {
          if (params.DEPLOY_TARGET == 'local') {
            withCredentials([
              string(credentialsId: 'inversiones-jwt-secret', variable: 'JWT_SECRET'),
              string(credentialsId: 'inversiones-postgres-password', variable: 'POSTGRES_PASSWORD')
            ]) {
              // The production compose file references n8n_default as an external network, which
              // does not exist on a dev host — Compose validates every network in a file even for
              // a single service, so prod cannot start here at all. This path therefore brings up
              // the dev stack: it validates the pipeline, not the production topology.
              sh '''
                docker compose -f deploy/docker-compose.dev.yml up -d --wait
                ./scripts/assert-backend-image.sh inversiones-backend:${IMAGE_TAG}
                docker compose -f deploy/docker-compose.dev.yml ps
              '''
            }
          } else {
            withCredentials([
              string(credentialsId: 'inversiones-jwt-secret', variable: 'JWT_SECRET'),
              string(credentialsId: 'inversiones-postgres-password', variable: 'POSTGRES_PASSWORD'),
              string(credentialsId: 'inversiones-backend-host', variable: 'BACKEND_HOST'),
              string(credentialsId: 'inversiones-allowed-origins', variable: 'SECURITY_ALLOWED_ORIGINS')
            ]) {
              // DOCKER_HOST points at the VPS over SSH: the compose file is evaluated locally and
              // the containers run there. All four variables are required by the file's ${VAR:?}
              // form — including for `down`, since Compose interpolates the whole file before
              // doing anything.
              sh '''
                # Refuse to sign production tokens with a secret printed in this repository. Every
                # CasC credential carries a :-default so Jenkins boots on a laptop with no .env,
                # and because the prod compose file is evaluated HERE — not on the VPS, whose
                # /docker/inversiones/.env this path never reads — those defaults WOULD have been
                # the real production values. ${VAR:?} is satisfied by a placeholder, the
                # placeholder is 36 bytes so the JWT length check passes, and it is not the one
                # literal string the committed-default guard knows. Nothing else stops it.
                ./scripts/assert-not-placeholder.sh JWT_SECRET POSTGRES_PASSWORD SECURITY_ALLOWED_ORIGINS
                export DOCKER_HOST=ssh://root@148.230.88.179
                docker compose -f deploy/docker-compose.prod.yml up -d --build --wait
                docker compose -f deploy/docker-compose.prod.yml ps
              '''
            }
          }
        }
      }
    }

    stage('Deploy Frontend') {
      when { expression { params.DEPLOY_TARGET == 'vps' } }
      steps {
        withCredentials([string(credentialsId: 'vercel-token', variable: 'VERCEL_TOKEN')]) {
          dir('frontend') {
            sh 'npx vercel deploy --prod --yes --token=$VERCEL_TOKEN'
          }
        }
      }
    }

    stage('Smoke Test') {
      when { expression { params.DEPLOY_TARGET != 'none' } }
      steps {
        script {
          // scripts/smoke.sh takes the base URL as $1 and exits with a distinct code per failed
          // step, including a check that an unauthenticated call is rejected.
          // For `local`, BACKEND_BASE_URL rather than localhost:8080: this script runs inside the
          // Jenkins container, where localhost:8080 is Jenkins' own HTTP port and the smoke test
          // would check Jenkins' health instead of the application's — passing for the wrong
          // reason, which is worse than failing.
          def base = params.DEPLOY_TARGET == 'local'
            ? env.BACKEND_BASE_URL
            : 'https://api.srv1449561.hstgr.cloud'
          sh "./scripts/smoke.sh ${base}"
        }
      }
    }
  }

  post {
    always {
      sh 'docker compose -f deploy/docker-compose.dev.yml down -v || true'
    }
  }
}
