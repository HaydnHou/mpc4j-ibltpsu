#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

PARTY_NUM="${PARTY_NUM:-5}"
LOG_SET_SIZE="${LOG_SET_SIZE:-10}"
RTT_MS="${RTT_MS:-0}"
TRIALS="${TRIALS:-2}"
SECURE_PEEL_TYPE="${SECURE_PEEL_TYPE:-SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_DOUBLE_SHARE}"
SSM_OPENING_MODE="${SSM_OPENING_MODE:-BALANCED_TWO_PHASE}"
NETWORK_BANDWIDTH_MBPS="${NETWORK_BANDWIDTH_MBPS:-0}"
DOCKER_IMAGE="${DOCKER_IMAGE:-mpc4j-netem:jre21}"
RESULT_ROOT="${RESULT_ROOT:-${REPO_ROOT}/results/mp_sogs_mpsu/netty_rtt}"
RUN_TAG="${RUN_TAG:-p${PARTY_NUM}_n${LOG_SET_SIZE}_rtt${RTT_MS}_${SECURE_PEEL_TYPE}}"
RUN_DIR="${RESULT_ROOT}/${RUN_TAG}"
CONF_FILE="${RUN_DIR}/conf.conf"
CP_FILE="${RESULT_ROOT}/classpath.txt"
NETWORK="ssm-rtt-${$}"
ONE_WAY_MS="$(awk -v rtt="${RTT_MS}" 'BEGIN { printf "%.3f", rtt / 2.0 }')"

if [[ "${PARTY_NUM}" -ne 5 ]]; then
  echo "This RTT gate currently targets the matched five-party SSM comparison." >&2
  exit 1
fi
if ! [[ "${RTT_MS}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "RTT_MS must be a non-negative number: ${RTT_MS}" >&2
  exit 1
fi

mkdir -p "${RUN_DIR}/out"

if [[ "${SKIP_COMPILE:-false}" != "true" ]]; then
  mvn -q -pl mpc4j-s2pc-pso -am \
    -Dmaven.compiler.source=21 \
    -Dmaven.compiler.target=21 \
    -DskipTests compile
  mvn -q -pl mpc4j-s2pc-pso dependency:build-classpath \
    -Dmdep.outputFile="${CP_FILE}"
fi
if [[ ! -s "${CP_FILE}" ]]; then
  echo "Missing dependency classpath: ${CP_FILE}" >&2
  exit 1
fi

LOCAL_CP=(
  "${REPO_ROOT}/mpc4j-s2pc-pso/target/classes"
  "${REPO_ROOT}/mpc4j-s3pc-abb3/target/classes"
  "${REPO_ROOT}/mpc4j-s2pc-opf/target/classes"
  "${REPO_ROOT}/mpc4j-s2pc-aby/target/classes"
  "${REPO_ROOT}/mpc4j-s2pc-pcg/target/classes"
  "${REPO_ROOT}/mpc4j-common-circuit/target/classes"
  "${REPO_ROOT}/mpc4j-common-rpc/target/classes"
  "${REPO_ROOT}/mpc4j-common-structure/target/classes"
  "${REPO_ROOT}/mpc4j-common-sampler/target/classes"
  "${REPO_ROOT}/mpc4j-common-tool/target/classes"
)
LOCAL_CP_JOINED="$(IFS=:; echo "${LOCAL_CP[*]}")"
FULL_CP="${LOCAL_CP_JOINED}:$(cat "${CP_FILE}")"

cat > "${CONF_FILE}" <<CONF
first_name = first
first_ip = first
first_port = 9401
second_name = second
second_ip = second
second_port = 9402
third_name = third
third_ip = third
third_port = 9403
fourth_name = fourth
fourth_ip = fourth
fourth_port = 9404
fifth_name = fifth
fifth_ip = fifth
fifth_port = 9405

save_path = ${RUN_DIR}/out
append_string = ${RUN_TAG}
pto_type = MP_SOGS_MPSU
party_num = 5
secure_peel_type = ${SECURE_PEEL_TYPE}
label_encoding = EXACT_QUOTIENT
ssm_opening_mode = ${SSM_OPENING_MODE}
network_rtt_ms = ${RTT_MS}
network_bandwidth_mbps = ${NETWORK_BANDWIDTH_MBPS}

log_set_size = ${LOG_SET_SIZE}
overlap = ${OVERLAP:-0.5}
alpha = ${ALPHA:-1.4}
k = ${K:-3}
hash_seed = ${HASH_SEED:-20260620}
max_peel_rounds = ${MAX_PEEL_ROUNDS:-10000}
max_hash_seed_retries = ${MAX_HASH_SEED_RETRIES:-4}
max_batch_cells = ${MAX_BATCH_CELLS:-1048576}
trials = ${TRIALS}
parallel = ${PARALLEL:-false}
task_id = ${TASK_ID:-4000000}
write_union = false
malicious = false
robust_rpc = false
CONF

cleanup() {
  for party in first second third fourth fifth; do
    docker rm -f "${NETWORK}-${party}" >/dev/null 2>&1 || true
  done
  docker network rm "${NETWORK}" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker network create "${NETWORK}" >/dev/null
PIDS=()
for party in first second third fourth fifth; do
  log_file="${RUN_DIR}/${party}.log"
  delay_command=":"
  if awk -v delay="${ONE_WAY_MS}" 'BEGIN { exit !(delay > 0) }'; then
    delay_command="tc qdisc add dev eth0 root netem delay ${ONE_WAY_MS}ms"
  fi
  docker run --rm \
    --name "${NETWORK}-${party}" \
    --hostname "${party}" \
    --network "${NETWORK}" \
    --network-alias "${party}" \
    --cap-add NET_ADMIN \
    -v "${REPO_ROOT}:${REPO_ROOT}" \
    -v "${HOME}/.m2:${HOME}/.m2:ro" \
    "${DOCKER_IMAGE}" \
    sh -lc "${delay_command}; exec java --add-modules jdk.incubator.vector -cp '${FULL_CP}' edu.alibaba.mpc4j.s2pc.pso.main.PsoMain '${CONF_FILE}' '${party}'" \
    > "${log_file}" 2>&1 &
  PIDS+=("$!")
done

status=0
for pid in "${PIDS[@]}"; do
  if ! wait "${pid}"; then
    status=1
  fi
done
if [[ "${status}" -ne 0 ]]; then
  echo "One or more Netty parties failed. Inspect ${RUN_DIR}/*.log" >&2
  exit "${status}"
fi

awk -F '\t' '
  FNR == 1 { next }
  {
    trial = $1;
    if ($13 != "true") {
      success[trial] = 0;
    } else if (!(trial in success)) {
      success[trial] = 1;
    }
    if ($21 > maxPto[trial]) maxPto[trial] = $21;
    if ($24 > maxSend[trial]) maxSend[trial] = $24;
    rounds[trial] = $14;
    calls[trial] = $15;
  }
  END {
    print "trial\tsuccess\tmax_pto_ms\tmax_send_bytes\trounds\tupeel_calls";
    for (trial = 0; trial < 1000; trial++) {
      if (trial in maxPto) {
        printf "%d\t%d\t%d\t%d\t%d\t%d\n", trial, success[trial], maxPto[trial], maxSend[trial], rounds[trial], calls[trial];
      }
    }
  }
' "${RUN_DIR}"/out/*.output | tee "${RUN_DIR}/summary.tsv"

echo "run_dir=${RUN_DIR}"
echo "configured_rtt_ms=${RTT_MS}"
echo "one_way_netem_ms=${ONE_WAY_MS}"
