#!/usr/bin/env bash
# Lance le launcher deja compile.
set -e

if [ ! -f "target/MiniCube.jar" ]; then
    echo "[ERREUR] target/MiniCube.jar est introuvable."
    echo "Lancez d'abord ./build.sh pour compiler le projet."
    exit 1
fi

exec java -jar "target/MiniCube.jar"
