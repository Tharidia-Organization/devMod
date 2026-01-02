# DevMod Release Checklist

## Automated Release Script

Use `scripts/release.sh` for automated version bumping and tagging:

```bash
# Preview changes (dry run)
./scripts/release.sh --dry-run patch

# Bump patch version (0.1.0 -> 0.1.1)
./scripts/release.sh patch

# Bump minor version (0.1.0 -> 0.2.0)
./scripts/release.sh minor

# Bump major version (0.1.0 -> 1.0.0)
./scripts/release.sh major

# Set explicit version
./scripts/release.sh 1.0.0-beta
```

The script will:
1. Update `mod_version` in `gradle.properties`
2. Generate changelog entry in `CHANGELOG.md`
3. Create git commit with release message
4. Create annotated git tag (e.g., `v0.2.0`)

## Manual Pre-Release Checklist

Before running the release script:

### Code Quality
- [ ] All tests pass: `./gradlew test`
- [ ] Static analysis clean: `./gradlew spotbugsMain`
- [ ] No compiler warnings: `./gradlew compileJava`
- [ ] Build succeeds: `./gradlew build`

### Documentation
- [ ] README.md is up to date
- [ ] ARCHITECTURE.md reflects current structure
- [ ] API changes documented
- [ ] Breaking changes noted

### Compatibility
- [ ] Tested on target Minecraft version (1.21.1)
- [ ] Tested with target NeoForge version
- [ ] Mod compatibility verified (check MOD_INVENTORY.md)

### Security
- [ ] No hardcoded credentials
- [ ] Network payloads validated
- [ ] Rate limiting in place

## Post-Release Steps

After running the release script:

```bash
# 1. Push commit and tag
git push origin main
git push origin --tags

# 2. Build release artifact
./gradlew build

# 3. Create GitHub release (optional)
gh release create v0.2.0 \
  --title "DevMod v0.2.0" \
  --notes-file CHANGELOG.md \
  build/libs/devmod-0.2.0.jar
```

### Distribution
- [ ] Upload to CurseForge (if applicable)
- [ ] Upload to Modrinth (if applicable)
- [ ] Notify users in Discord/community

## Version Numbering

We follow [Semantic Versioning](https://semver.org/):

- **MAJOR** (1.0.0): Breaking changes, incompatible API changes
- **MINOR** (0.1.0): New features, backwards compatible
- **PATCH** (0.0.1): Bug fixes, backwards compatible

### Pre-release Tags
- `0.1.0-alpha`: Early development, unstable
- `0.1.0-beta`: Feature complete, testing phase
- `0.1.0-rc.1`: Release candidate

## Rollback Procedure

If a release needs to be reverted:

```bash
# 1. Delete the tag locally and remotely
git tag -d v0.2.0
git push origin :refs/tags/v0.2.0

# 2. Revert the release commit
git revert HEAD

# 3. Push the revert
git push origin main

# 4. Delete GitHub release (if created)
gh release delete v0.2.0 --yes
```

## Release History

See [CHANGELOG.md](../CHANGELOG.md) for version history.
