#!/bin/sh
# Builds the shaded jar on first use, then runs the MCP server over stdio.
#
# Maven logs to stdout, and stdout is the MCP protocol channel — so the build's output is
# redirected to stderr, where MCP clients surface it as server logs.
set -e

DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$DIR/target/catalog-mcp-server.jar"

if [ ! -f "$JAR" ]; then
  echo "[catalog-mcp] building $JAR" >&2
  "$DIR/../../mvnw" -B -q -f "$DIR/pom.xml" -DskipTests package >&2
fi

exec java -jar "$JAR"
