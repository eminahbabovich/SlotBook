import { execSync } from 'node:child_process'

// Gasi bazu i Mailpit iz compose.yml. Volumena nema (baza je u tmpfs), pa nema ni šta brisati.
export default function ugasi() {
	execSync('docker compose -f e2e/compose.yml down', { cwd: new URL('..', import.meta.url), stdio: 'inherit' })
}
