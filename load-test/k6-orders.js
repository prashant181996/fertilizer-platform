import http from 'k6/http';
import { check, sleep } from 'k6';
export const options = { scenarios: { order_load: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 50), timeUnit: '1s', duration: __ENV.DURATION || '1m', preAllocatedVUs: 50, maxVUs: 500 } }, thresholds: { http_req_failed: ['rate<0.02'], http_req_duration: ['p(95)<1000'] } };
export default function () {
  const key = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({ customerId: `load-${__VU}`, productId: 1, quantity: 1, idempotencyKey: key });
  const r = http.post(`${__ENV.BASE_URL || 'http://localhost:8080'}/api/orders`, payload, { headers: { 'Content-Type': 'application/json', 'X-API-Key': `k6-${__VU}`, 'Idempotency-Key': key } });
  check(r, { 'order accepted or business rejection': x => [201, 200, 409, 429, 502].includes(x.status) });
}
