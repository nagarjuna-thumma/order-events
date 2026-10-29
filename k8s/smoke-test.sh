#!/usr/bin/env bash
# Places an order through the order-service in the cluster and waits for the
# fulfilment-service to create its shipment. Used by CI after deploying to kind.
set -euo pipefail

NS=order-events
kubectl -n "$NS" port-forward svc/order-service 18080:80 >/dev/null 2>&1 &
kubectl -n "$NS" port-forward svc/fulfilment-service 18081:80 >/dev/null 2>&1 &
trap 'kill $(jobs -p) 2>/dev/null' EXIT

# port-forward takes a moment to start listening. Wait for both instead of guessing with a sleep.
for port in 18080 18081; do
  for _ in $(seq 30); do
    curl -sf "localhost:$port/actuator/health/readiness" >/dev/null && break
    sleep 1
  done
done

order_id=$(curl -sf --retry 5 --retry-connrefused --retry-delay 2 -X POST localhost:18080/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"smoke","sku":"SKU-1","quantity":1,"totalAmount":9.99}' | jq -r .id)
echo "placed order $order_id"

for _ in $(seq 30); do
  if curl -sf "localhost:18081/shipments/$order_id" >/dev/null; then
    echo "shipment created for $order_id"
    exit 0
  fi
  sleep 2
done

echo "no shipment for $order_id after 60s" >&2
# ::error:: lines show up as annotations on the CI run, which are easier to find than the raw log.
echo "::error::no shipment for order $order_id after 60s"
kubectl -n "$NS" logs deploy/fulfilment-service --tail=200 | grep -iE "warn|error|partitions assigned" | tail -5 | sed 's/^/::error::fulfilment: /' || true
kubectl -n "$NS" logs deploy/order-service --tail=50 >&2
kubectl -n "$NS" logs deploy/fulfilment-service --tail=50 >&2
exit 1
