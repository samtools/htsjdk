# Contributing to HTSJDK

## Code Style

HTSJDK uses [Palantir Java Format](https://github.com/palantir/palantir-java-format)
(applied via the [Spotless](https://github.com/diffplug/spotless) Gradle plugin)
to enforce a single, mechanical code style across the codebase. There are no
formatting knobs to configure -- the formatter is the style guide.

Formatting is applied automatically as part of `compileJava`: every build
runs `spotlessJavaApply`, which rewrites any unformatted source in place
before compiling. In normal use you shouldn't need to invoke the formatter
yourself -- just build, and your code is formatted. If you want to format
without compiling, run:

```bash
./gradlew spotlessApply
```

CI runs `./gradlew spotlessCheck` (verify-only, no mutation) so a PR with
unformatted code still fails CI -- the local auto-format is a convenience,
not the enforcement boundary.

### Git blame and the bulk-format commit

The codebase was reformatted in a single mechanical commit. To keep `git blame`
useful (so you see the author who actually wrote each line, not the reformat
commit), the repository ships a `.git-blame-ignore-revs` file. GitHub honors
it automatically in the web UI; for `git blame` on the command line, opt in
once per clone:

```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

## Building

HTSJDK uses Gradle (via the Gradle wrapper). To build:

```bash
./gradlew jar
```

To run tests:

```bash
./gradlew test
```

To install to your local Maven repository (e.g. for testing with downstream projects):

```bash
./gradlew install
```

## Publishing to Maven Central

HTSJDK is published to Maven Central via the [Sonatype Central Portal](https://central.sonatype.com).
The build uses the [NMCP Gradle plugin](https://github.com/GradleUp/nmcp) to handle bundle
creation and upload.

### Prerequisites

#### 1. Sonatype Central Portal Account and Tokens

You need a Sonatype Central Portal account with access to the `com.github.samtools` namespace.

Generate a user token:

1. Log in to https://central.sonatype.com
2. Go to Account > User Token
3. Click "Generate User Token"
4. **Save the username and password immediately** -- they are shown only once

#### 2. Configure Sonatype Credentials

Gradle resolves project properties in this order (highest precedence first):

| Priority | Method | Example |
|----------|--------|---------|
| 1 | Command-line `-P` flag | `-PsonatypeUsername=...` |
| 2 | Environment variable | `ORG_GRADLE_PROJECT_sonatypeUsername=...` |
| 3 | `~/.gradle/gradle.properties` | `sonatypeUsername=...` |

**Option A: `~/.gradle/gradle.properties`** (recommended for local development)

Add to `~/.gradle/gradle.properties`:

```properties
sonatypeUsername=<your-token-username>
sonatypePassword=<your-token-password>
```

**Option B: Environment variables** (recommended for CI)

```bash
export ORG_GRADLE_PROJECT_sonatypeUsername=<your-token-username>
export ORG_GRADLE_PROJECT_sonatypePassword=<your-token-password>
```

**Option C: Command line** (one-off use)

```bash
./gradlew publishAllPublicationsToCentralPortal -Drelease=true \
  -PsonatypeUsername=<your-token-username> \
  -PsonatypePassword=<your-token-password>
```

#### 3. GPG Signing Key

Release artifacts must be signed with a GPG key that has been published to a public keyserver.

**Generate a new key** (if you don't have one):

```bash
gpg --full-generate-key
```

- Choose RSA and RSA, 4096 bits
- Set an expiration or choose no expiration
- Choose a passphrase you will remember

**Find your key ID:**

```bash
gpg --list-keys --keyid-format long
```

The key ID is the hex string on the `pub` line, e.g. `pub rsa4096/AABBCCDD11223344`.

**Publish to keyservers** (required -- Central Portal verifies signatures against these):

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys <YOUR_KEY_ID>
gpg --keyserver keys.openpgp.org --send-keys <YOUR_KEY_ID>
```

**Configure Gradle to use your key:**

If you have multiple GPG keys, add to `~/.gradle/gradle.properties`:

```properties
signing.gnupg.keyName=<YOUR_KEY_ID>
```

If you have only one key, this is optional -- Gradle will use the default key.

#### 4. GPG Agent and Pinentry Setup (macOS)

The build uses `useGpgCmd()` to delegate signing to your system `gpg` command, which in turn
uses `gpg-agent` to handle passphrase prompting. This avoids storing your GPG passphrase in
plain text.

For reliable passphrase prompting on macOS, install `pinentry-mac`:

```bash
brew install pinentry-mac
```

Then configure `gpg-agent` to use it. Add to `~/.gnupg/gpg-agent.conf`:

```
pinentry-program /opt/homebrew/bin/pinentry-mac
```

Restart the agent:

```bash
gpgconf --kill gpg-agent
```

Without `pinentry-mac`, you may see `Inappropriate ioctl for device` errors when Gradle
invokes `gpg` for signing, because the default pinentry cannot open a prompt from Gradle's
non-interactive process.

You should also ensure your shell has `GPG_TTY` set. Add to your `~/.zshrc` (or `~/.bashrc`):

```bash
export GPG_TTY=$(tty)
```

### Version Numbering

The build computes the version from git state plus a single declaration in
`build.gradle`:

```groovy
final nextVersionBump = "x"   // "x" major, "x.x" minor, "x.x.x" patch
```

`nextVersionBump` declares the *shape* of the next planned release relative to
the most recent semver tag (e.g. `4.3.0`):

| Bump    | Most recent tag | Computed next version |
| ------- | --------------- | --------------------- |
| `x`     | `4.3.0`         | `5.0.0`               |
| `x.x`   | `4.3.0`         | `4.4.0`               |
| `x.x.x` | `4.3.0`         | `4.3.1`               |

What the build actually publishes:

- **Release** (`-Drelease=true`): HEAD must be on a semver-tagged commit; the
  tag itself is the version (e.g. `5.0.0`). `nextVersionBump` is ignored on
  release — the tag is authoritative.
- **Snapshot** (default): `<computedNextVersion>-<shortHash>-SNAPSHOT`
  (e.g. `5.0.0-23c681a-SNAPSHOT`).

The short hash in snapshot versions makes each snapshot a distinct, pinnable
artifact rather than the usual moving-target Maven SNAPSHOT — consumers can
lock to a specific commit. Trade-off: there is no plain `5.0.0-SNAPSHOT` to
depend on for "always latest."

To see the version the build will produce:

```bash
./gradlew -q printVersion
```

After cutting a release, update `nextVersionBump` if the *next* planned release
is a different shape (e.g. switch from `x` to `x.x` once you start shipping
minor releases on a stable major line).

### Publishing a Snapshot

Snapshots are published from any state of the repository without signing:

```bash
./gradlew publishAllPublicationsToCentralPortalSnapshots
```

**Note:** Snapshot publishing to Central Portal requires that SNAPSHOT support is enabled
on the `com.github.samtools` namespace in the Central Portal settings.

### Publishing a Release

Releases are published from a git tag. The full process:

#### Step 1: Tag the Release

Make sure `nextVersionBump` in `build.gradle` matches the kind of release you
intend to ship (major / minor / patch). Then check the version and tag the
release commit:

```bash
./gradlew -q printVersion   # prints e.g. "5.0.0-23c681a-SNAPSHOT"
```

Strip the `-<hash>-SNAPSHOT` suffix to get the tag string. For the example
above, that's `5.0.0`:

```bash
git tag 5.0.0
git push origin 5.0.0
```

#### Step 2: Verify Locally (Dry Run)

Build and sign all artifacts into your local Maven repository:

```bash
git checkout X.Y.Z
./gradlew clean publishHtsjdkPublicationToMavenLocal -Drelease=true
```

Inspect the output:

```bash
ls ~/.m2/repository/com/github/samtools/htsjdk/X.Y.Z/
```

You should see:
- `htsjdk-X.Y.Z.jar` + `.asc`
- `htsjdk-X.Y.Z-javadoc.jar` + `.asc`
- `htsjdk-X.Y.Z-sources.jar` + `.asc`
- `htsjdk-X.Y.Z.pom` + `.asc`
- `htsjdk-X.Y.Z.module` + `.asc`

#### Step 3: Publish to Maven Central

```bash
./gradlew publishAllPublicationsToCentralPortal -Drelease=true
```

The NMCP plugin will:
1. Stage all artifacts locally
2. Generate checksums (MD5, SHA1, SHA256, SHA512)
3. Create a ZIP bundle
4. Upload to the Central Portal API
5. Wait for validation to pass
6. Automatically release to Maven Central (configured as `AUTOMATIC`)

#### Step 4: Verify

Artifacts typically appear on Maven Central within 15 minutes:

```
https://repo1.maven.org/maven2/com/github/samtools/htsjdk/X.Y.Z/
```

Search index updates (e.g. on https://search.maven.org) may take up to 2 hours.
