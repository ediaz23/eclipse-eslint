#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$PROJECT_DIR/eclipse-plugin-signing.properties"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "Falta el archivo de configuración: $CONFIG_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$CONFIG_FILE"

SIGNING_HOME_DIR="${HOME}/.eclipse-plugin-signing"
KEYSTORE_PATH="${SIGNING_HOME_DIR}/${SIGNING_KEYSTORE_NAME}"

mkdir -p "$SIGNING_HOME_DIR"

if [[ -f "$KEYSTORE_PATH" ]]; then
  echo "El keystore ya existe: $KEYSTORE_PATH"
  exit 0
fi

keytool -genkeypair \
  -alias "$SIGNING_ALIAS" \
  -keyalg "$SIGNING_KEYALG" \
  -keysize "$SIGNING_KEYSIZE" \
  -sigalg "$SIGNING_SIGALG" \
  -validity "$SIGNING_VALIDITY_DAYS" \
  -keystore "$KEYSTORE_PATH" \
  -storetype PKCS12 \
  -storepass "$SIGNING_STOREPASS" \
  -keypass "$SIGNING_KEYPASS" \
  -dname "$SIGNING_DNAME"

echo "Keystore creado en: $KEYSTORE_PATH"
