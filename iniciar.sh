#!/bin/sh
cd "$(dirname "$0")" || exit 1
exec java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -jar dist/ra1-act1.jar "$@"
