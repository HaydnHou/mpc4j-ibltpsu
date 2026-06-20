#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

OUT_DIR="${OUT_DIR:-${REPO_ROOT}/results/mp_sogs_mpsu}"
mkdir -p "${OUT_DIR}"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)_$$"
OUT_FILE="${OUT_FILE:-${OUT_DIR}/mp_sogs_mpsu_${TIMESTAMP}.csv}"
CP_FILE="${OUT_DIR}/mp_sogs_mpsu_classpath.txt"
LOG4J_FILE="${OUT_DIR}/mp_sogs_mpsu_log4j.properties"

MAVEN_SOURCE="${MAVEN_SOURCE:-21}"
MAVEN_TARGET="${MAVEN_TARGET:-21}"

echo "[mp-sogs] compiling local modules..."
mvn -q -pl mpc4j-s2pc-pso -am \
  -Dmaven.compiler.source="${MAVEN_SOURCE}" \
  -Dmaven.compiler.target="${MAVEN_TARGET}" \
  -DskipTests compile

echo "[mp-sogs] building dependency classpath..."
mvn -q -pl mpc4j-s2pc-pso dependency:build-classpath -Dmdep.outputFile="${CP_FILE}"

LOCAL_CP=(
  "mpc4j-s2pc-pso/target/classes"
  "mpc4j-s3pc-abb3/target/classes"
  "mpc4j-s2pc-opf/target/classes"
  "mpc4j-s2pc-aby/target/classes"
  "mpc4j-s2pc-pcg/target/classes"
  "mpc4j-common-circuit/target/classes"
  "mpc4j-common-rpc/target/classes"
  "mpc4j-common-structure/target/classes"
  "mpc4j-common-sampler/target/classes"
  "mpc4j-common-tool/target/classes"
)

LOCAL_CP_JOINED="$(IFS=:; echo "${LOCAL_CP[*]}")"
FULL_CP="${LOCAL_CP_JOINED}:$(cat "${CP_FILE}")"

if [[ ! -f "${LOG4J_FILE}" ]]; then
  cat > "${LOG4J_FILE}" <<'LOG4J'
log4j.rootLogger=ERROR, stdout
log4j.appender.stdout=org.apache.log4j.ConsoleAppender
log4j.appender.stdout.Target=System.err
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
log4j.appender.stdout.layout.ConversionPattern=%d %-5p %c - %m%n
LOG4J
fi

echo "[mp-sogs] running benchmark -> ${OUT_FILE}"
java ${JAVA_OPTS:-} --add-modules jdk.incubator.vector \
  -Dlog4j.configuration="file:${LOG4J_FILE}" \
  -cp "${FULL_CP}" \
  edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuBenchmarkMain "$@" \
  | tee "${OUT_FILE}"
