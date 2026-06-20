#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

OUT_DIR="${OUT_DIR:-${REPO_ROOT}/results/mp_sogs_mpsu/netty}"
mkdir -p "${OUT_DIR}"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)_$$"
RUN_DIR="${RUN_DIR:-${OUT_DIR}/${TIMESTAMP}}"
mkdir -p "${RUN_DIR}"

CP_FILE="${RUN_DIR}/mp_sogs_mpsu_classpath.txt"
LOG4J_FILE="${RUN_DIR}/mp_sogs_mpsu_log4j.properties"
CONF_FILE="${CONF_FILE:-${RUN_DIR}/conf_mp_sogs_mpsu.conf}"

MAVEN_SOURCE="${MAVEN_SOURCE:-21}"
MAVEN_TARGET="${MAVEN_TARGET:-21}"

echo "[mp-sogs-netty] compiling local modules..."
mvn -q -pl mpc4j-s2pc-pso -am \
  -Dmaven.compiler.source="${MAVEN_SOURCE}" \
  -Dmaven.compiler.target="${MAVEN_TARGET}" \
  -DskipTests compile

echo "[mp-sogs-netty] building dependency classpath..."
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
log4j.rootLogger=INFO, stdout
log4j.appender.stdout=org.apache.log4j.ConsoleAppender
log4j.appender.stdout.Target=System.err
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
log4j.appender.stdout.layout.ConversionPattern=%d %-5p %c - %m%n
LOG4J
fi

if [[ ! -f "${CONF_FILE}" ]]; then
  FIRST_PORT="${FIRST_PORT:-9401}"
  SECOND_PORT="${SECOND_PORT:-9402}"
  THIRD_PORT="${THIRD_PORT:-9403}"
  SAVE_PATH="${SAVE_PATH:-${RUN_DIR}/out}"
  mkdir -p "${SAVE_PATH}"
  cat > "${CONF_FILE}" <<CONF
first_name = first
first_ip = 127.0.0.1
first_port = ${FIRST_PORT}

second_name = second
second_ip = 127.0.0.1
second_port = ${SECOND_PORT}

third_name = third
third_ip = 127.0.0.1
third_port = ${THIRD_PORT}

save_path = ${SAVE_PATH}
append_string = ${APPEND_STRING:-netty}
pto_type = MP_SOGS_MPSU

log_set_size = ${LOG_SET_SIZE:-8}
overlap = ${OVERLAP:-0.5}
alpha = ${ALPHA:-1.4}
k = ${K:-3}
hash_seed = ${HASH_SEED:-20260620}
max_peel_rounds = ${MAX_PEEL_ROUNDS:-10000}
max_hash_seed_retries = ${MAX_HASH_SEED_RETRIES:-4}
max_batch_cells = ${MAX_BATCH_CELLS:-1048576}
cr_buffer_byte_size = ${CR_BUFFER_BYTE_SIZE:-16777216}
trials = ${TRIALS:-1}
parallel = ${PARALLEL:-true}
task_id = ${TASK_ID:-1000000}
write_union = ${WRITE_UNION:-false}

malicious = ${MALICIOUS:-false}
use_mac = ${USE_MAC:-false}
mt_sim_mode = ${MT_SIM_MODE:-false}
CONF
fi

echo "[mp-sogs-netty] config: ${CONF_FILE}"
echo "[mp-sogs-netty] run dir: ${RUN_DIR}"

PIDS=()
for PARTY in first second third; do
  LOG_FILE="${RUN_DIR}/${PARTY}.log"
  echo "[mp-sogs-netty] starting ${PARTY}, log -> ${LOG_FILE}"
  java --add-modules jdk.incubator.vector \
    -Dlog4j.configuration="file:${LOG4J_FILE}" \
    -cp "${FULL_CP}" \
    edu.alibaba.mpc4j.s2pc.pso.main.PsoMain "${CONF_FILE}" "${PARTY}" \
    > "${LOG_FILE}" 2>&1 &
  PIDS+=("$!")
done

STATUS=0
for PID in "${PIDS[@]}"; do
  if ! wait "${PID}"; then
    STATUS=1
  fi
done

if [[ "${STATUS}" -ne 0 ]]; then
  echo "[mp-sogs-netty] one or more parties failed. Logs are in ${RUN_DIR}" >&2
  exit "${STATUS}"
fi

echo "[mp-sogs-netty] finished. Outputs:"
find "${RUN_DIR}" -type f \( -name '*.output' -o -name '*.log' -o -name '*.conf' \) -print | sort
