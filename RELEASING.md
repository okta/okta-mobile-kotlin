# Releasing
1. Update version number in [gradle.properties](gradle.properties), and [CHANGELOG](CHANGELOG.md), and merge the PR.
1. `git checkout master && git pull`
1. `git tag -s -a 1.0.0 -m "Release 1.0.0"` - Update the version number to the one you're trying to publish
1. `git push --tags`
1. `./gradlew publish --rerun-tasks --no-daemon --no-parallel -PsignWithGpgCommand`
1. `./gradlew closeAndReleaseRepository`

## Generating GPG Keys
- TLDR: `gpg --gen-key`
- [Maven Central GPG Docs](https://central.sonatype.org/publish/requirements/gpg/)

## Other means of signing
- See [Publishing Docs](https://github.com/vanniktech/gradle-maven-publish-plugin#signing)
- If using an in memory GPG Key:
    - Export the secret key with something along the lines of `gpg --output ~/Desktop/OktaPrivate.pgp --armor --export-secret-key jay.newstrom@okta.com`
    - Then you can format it correctly using something along the lines of `cat ~/Desktop/OktaPrivate.pgp | grep -v '\-\-' | grep -v '^=.' | tr -d '\n'`
