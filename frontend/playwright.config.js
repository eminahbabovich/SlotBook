import { randomBytes } from 'node:crypto'
import { defineConfig } from '@playwright/test'

// cmd bez .\ ne traži mvnw.cmd u trenutnom folderu
const mvnw = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw'

// Glavni tok ide kroz pravi backend i pravu bazu, ne kroz lažni backend iz Vitest testova.
// Sve se diže i gasi samo: npm run e2e
export default defineConfig({
	testDir: 'e2e',
	// .e2e.js, a ne .spec.js, da ih Vitest ne pokupi kao svoje
	testMatch: '*.e2e.js',
	// Testovi dijele jednu organizaciju i jedan resurs, pa idu jedan za drugim
	workers: 1,
	// beforeAll čeka mail s pozivnicom, a NotificationJob šalje svakih 30 s
	timeout: 90_000,
	globalTeardown: './e2e/ugasi.js',
	use: {
		baseURL: 'http://localhost:5173',
		// Edge je već na računaru, pa Playwright ne skida svoj browser
		channel: 'msedge',
		// Browser u drugoj zoni od organizacije: termin od 08:00 u Sarajevu mora i dalje pisati 08:00
		timezoneId: 'America/New_York',
		trace: 'retain-on-failure',
	},
	webServer: [
		{
			// Baza se diže u istoj komandi kao backend, jer Playwright pali webServer prije globalSetup-a
			command: `docker compose -f ../frontend/e2e/compose.yml up -d --wait && ${mvnw} -q spring-boot:run`,
			cwd: '../backend',
			url: 'http://localhost:8080/actuator/health',
			timeout: 180_000,
			// Backend koji već radi na 8080 gleda razvojnu bazu, pa je bolje da test odmah stane
			reuseExistingServer: false,
			env: {
				POSTGRES_PORT: '5434',
				POSTGRES_DB: 'slotbook_e2e',
				POSTGRES_USER: 'slotbook_e2e',
				POSTGRES_PASSWORD: 'slotbook_e2e',
				// Nov ključ za svako pokretanje: tokeni žive koliko i test, a u repou nema ničeg što liči na tajnu
				JWT_SECRET: randomBytes(48).toString('base64'),
				MAIL_PORT: '1026',
				APP_URL: 'http://localhost:5173',
			},
		},
		{
			command: 'npm run dev -- --port 5173 --strictPort',
			url: 'http://localhost:5173',
			timeout: 60_000,
			reuseExistingServer: false,
		},
	],
})
