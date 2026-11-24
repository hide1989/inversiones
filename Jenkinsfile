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
          // --no-configuration-cache: `build` depends on jacocoMergedReport, which depends on the
          // PIT plugin's :pitestReportAggregate, which Gradle's configuration cache cannot
          // serialise ("error writing value of type DefaultConfigurableFileCollection"). PROVEN
          // pre-existing by stashing every source change and reproducing it. Without this flag the
          // stage fails on a green tree.
          //
          // `build` already runs validateStructure (compileJava.dependsOn) and the merged 80%
          // coverage gate (check.dependsOn jacocoMergedCoverageVerification), so neither needs
          // naming here.
          sh './gradlew --no-daemon --rerun-tasks --no-configuration-cache build'
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
      steps {
        sh 'docker compose -f deploy/docker-compose.dev.yml up -d --wait'
        dir('backend') {
          // acceptanceTest declares outputs.upToDateWhen { false }, so it always re-runs; the
          // configuration-cache flag is still needed for the reasons above.
          sh './gradlew --no-daemon --no-configuration-cache :app-service:acceptanceTest'
        }
        dir('frontend') {
          sh 'npx playwright install --with-deps chromium webkit'
          // e2e/global-setup.ts refuses to run against a dev server left on port 5173, and against
          // a backend that is not up. Both would otherwise pass while testing the wrong thing.
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
        publishHTML(target: [
          reportDir: 'backend/build/reports/pitest',
          reportFiles: 'index.html', reportName: 'PIT (mutation)',
          keepAll: true, alwaysLinkToLastBuild: true, allowMissing: false
        ])
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
          def base = params.DEPLOY_TARGET == 'local'
            ? 'http://localhost:8080'
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
