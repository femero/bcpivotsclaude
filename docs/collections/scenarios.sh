#!/usr/bin/env bash
# Publishes Avro test scenarios to the LOCAL docker-compose stack, one group per use case
# (docs/casos-de-uso.md). Requires: `docker compose up -d --wait`, topics + schemas registered and
# the service running. Uses kafka-avro-console-producer from the schema-registry container.
#
# Usage:  scenarios.sh [cu01|cu02|cu04|cu05|cu06|all]      (default: all)
# Env:    RUN=DEMO (reference prefix; change it to avoid duplicates on re-runs)
#         BASE_URL=http://localhost:8080
set -u
export MSYS_NO_PATHCONV=1

RUN="${RUN:-DEMO}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
SR=payment-reconciliation-local-schema-registry-1
KAFKA=payment-reconciliation-local-kafka-1
AVRO_DIR="$(cd "$(dirname "$0")/../../src/main/avro" && pwd)"
# cd first: a Windows jq does not understand /c/... paths.
PAYMENT_SCHEMA="$(cd "$AVRO_DIR" && jq -c . PaymentExecuted.avsc)"
CORE_SCHEMA="$(cd "$AVRO_DIR" && jq -c . CoreTransactionReported.avsc)"
NOW="$(date +%s)000"

# "100.00" -> JSON escape of the big-endian two's-complement unscaled value (Avro decimal(19,2)).
decimal_bytes() {
  local cents hex out="" i
  cents=$(awk -v a="$1" 'BEGIN{printf "%d", a*100+0.5}')
  hex=$(printf '%x' "$cents")
  [ $(( ${#hex} % 2 )) -eq 1 ] && hex="0$hex"
  [ $(( 16#${hex:0:2} )) -ge 128 ] && hex="00$hex"
  for ((i = 0; i < ${#hex}; i += 2)); do out+="\\u00${hex:i:2}"; done
  printf '%s' "$out"
}

# pay <reference> <eventId> <amount> <currency> [account]
pay() {
  printf '{"eventId":"%s","operationReference":"%s","accountNumber":"%s","amount":"%s","currency":"%s","occurredAt":%s}\n' \
    "$2" "$1" "${5-00110022003300}" "$(decimal_bytes "$3")" "$4" "$NOW" |
    docker exec -i "$SR" kafka-avro-console-producer --bootstrap-server kafka:29092 --topic payments-in \
      --property schema.registry.url=http://localhost:8081 --property "value.schema=$PAYMENT_SCHEMA" >/dev/null 2>&1
}

# core <reference> <eventId> <amount> <currency>
core() {
  printf '{"eventId":"%s","operationReference":"%s","amount":"%s","currency":"%s","occurredAt":%s}\n' \
    "$2" "$1" "$(decimal_bytes "$3")" "$4" "$NOW" |
    docker exec -i "$SR" kafka-avro-console-producer --bootstrap-server kafka:29092 --topic core-transactions-in \
      --property schema.registry.url=http://localhost:8081 --property "value.schema=$CORE_SCHEMA" >/dev/null 2>&1
}

show() { # show <reference>
  local body code
  body=$(curl -s -w '\n%{http_code}' "$BASE_URL/v1/reconciliations/$1")
  code=${body##*$'\n'}
  printf '  %-26s HTTP %s  %s\n' "$1" "$code" "$(printf '%s' "${body%$'\n'*}" | jq -rc 'if .paymentAmount then "\(.status) payment=\(.paymentAmount.amount) diff=\(.difference.amount // "-")" else .title // . end' 2>/dev/null)"
}

dlq_count() { # dlq_count <topic>
  docker exec "$KAFKA" kafka-get-offsets --bootstrap-server kafka:29092 --topic "$1" 2>/dev/null |
    awk -F: '{s+=$3} END{print s+0}'
}

wait_for_processing() { sleep "${1:-4}"; }

# CU-01 Register reported payment -> PENDING, nothing published
cu01() {
  echo "== CU-01 Registrar pago reportado"
  pay "$RUN-CU01-PENDING" "e-$RUN-1" 100.00 PEN
  wait_for_processing
  show "$RUN-CU01-PENDING"
}

# CU-02 (+ CU-07) Reconcile core transaction -> MATCHED / MISMATCHED, result published
cu02() {
  echo "== CU-02 Conciliar movimiento del core (y CU-07 Publicar resultado)"
  local cases=("MATCH:100.00:100.00" "LIMIT:100.00:99.00" "OVER:100.00:98.99" "CHARGED-MORE:100.00:100.50")
  local c ref p k
  for c in "${cases[@]}"; do IFS=: read -r ref p k <<<"$c"; pay "$RUN-CU02-$ref" "e-$RUN-p-$ref" "$p" PEN; done
  wait_for_processing
  for c in "${cases[@]}"; do IFS=: read -r ref p k <<<"$c"; core "$RUN-CU02-$ref" "e-$RUN-c-$ref" "$k" PEN; done
  wait_for_processing
  for c in "${cases[@]}"; do IFS=: read -r ref p k <<<"$c"; show "$RUN-CU02-$ref"; done
  echo "  -- 4a: repeated core movement over a resolved reconciliation is discarded (WARN, no change)"
  core "$RUN-CU02-MATCH" "e-$RUN-c-again" 50.00 PEN
  wait_for_processing 3
  show "$RUN-CU02-MATCH"
}

# CU-04 Discard duplicates -> a single effect even with a different event id
cu04() {
  echo "== CU-04 Descartar duplicados"
  pay "$RUN-CU04-DUP" "e-$RUN-d1" 100.00 PEN
  pay "$RUN-CU04-DUP" "e-$RUN-d2" 999.00 PEN
  wait_for_processing
  show "$RUN-CU04-DUP"
  echo "  (expected paymentAmount 100.00: the second payment was ignored)"
  docker exec payment-reconciliation-local-postgres-1 psql -U postgres -d reconciliation -Atc \
    "select count(*) || ' row(s) for $RUN-CU04-DUP' from reconciliations where operation_reference='$RUN-CU04-DUP'"
}

# CU-05 Reject invalid inputs -> nack, DLQ of the channel, nothing persisted
cu05() {
  echo "== CU-05 Rechazar entradas inválidas"
  local p0 c0
  p0=$(dlq_count payments-dlq); c0=$(dlq_count core-transactions-dlq)
  pay "$RUN-CU05-USD" "e-$RUN-i1" 100.00 USD
  pay "$RUN-CU05-ZERO" "e-$RUN-i2" 0.00 PEN
  pay "$RUN-CU05-NOACC" "e-$RUN-i3" 100.00 PEN ""
  pay "" "e-$RUN-i4" 100.00 PEN
  pay "$RUN-CU05-CORE" "e-$RUN-i5" 100.00 PEN
  wait_for_processing
  core "$RUN-CU05-CORE" "e-$RUN-i6" 100.00 USD
  core "$RUN-CU05-CORE" "e-$RUN-i7" 0.004 PEN
  wait_for_processing 6
  for r in USD ZERO NOACC; do show "$RUN-CU05-$r"; done
  show "$RUN-CU05-CORE"
  echo "  (CORE stays PENDING: both invalid core movements were rejected)"
  echo "  payments-dlq:          +$(( $(dlq_count payments-dlq) - p0 )) (expected +4)"
  echo "  core-transactions-dlq: +$(( $(dlq_count core-transactions-dlq) - c0 )) (expected +2)"
}

# CU-06 Discard orphan core transaction -> WARN, no row, no DLQ
cu06() {
  echo "== CU-06 Descartar movimiento huérfano"
  local c0; c0=$(dlq_count core-transactions-dlq)
  core "$RUN-CU06-ORPHAN" "e-$RUN-o1" 100.00 PEN
  wait_for_processing
  show "$RUN-CU06-ORPHAN"
  echo "  core-transactions-dlq: +$(( $(dlq_count core-transactions-dlq) - c0 )) (expected +0)"
}

case "${1:-all}" in
  cu01) cu01 ;;
  cu02) cu02 ;;
  cu04) cu04 ;;
  cu05) cu05 ;;
  cu06) cu06 ;;
  all) cu01; cu02; cu04; cu05; cu06 ;;
  *) echo "Unknown scenario: $1 (cu01|cu02|cu04|cu05|cu06|all)"; exit 1 ;;
esac
