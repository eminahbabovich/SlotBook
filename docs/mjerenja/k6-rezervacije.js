// Load test za SlotBook (vidi docs/PERFORMANSE.md). Tri scenarija istovremeno:
// kalendar slobodnih termina, rezervacije na različite termine i 20 članova na istom terminu.
// Podaci su iz k6-podaci.sql. Pokretanje:
//   docker run --rm -i grafana/k6:2.3.0 run - < docs/mjerenja/k6-rezervacije.js
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const LOZINKA = 'opterecenje-bjelave-2035';
const CLANOVA = 100;
const TERETANA = '00000000-0000-0000-0000-00000000b001';
const SALA_ZA_SUDAR = '00000000-0000-0000-0000-00000000c001';
const SAT = 3600 * 1000;
const DAN = 24 * SAT;

// 409 je očekivan odgovor na sudar, a ne greška servera
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

const rezervacije201 = new Counter('rezervacije_201');
const rezervacije409 = new Counter('rezervacije_409');
const sudar201 = new Counter('sudar_201');
const sudar409 = new Counter('sudar_409');

export const options = {
	setupTimeout: '120s',
	scenarios: {
		slobodni_termini: {
			executor: 'constant-arrival-rate', exec: 'slobodniTermini',
			rate: 50, timeUnit: '1s', duration: '60s', preAllocatedVUs: 50, maxVUs: 100,
		},
		rezervacije: {
			executor: 'constant-arrival-rate', exec: 'rezervacija',
			rate: 10, timeUnit: '1s', duration: '60s', preAllocatedVUs: 20, maxVUs: 50,
		},
		sudar: {
			executor: 'per-vu-iterations', exec: 'sudar',
			vus: 20, iterations: 30, startTime: '10s', maxDuration: '50s',
		},
	},
	thresholds: {
		http_req_failed: ['rate<0.01'],
		'http_req_duration{scenario:slobodni_termini}': ['p(95)<500'],
		'http_req_duration{scenario:rezervacije}': ['p(95)<500'],
		'http_req_duration{scenario:sudar}': ['p(95)<500'],
	},
};

function json(token) {
	return { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } };
}

export function setup() {
	const prijave = [];
	for (let i = 1; i <= CLANOVA; i++) {
		prijave.push(['POST', `${BASE}/api/auth/login`,
			JSON.stringify({ email: `clan${i}.k6@example.com`, password: LOZINKA }),
			{ headers: { 'Content-Type': 'application/json' } }]);
	}
	const tokeni = http.batch(prijave).map((odgovor) => {
		if (odgovor.status !== 200) {
			throw new Error(`Prijava nije uspjela: ${odgovor.status} ${odgovor.body}`);
		}
		return odgovor.json('accessToken');
	});
	const resursi = http.get(`${BASE}/api/organizations/${TERETANA}/resources?size=100`, json(tokeni[0]))
		.json('content')
		.filter((r) => r.id !== SALA_ZA_SUDAR)
		.map((r) => r.id);
	if (resursi.length !== 20) {
		throw new Error(`Očekivano 20 sala, stiglo ${resursi.length}`);
	}
	return { tokeni, resursi };
}

// k-ti radni termin od ponedjeljka 8.1.2035. u 08:00 po Sarajevu (07:00 UTC). Samo zimi, prije 25.3.,
// da pomak ne zavisi od ljetnog vremena: 11 sedmica x 5 dana x 12 sati.
function termin(k) {
	const dan = Math.floor(k / 12);
	const sedmica = Math.floor(dan / 5);
	return new Date(Date.UTC(2035, 0, 8, 7) + sedmica * 7 * DAN + (dan % 5) * DAN + (k % 12) * SAT).toISOString();
}

function nasumicno(niz) {
	return niz[Math.floor(Math.random() * niz.length)];
}

export function slobodniTermini(podaci) {
	const sedmica = Math.floor(Math.random() * 11);
	const od = new Date(Date.UTC(2035, 0, 8) + sedmica * 7 * DAN).toISOString().slice(0, 10);
	const doo = new Date(Date.UTC(2035, 0, 15) + sedmica * 7 * DAN).toISOString().slice(0, 10);
	const odgovor = http.get(`${BASE}/api/resources/${nasumicno(podaci.resursi)}/slots?from=${od}&to=${doo}`,
		json(nasumicno(podaci.tokeni)));
	check(odgovor, { 'slobodni termini 200': (r) => r.status === 200 });
}

// Svaka iteracija ima svoj termin (sala i sat iz broja iteracije), pa sudara ovdje ne bi smjelo biti
export function rezervacija(podaci) {
	const i = exec.scenario.iterationInTest;
	const odgovor = http.post(`${BASE}/api/resources/${podaci.resursi[i % 20]}/bookings`,
		JSON.stringify({ startTime: termin(Math.floor(i / 20)) }), json(nasumicno(podaci.tokeni)));
	(odgovor.status === 201 ? rezervacije201 : rezervacije409).add(1);
	check(odgovor, { 'rezervacija 201': (r) => r.status === 201 });
}

// Svih 20 VU-ova traži isti termin u svojoj n-toj iteraciji. Po terminu prođe tačno jedan.
export function sudar(podaci) {
	const n = exec.vu.iterationInScenario;
	const odgovor = http.post(`${BASE}/api/resources/${SALA_ZA_SUDAR}/bookings`,
		JSON.stringify({ startTime: termin(n) }), json(podaci.tokeni[exec.vu.idInTest % CLANOVA]));
	(odgovor.status === 201 ? sudar201 : sudar409).add(1);
	check(odgovor, { 'sudar 201 ili 409': (r) => r.status === 201 || r.status === 409 });
}
