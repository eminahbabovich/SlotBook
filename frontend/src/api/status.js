import { apiFetch, provjeriPolja } from './apiFetch.js'

// StatusResponse(String aplikacija, Instant vrijeme) iz ba.slotbook.status.
export async function getStatus() {
	const odgovor = provjeriPolja(await apiFetch('/api/status'), ['aplikacija', 'vrijeme'], '/api/status')
	return { aplikacija: odgovor.aplikacija, vrijeme: odgovor.vrijeme }
}
