# VPS and Vercel runbook

The steps that need credentials, or that touch infrastructure outside this repository. Each one is
done deliberately, once, by a person. Everything that can be automated is already in the pipeline.

## What is already verified, and what is not

| | Status |
|---|---|
| The pipeline, end to end, `DEPLOY_TARGET=local` | **Verified.** Jenkins build #17 on commit `91e8b57`: ten stages, SUCCESS in 776s — the Guards suite (13 checks), Cucumber (11 scenarios, 66 steps), 41 Playwright cases, and a 4/4 smoke test. Build #17 is the first run whose acceptance evidence is trustworthy: until `scripts/assert-backend-image.sh` existed, the dev compose named no image and `docker compose up` reused a stale one, so every earlier run — build #12 included — tested a backend binary dated 2026-10-08. The console of #17 reads `ok: inversiones-backend is running inversiones-backend:91e8b574b356` and `41 passed`, which is the pairing that makes the number mean something. |
| `deploy/docker-compose.prod.yml` | Linted, reviewed, and checked by `deploy/__tests__/compose-prod.sh` (14 checks). **Never run** — it needs the VPS `.env`. |
| `frontend/vercel.json` | Checked by 6 unit tests. **Never deployed** — no Vercel account exists. |
| The `vps` deploy path in `Jenkinsfile` | Written and linted. Skipped by its own `when` guard until a Vercel token exists, which is why build #12 shows `Deploy Frontend` as NOT_EXECUTED rather than green. |

Nothing below has been executed. Treat it as a procedure to follow, not a record of what happened.

## Verified facts about the VPS

Checked over SSH on 2026-10-28, not assumed:

| Fact | Value |
|---|---|
| Host | `148.230.88.179`, hostname `srv1449561` |
| DNS | `*.srv1449561.hstgr.cloud` is a **wildcard** to that IP — no DNS change needed |
| Traefik | container `n8n-traefik-1`, network **`n8n_default`**, owns :80 and :443 |
| Entrypoints | `web` (:80, redirects to websecure), `websecure` (:443) |
| Cert resolver | **`mytlschallenge`**, TLS-ALPN-01 |
| Traefik default | `exposedbydefault=false` — `traefik.enable=true` is required |
| Memory | 7.8 GB total, 5.2 GB available, **no swap** |
| Existing limits | none of the seven running containers declares one |
| CPUs | 2 |
| Disk | 96 GB, 80 GB free |
| Stacks | `/docker/n8n`, `/docker/social-autopost`. This one goes in `/docker/inversiones` |
| Free host ports | 8080 and 5432 are unused |

## 1. Vercel, first time

No account exists yet.

1. Create an account at vercel.com and a project pointing at this repository.
2. Set the root directory to `frontend`. `vercel.json` supplies the build command and output
   directory, so leave those at their detected values.
3. Add the environment variable, for both Production and Preview:

       VITE_API_BASE_URL = https://api.srv1449561.hstgr.cloud

4. Create a token under Account Settings → Tokens, and put it in Jenkins as the credential
   `vercel-token` (the id `jenkins/casc/jenkins.yaml` already declares as a placeholder).
5. Note the production domain Vercel assigns. It goes into `SECURITY_ALLOWED_ORIGINS` in step 2,
   and **the backend will reject the frontend until it does** — CORS failures in the browser
   console with no server-side error. That is the safe direction: a wildcard would have been the
   unsafe one.

## 2. The VPS, first deploy

```bash
ssh root@148.230.88.179
mkdir -p /docker/inversiones && cd /docker/inversiones
# Copy deploy/.env.template here as .env and fill in the four values marked REQUIRED.
chmod 600 .env
```

Generate the two secrets **on the VPS**, not locally, so they never touch a laptop's shell history:

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 32   # POSTGRES_PASSWORD
```

The four REQUIRED values are `POSTGRES_PASSWORD`, `JWT_SECRET`, `BACKEND_HOST` and
`SECURITY_ALLOWED_ORIGINS`. None has a default in the compose file, on purpose: a deploy that
forgets one stops with a message naming it instead of starting with a guessable password or an
origin that silently blocks the frontend.

**Keep a copy of this `.env` somewhere safe.** `docker compose down` needs the same four values
that `up` does, so losing it means not being able to stop the stack cleanly either.

Confirm the Traefik network before deploying — the compose file references it as external, so a
wrong name fails the deploy rather than creating a stray network that routes nothing:

```bash
docker network ls --filter name=n8n_default
```

Then deploy from the Mac, with the compose file evaluated locally and the containers run there:

```bash
export DOCKER_HOST=ssh://root@148.230.88.179
docker compose -f deploy/docker-compose.prod.yml up -d --build --wait
```

Verify, in this order:

```bash
docker compose -f deploy/docker-compose.prod.yml ps          # both healthy
curl -s https://api.srv1449561.hstgr.cloud/actuator/health   # {"status":"UP"}
./scripts/smoke.sh https://api.srv1449561.hstgr.cloud        # 4/4
```

The certificate is issued on the first HTTPS request and takes a few seconds. If it fails, read
`docker logs n8n-traefik-1` for the ACME error rather than retrying blindly — repeated failures
count against Let's Encrypt's rate limit, which is per-domain and slow to reset.

### The one check that protects n8n

```bash
docker inspect inversiones-backend --format 'mem_limit={{.HostConfig.Memory}}'
docker exec inversiones-backend java -XX:+PrintFlagsFinal -version | grep -i maxheapsize
```

Expected: `mem_limit=1073741824`, and a `MaxHeapSize` near 750 MB — **not** near 5.9 GB. A heap
sized from the host rather than the container means the limit is not being applied, and this
machine has no swap: the kernel would eventually kill a process to reclaim memory, and the
process it picks is not necessarily this one.

This duplicates `deploy/__tests__/compose-prod.sh`, deliberately. That test reads the file; this
reads the running container. They verify different things, and the session that built this stack
watched a memory limit be declared and not take effect.

## 3. Jenkins on the VPS

The same `jenkins/docker-compose.yml`, with four changes — the first two are configuration, the
last two are security:

1. Route it through Traefik with its own `Host(...)` label rather than publishing port 8090.
2. Replace the SCM polling trigger in `casc/jenkins.yaml` with a GitHub webhook: add
   `https://<jenkins-host>/github-webhook/` under the repository's Settings → Webhooks, content
   type `application/json`, push events only. Polling exists only because a laptop has no public
   IP for GitHub to reach.
3. **Remove `-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true`** from `JAVA_OPTS`. It is
   there because the local setup checks out over `file:///repo`, which the git plugin blocks by
   default — a local checkout can reference any path the Jenkins process can read. On the VPS the
   SCM is a real remote and the flag has no reason to exist.
4. **Reconsider the mounted Docker socket.** It is root-equivalent control of the host's Docker
   daemon, which also runs n8n. Acceptable on a single-developer laptop; on a public host, a
   Jenkins compromise becomes a host compromise. If Jenkins stays on this VPS, run the builds on
   a separate agent rather than inside the controller.

Two environment variables in that file are laptop-specific and should come out as well:
`TESTCONTAINERS_HOST_OVERRIDE` (a Docker Desktop workaround) and the `GIT_CONFIG_*` trio (which
exists because the mounted repo is owned by a different uid).

## 4. Daily backup

`deploy/backup/pg_dump_daily.sh` takes a compressed dump, keeps 14, and **rejects a dump that
looks wrong** rather than rotating it into the keep set. Its defaults suit this database; they are
all overridable by environment variable (`MIN_BYTES`, `MIN_RATIO_PCT`, `KEEP`, `DEST`,
`CONTAINER`).

```bash
scp deploy/backup/pg_dump_daily.sh root@148.230.88.179:/usr/local/bin/
ssh root@148.230.88.179 'chmod +x /usr/local/bin/pg_dump_daily.sh'
```

Install the cron entry. Check first whether it is already there — running the append twice gives
two identical entries and two backups a night:

```bash
ssh root@148.230.88.179 'crontab -l 2>/dev/null | grep -c pg_dump_daily'
```

If that prints `0`:

```bash
ssh root@148.230.88.179 \
  '(crontab -l 2>/dev/null; echo "15 3 * * * /usr/local/bin/pg_dump_daily.sh >> /var/log/inversiones-backup.log 2>&1") | crontab -'
```

Run it once by hand before trusting the schedule, and read the output:

```bash
ssh root@148.230.88.179 '/usr/local/bin/pg_dump_daily.sh'
```

A healthy run prints `backup ok:` with the size and how many are kept. A rejected one prints why,
leaves the file as `*.suspect`, and exits non-zero — so cron will mail it rather than failing
silently. The first run says "no previous backup to compare", which is expected exactly once.

### This retention is not disaster recovery

Both the database and all 14 dumps live on the same disk, on the same machine. That protects
against operator error — a bad migration, a wrong `DELETE` — and against nothing else. A disk
failure or a lost VPS takes the database and every backup of it together.

Copy them somewhere else. Any of these is enough, and the first is the least work:

```bash
# From the Mac, nightly or by hand. --delete is deliberately absent: a dump deleted on the VPS by
# rotation should not disappear from the archive too.
rsync -az root@148.230.88.179:/var/backups/inversiones/ ~/Backups/inversiones/
```

Or `rclone` to any object store from the VPS itself, added to the same cron entry as the dump. Until
one of those exists, treat the backup as protection against mistakes, not against loss.

### Prove a restore works

A backup nobody has restored is a hypothesis. Do this once now, and again after any Postgres
major-version change:

```bash
ssh root@148.230.88.179 '
  set -e
  latest=/var/backups/inversiones/$(ls -1t /var/backups/inversiones | grep "\.sql\.gz$" | head -1)
  echo "restoring $latest"
  docker exec inversiones-postgres psql -U inversiones -d postgres -c "CREATE DATABASE restore_test;"
  gunzip -c "$latest" | docker exec -i inversiones-postgres psql -U inversiones -d restore_test
  docker exec inversiones-postgres psql -U inversiones -d restore_test -c "\dt"
  docker exec inversiones-postgres psql -U inversiones -d restore_test -c "select count(*) from transactions;"
  docker exec inversiones-postgres psql -U inversiones -d postgres -c "DROP DATABASE restore_test;"
'
```

Expected: eight tables, and a row count matching production. `\dt` returning "Did not find any
relations" means the dump carried headers but no schema — which is the case `MIN_BYTES` exists to
catch, measured at 377 bytes on an empty database versus about 4 KB for the schema alone.

## 5. Rolling back a bad deploy

Images are tagged with the commit, never `latest` — that is what makes this possible:

```bash
export DOCKER_HOST=ssh://root@148.230.88.179
IMAGE_TAG=<previous-commit-sha-12> docker compose -f deploy/docker-compose.prod.yml up -d --wait
```

A rollback does **not** revert the database. Flyway migrations are forward-only here, so a
migration that has run stays run: check whether the bad deploy added one before rolling back, and
restore from backup if it did.

## Known limitations, carried from earlier plans

- Two of the six signal sources (`news`, `analyst`) return no data from n8n today, so several
  scores arrive null. Workflow state, not an API fault.
- `/inv/earnings` (WF-13) returns `[]`.
- A degraded upstream reaches clients as HTTP 404 "Could not resolve ticker", which names the
  wrong cause; 503 would be honest. Recorded in `docs/superpowers/decisions.md`.
- Caffeine's `@Cacheable` does not coalesce concurrent loaders, so a cold cache under load lets
  every request through to n8n at once. Measured: 524 failed calls, three circuit breakers opened,
  p99 from 61 ms to 30,514 ms. Unfixed, recorded.
- The `vps` deploy runs `docker compose up --build` over `DOCKER_HOST=ssh://`, so the full
  Gradle-in-Docker build would run ON the VPS: 2 CPUs, 5.2 GB available, no swap, and BuildKit is
  not covered by the container `mem_limit`. That is the OOM this whole deployment plan exists to
  prevent, triggered by the deploy itself — and it also means the image the pipeline tested is
  never the image that runs. The fix is a registry (GHCR with a token is enough) and
  `--pull always` with no `--build`; `docker save | ssh docker load` works without one. Settle this
  before the first VPS deploy.
- Migration `V2__seed_reference_data.sql` inserts the user `david` with the bcrypt hash of
  `Inversiones2026!` unconditionally, and `spring.flyway.enabled` is true in the base
  `application.yml`, so it runs under the `prod` profile too. `scripts/smoke.sh` also defaults to
  that credential, which means the pipeline's definition of a healthy production deploy is "the
  published password still logs in" — rotating it turns CI red. That credential is deliberately
  public in this repository; it must not be live on the internet. Removing the insert from `V2`
  changes its checksum and Flyway will reject an existing database, so this needs a decision, not
  a quick edit.
  **Decision taken 2026-11-25 (project owner): keep the seeded user as it is.** This is a personal
  project with a single user and no public deploy, so the credential being public costs nothing
  today and the pipeline keeps a working smoke test. The decision is scoped to that premise: it
  becomes a BLOCKER the moment this reaches a public URL. Whoever does that first deploy owes two
  changes — a compensating migration that removes or rotates the seeded user outside the `local`
  profile, and a `SMOKE_PASSWORD` the pipeline reads from a credential instead of defaulting to the
  committed one.
- Mutation testing runs but gates nothing: no `mutationThreshold` is configured, and 65% mutation
  coverage against 96.5% line coverage with 208 `NO_COVERAGE` mutations suggests PIT is not
  running over the class set JaCoCo measures. Diagnose that before setting any threshold. PIT is
  no longer wired into `build` — run it deliberately with `./gradlew pitest`.
