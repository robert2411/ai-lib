# Smoke Test Checklist

Manual smoke test for verifying the Docker deployment of agent-library.

## Prerequisites

- Docker and Docker Compose installed (Docker Engine 20.10+)
- Port 8080 available on localhost
- `curl` available
- Terminal with bash

## Steps

### Step 1: Build and Start

```bash
docker compose up --build -d
```

Wait for the service to become healthy:

```bash
docker compose ps
```

Expected: `agent-library` shows status `healthy`.

- [ ] Service starts and logs show bootstrap complete

### Step 2: Health Check

```bash
curl -s http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

- [ ] Health endpoint responds with `{"status":"UP"}`

### Step 3: Seed Skills Visible at Root

```bash
curl -s -u admin:changeme http://localhost:8080/ | grep -i "agent-library-install"
```

Expected: HTML page at `/` contains references to seed skills (agent-library-install, agent-lib.sh).

- [ ] Seed skills are visible on the root page

### Step 4: Browse Page

```bash
curl -s -u admin:changeme http://localhost:8080/browse | grep -i "agent-lib"
```

Expected: HTML browse page loads and lists artifacts including agent-lib.

- [ ] Browse page loads and lists artifacts

### Step 5: Install a Skill via CLI

```bash
./scripts/agent-lib.sh install agent-library-install --server http://localhost:8080
```

Verify the file was placed correctly:

```bash
ls -la .claude/skills/agent-library-install.md && echo "✓ File installed"
```

Expected: The skill file exists in the target directory.

- [ ] Skill installed successfully via agent-lib.sh

### Step 6: Push a New Artifact

```bash
curl -s -X POST http://localhost:8080/api/v1/artifacts \
  -u admin:changeme \
  -H "Content-Type: text/plain" \
  -d '---
name: smoke-test-artifact
version: 1.0.0
type: skill
description: Smoke test artifact
---
# Smoke Test Artifact

This artifact was created during smoke testing.'
```

Expected: HTTP 201 Created response.

- [ ] New artifact created with 201 response

### Step 7: Verify New Artifact Appears in Browse

```bash
curl -s -u admin:changeme http://localhost:8080/browse | grep -i "smoke-test-artifact"
```

Expected: The browse page lists the newly pushed artifact.

- [ ] New artifact appears on the browse page

### Step 8: Persistence After Restart

```bash
docker compose down
docker compose up -d
```

Wait for healthy status, then verify the artifact persists:

```bash
curl -s -u admin:changeme http://localhost:8080/api/v1/artifacts/smoke-test-artifact/1.0.0
```

Expected: Same artifact content as Step 7.

- [ ] Artifact persists after docker compose restart

### Step 9: Clean Up

```bash
docker compose down -v
```

This removes containers AND the named volume (all data deleted).

- [ ] Clean shutdown with volume removal

## Pass Criteria

All checkboxes ticked = **PASS**.

Any failure = note which step failed and include the error output.

## Troubleshooting

### Port 8080 already in use

```bash
# Find the process using port 8080
lsof -i :8080
# Kill it or change the port in docker-compose.yml:
# ports: - "9090:8080"
```

### Permission denied on /data volume

If using a bind mount instead of a named volume, ensure the host directory is writable:

```bash
mkdir -p ./docker-data
chmod 777 ./docker-data
# Then update docker-compose.yml volumes to: ./docker-data:/data
```

### Container exits immediately

Check the logs for startup errors:

```bash
docker compose logs agent-library
```

Common cause: invalid `users.yaml` format or missing required configuration.

### Health check failing

```bash
# Check if the app started
docker compose logs agent-library | tail -30

# Verify the health endpoint manually from inside the container
docker compose exec agent-library curl -f http://localhost:8080/actuator/health
```

### Default credentials

On first boot, `docker-entrypoint.sh` creates a default admin user:
- Username: `admin`
- Password: `changeme`

**Change these immediately** using `gen-password-hash.sh`:

```bash
docker compose exec agent-library /app/scripts/gen-password-hash.sh mynewpassword
# Update /data/users.yaml with the new hash, then restart
docker compose restart agent-library
```
