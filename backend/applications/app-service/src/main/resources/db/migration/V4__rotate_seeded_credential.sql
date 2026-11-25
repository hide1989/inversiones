-- Rewrites the seeded user's password hash from a Flyway placeholder, so the credential that runs
-- in a deployment is never the one committed to this repository.
--
-- WHY THIS IS A NEW MIGRATION AND NOT AN EDIT TO V2
--
-- V2 has already been applied to every existing database, and Flyway stores a checksum of each
-- applied migration. Editing V2 changes its checksum, and `flyway validate` then refuses to start
-- against any database that ran the old version — which is every database this project has. A
-- compensating migration is the mechanism Flyway provides for exactly this: V2 stays as history,
-- V4 states the correction.
--
-- WHY A PLACEHOLDER AND NOT A LITERAL
--
-- Flyway has no notion of a Spring profile: whatever is written here runs in local development and
-- in production alike. A literal hash would therefore be either useless (the committed one) or a
-- production secret committed to Git. The placeholder resolves from SEED_PASSWORD_HASH, which
-- application.yml requires with no default outside the `local` profile — so a deploy that forgets
-- it fails at startup naming the variable, rather than coming up with the published password.
--
-- The UPDATE is idempotent and safe to re-run: in local development the placeholder resolves to
-- the same hash V2 inserted, so this rewrites the value to itself.
UPDATE users
   SET password_hash = '${seed_password_hash}'
 WHERE username = 'david';
