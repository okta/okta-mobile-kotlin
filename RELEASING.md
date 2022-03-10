# Releasing
1. Update version number in [gradle.properties](gradle.properties), and [CHANGELOG](CHANGELOG.md), and merge the PR.
1. `git checkout master && git pull`
1. `git tag -s -a 1.0.0 -m "Release 1.0.0"` - Update the version number to the one you're trying to publish
1. `git push --tags`
1. `./gradlew clean && ./gradlew publish --no-daemon --no-parallel`
1. `./gradlew closeAndReleaseRepository`
