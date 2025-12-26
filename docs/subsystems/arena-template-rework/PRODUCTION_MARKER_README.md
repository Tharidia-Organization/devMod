# Production Marker File

## DD32: Autosmoke Production Guard

The `.production` marker file is part of the triple-guard system that prevents accidental autosmoke execution in production environments.

## Usage

### Creating the marker file

In production deployments, create an empty `.production` file in the server root directory:

```bash
touch /path/to/server/.production
```

### Guard checks

The `AutosmokeGuard` performs three checks before allowing autosmoke to run:

1. **Environment Variable**: `DEVMOD_ENV` must NOT be set to `production`
2. **Feature Flag**: `devmod.autosmoke.enabled` system property must be `true` (or unset)
3. **Marker File**: `.production` file must NOT exist

ALL THREE checks must pass for autosmoke to run.

### Deployment script example

```bash
#!/bin/bash
# production-deploy.sh

# Set environment variable
export DEVMOD_ENV=production

# Create marker file
touch .production

# Disable feature flag
java -Ddevmod.autosmoke.enabled=false -jar server.jar
```

### Development environment

In development, ensure:
- `DEVMOD_ENV` is unset or not `production`
- No `.production` file exists
- `devmod.autosmoke.enabled` is unset or `true`

## File locations

The marker file path can be configured via `AutosmokeGuard.setProductionMarkerPath()`.

Default: `.production` (relative to working directory)
