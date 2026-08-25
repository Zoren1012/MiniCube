#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# MiniCube - compilation sous macOS et Linux
# Produit target/MiniCube.jar (jar auto-portant, JavaFX inclus).
# ---------------------------------------------------------------------------
set -e

echo
echo "=== MiniCube - compilation ==="
echo

if ! command -v mvn >/dev/null 2>&1; then
    echo "[ERREUR] Maven est introuvable dans le PATH."
    echo
    echo "  Debian/Ubuntu : sudo apt install maven"
    echo "  Fedora        : sudo dnf install maven"
    echo "  macOS         : brew install maven"
    echo
    exit 1
fi

if ! command -v javac >/dev/null 2>&1; then
    echo "[ERREUR] Aucun JDK detecte (javac introuvable)."
    echo "  Installez un JDK 21 : https://adoptium.net/temurin/releases/?version=21"
    echo
    exit 1
fi

JAVA_MAJOR=$(javac -version 2>&1 | sed 's/javac //' | cut -d. -f1)
if [ "$JAVA_MAJOR" -lt 21 ] 2>/dev/null; then
    echo "[ERREUR] Java 21 ou superieur est requis (detecte : $JAVA_MAJOR)."
    echo "  Installez un JDK 21 : https://adoptium.net/temurin/releases/?version=21"
    echo
    exit 1
fi

mvn clean package

echo
echo "=== Compilation terminee ==="
echo "  Jar genere : target/MiniCube.jar"
echo "  Lancement  : ./run.sh  (ou : java -jar target/MiniCube.jar)"
echo
