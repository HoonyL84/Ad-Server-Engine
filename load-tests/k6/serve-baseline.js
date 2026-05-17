import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const TARGET_VUS = Number(__ENV.TARGET_VUS || '10');
const RAMP_UP_DURATION = __ENV.RAMP_UP_DURATION || '30s';
const HOLD_DURATION = __ENV.HOLD_DURATION || '1m';
const RAMP_DOWN_DURATION = __ENV.RAMP_DOWN_DURATION || '30s';

export const options = {
  scenarios: {
    baseline: {
      executor: 'ramping-vus',
      stages: [
        { duration: RAMP_UP_DURATION, target: TARGET_VUS },
        { duration: HOLD_DURATION, target: TARGET_VUS },
        { duration: RAMP_DOWN_DURATION, target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<200', 'p(99)<500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SLOT_IDS = (__ENV.SLOT_IDS || __ENV.SLOT_ID || 'fashion,local,home')
  .split(',')
  .map((slotId) => slotId.trim())
  .filter((slotId) => slotId.length > 0);
const USER_ID_START = Number(__ENV.USER_ID_START || '1');
const USER_ID_RANGE = Number(__ENV.USER_ID_RANGE || '100000');
const ENABLE_EVENTS = (__ENV.ENABLE_EVENTS || 'false').toLowerCase() === 'true';
const CLICK_RATE = Number(__ENV.CLICK_RATE || '0.02');

const fallbackReasonCount = new Counter('ad_fallback_reason_total');
const fallbackRate = new Rate('ad_fallback_rate');
const adDeliveredRate = new Rate('ad_delivered_rate');
const personalizedServedRate = new Rate('ad_personalized_served_rate');
const budgetExhaustedRate = new Rate('ad_budget_exhausted_rate');
const candidateTimeoutRate = new Rate('ad_candidate_timeout_rate');
const candidateErrorRate = new Rate('ad_candidate_error_rate');
const dmpTimeoutRate = new Rate('ad_dmp_timeout_rate');
const dmpErrorRate = new Rate('ad_dmp_error_rate');
const noCandidateRate = new Rate('ad_no_candidate_rate');
const targetNotMatchedRate = new Rate('ad_target_not_matched_rate');
const profileNotFoundRate = new Rate('ad_profile_not_found_rate');
const impressionEventRate = new Rate('ad_impression_event_rate');
const clickEventRate = new Rate('ad_click_event_rate');

export default function () {
  const userId = String(USER_ID_START + (__VU + __ITER) % USER_ID_RANGE);

  for (const slotId of SLOT_IDS) {
    const url = `${BASE_URL}/api/v1/ads/serve?userId=${userId}&slotId=${slotId}`;

    const response = http.get(url, {
      tags: {
        name: 'GET /api/v1/ads/serve',
        endpoint: 'serve',
        slotId,
      },
    });

    const ok = check(response, {
      'status is 200': (res) => res.status === 200,
      'has fallbackReason': (res) => {
        try {
          return JSON.parse(res.body).fallbackReason !== undefined;
        } catch (_) {
          return false;
        }
      },
    });

    if (ok && response.status === 200) {
      const body = JSON.parse(response.body);
      const reason = body.fallbackReason || 'NONE';

      fallbackRate.add(body.fallback === true, { slotId });
      adDeliveredRate.add(body.adId !== null && body.adId !== undefined, { slotId });
      personalizedServedRate.add(body.fallback !== true, { slotId });
      fallbackReasonCount.add(1, { reason, slotId });
      budgetExhaustedRate.add(reason === 'BUDGET_EXHAUSTED', { slotId });
      candidateTimeoutRate.add(reason === 'CANDIDATE_TIMEOUT', { slotId });
      candidateErrorRate.add(reason === 'CANDIDATE_ERROR', { slotId });
      dmpTimeoutRate.add(reason === 'DMP_TIMEOUT', { slotId });
      dmpErrorRate.add(reason === 'DMP_ERROR', { slotId });
      noCandidateRate.add(reason === 'NO_CANDIDATE', { slotId });
      targetNotMatchedRate.add(reason === 'TARGET_NOT_MATCHED', { slotId });
      profileNotFoundRate.add(reason === 'PROFILE_NOT_FOUND', { slotId });

      if (ENABLE_EVENTS && body.adId !== null && body.adId !== undefined) {
        const impressionResponse = http.get(body.impressionUrl, {
          tags: {
            name: 'GET /api/v1/ad-events/impressions',
            endpoint: 'impression',
            slotId,
          },
        });
        impressionEventRate.add(impressionResponse.status === 200, { slotId });

        if (Math.random() < CLICK_RATE) {
          const clickResponse = http.get(body.clickTrackingUrl, {
            redirects: 0,
            tags: {
              name: 'GET /api/v1/ad-events/clicks',
              endpoint: 'click',
              slotId,
            },
          });
          clickEventRate.add(clickResponse.status >= 200 && clickResponse.status < 400, { slotId });
        }
      }
    }
  }

  sleep(0.1);
}
