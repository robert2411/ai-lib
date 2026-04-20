#!/bin/bash
set -e

DATA_DIR="${LIBRARY_DATA_DIR:-/data}"
USERS_FILE="${LIBRARY_USERS_FILE:-/data/users.yaml}"

# Verify /data is writable (named volumes inherit image permissions;
# bind mounts require the host directory to be writable by UID 100).
if [ ! -w "$DATA_DIR" ]; then
  echo "ERROR: $DATA_DIR is not writable by appuser. If using a bind mount,"
  echo "       ensure the host directory is writable: chmod 777 <host-path>"
  echo "       or run: docker run --user root ... (not recommended)."
  exit 1
fi

# Initialize users.yaml with a default admin user if it doesn't exist.
# The default password is "changeme" — operators MUST change this on first use.
if [ ! -f "$USERS_FILE" ]; then
  echo "INFO: No users file found. Creating default at $USERS_FILE"
  cat > "$USERS_FILE" <<'EOF'
users:
  - username: admin
    # Default password: changeme (BCrypt hash)
    password: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    roles:
      - ADMIN
      - EDITOR
      - USER
EOF
  echo "WARNING: Default admin credentials created (admin/changeme). Change immediately!"
fi

exec "$@"
