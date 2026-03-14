#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$PROJECT_DIR/eclipse-plugin-signing.properties"
CREATE_SCRIPT="$PROJECT_DIR/create-keystore.sh"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "Falta el archivo de configuración: $CONFIG_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$CONFIG_FILE"

SIGNING_HOME_DIR="${HOME}/.eclipse-plugin-signing"
KEYSTORE_PATH="${SIGNING_HOME_DIR}/${SIGNING_KEYSTORE_NAME}"

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "Keystore no encontrado, creándolo..."
  "$CREATE_SCRIPT"
fi

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "No se pudo crear el keystore: $KEYSTORE_PATH" >&2
  exit 1
fi

for jar in "$PROJECT_DIR"/site/plugins/*.jar "$PROJECT_DIR"/site/features/*.jar; do
  [[ -f "$jar" ]] || continue
  echo "Firmando $jar"
  jarsigner \
    -keystore "$KEYSTORE_PATH" \
    -storetype PKCS12 \
    -storepass "$SIGNING_STOREPASS" \
    -sigalg "$SIGNING_SIGALG" \
    -digestalg SHA-256 \
    "$jar" \
    "$SIGNING_ALIAS"
done

echo "Firma completada."
