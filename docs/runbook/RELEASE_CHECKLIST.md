# DevMod Release Checklist

> Ultimo aggiornamento: 2026-01-31
> Stato: CURRENT

## Automated Release Script

Usa `scripts/release.sh` per bump versione e tagging:

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

Lo script:
1. Aggiorna `mod_version` in `gradle.properties`
2. Genera changelog in `CHANGELOG.md`
3. Crea commit con messaggio release
4. Crea tag annotato (es. `v0.2.0`)

## Manual Pre-Release Checklist

Prima di eseguire lo script:

### Code Quality
- [ ] Test suite ok: `./gradlew test`
- [ ] Static analysis ok: `./gradlew spotbugsMain`
- [ ] Nessun warning bloccante: `./gradlew compileJava`
- [ ] Build completa: `./gradlew build`

### Documentation
- [ ] `docs/README.md` aggiornato
- [ ] `docs/ARCHITECTURE.md` allineato
- [ ] API changes documentate (dashboard/admin)
- [ ] Breaking changes annotate

### Compatibility
- [ ] Test su Minecraft 1.21.1
- [ ] Test su NeoForge 21.1.x (repo usa 21.1.216)
- [ ] Compat mod verificata (vedi `MOD_INVENTORY.md` se presente)

### Security / Gate
- [ ] Nessuna credenziale hardcoded
- [ ] Payload di rete validati
- [ ] Rate limiting dove previsto
- [ ] CI `release-gate.yml` verde (se usata)

## Post-Release Steps

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
- [ ] Upload su CurseForge (se applicabile)
- [ ] Upload su Modrinth (se applicabile)
- [ ] Notifica community/Discord

## Version Numbering

SemVer:
- **MAJOR** (1.0.0): breaking changes
- **MINOR** (0.1.0): nuove feature, backward compatible
- **PATCH** (0.0.1): bugfix

### Pre-release Tags
- `0.1.0-alpha`
- `0.1.0-beta`
- `0.1.0-rc.1`

## Rollback Procedure

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

Vedi `CHANGELOG.md`.
