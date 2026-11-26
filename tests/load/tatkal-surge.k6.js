import http from 'k6/http';
import { check, sleep } from 'k6';

// Grafana k6 10:00 AM Tatkal Surge Load Test
// Run: k6 run tests/load/tatkal-surge.k6.js
export const options = {
  stages: [
    { duration: '10s', target: 100 }, // Warmup ramp to 10 AM opening
    { duration: '20s', target: 300 }, // Peak Tatkal surge
    { duration: '10s', target: 0 },   // Cool-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const idemKey = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    userId: `k6-user-${__VU}`,
    trainNumber: '12951',
    sourceStation: 'BCT',
    destinationStation: 'NDLS',
    coachClass: 'AC_3',
    quotaCode: 'TQ',
    paymentMode: 'NORMAL_SUCCESS',
    bypassTatkalClock: true,
    passengers: [
      { name: `K6-Passenger-${__VU}`, age: 29, gender: 'MALE', preference: 'NONE' },
    ],
  });

  const res = http.post(`${BASE_URL}/api/v1/bookings`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idemKey,
    },
  });

  // 200 = booked, 400 = sold out/regret, 429 = per-user limit, 503 = graceful backpressure shed
  check(res, {
    'no 5xx server crashes': (r) => r.status !== 500,
    'valid tatkal response': (r) => [200, 400, 429, 503].includes(r.status),
  });

  sleep(0.05);
}
