import http from 'k6/http';
import { check } from 'k6';

const targetRate = Number(__ENV.RATE || 100);
const duration = __ENV.DURATION || '1m';
export const options = {
  scenarios: { order_load: { executor: 'constant-arrival-rate', rate: targetRate, timeUnit: '1s', duration, preAllocatedVUs: Number(__ENV.PRE_VUS || 100), maxVUs: Number(__ENV.MAX_VUS || 2000) } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<1000', 'p(99)<2000'] },
};

export default function () {
  const id = `${__VU}-${__ITER}-${Date.now()}-${Math.random()}`;
  const payload = JSON.stringify({ customerId: `load-${__VU}`, productId: Number(__ENV.PRODUCT_ID || 1), quantity: 1, idempotencyKey: `k6-${id}` });
  const response = http.post(`${__ENV.BASE_URL || 'http://localhost:8080'}/api/orders`, payload, { headers: { 'Content-Type': 'application/json', 'X-API-Key': `load-client-${__VU % Number(__ENV.CLIENT_KEYS || 200)}`, 'Idempotency-Key': `k6-${id}` }, tags: { endpoint: 'create-order' } });
  check(response, { 'order accepted': r => r.status === 202 });
}
