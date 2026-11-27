import { apiFetch, provjeriPolja } from './apiFetch.js'

// Razgovor se otvara tek uz prvu poruku, pa stranica koju niko ne pita ne pravi prazne sesije.
// ChatSessionResponse(id, createdAt)
export async function otvoriRazgovor(organizacijaId) {
	const putanja = `/api/organizations/${organizacijaId}/chat/sessions`
	const r = provjeriPolja(await apiFetch(putanja, { metoda: 'POST' }), ['id'], putanja)
	return r.id
}

const POLJA_PRIJEDLOGA = ['id', 'action', 'resourceId', 'resourceName', 'startTime', 'endTime']

// ChatReplyResponse(text, proposals). Prijedlog je karta: ništa nije rezervisano dok se ne potvrdi.
export async function posaljiPoruku(razgovorId, tekst) {
	const putanja = `/api/chat/sessions/${razgovorId}/messages`
	const r = provjeriPolja(await apiFetch(putanja, { metoda: 'POST', tijelo: { text: tekst } }), ['text', 'proposals'], putanja)
	return {
		tekst: bezMarkdowna(r.text),
		prijedlozi: r.proposals.map((p) => {
			provjeriPolja(p, POLJA_PRIJEDLOGA, putanja)
			return {
				id: p.id,
				akcija: p.action,
				resursId: p.resourceId,
				resurs: p.resourceName,
				pocetak: p.startTime,
				kraj: p.endTime,
				cekaPotvrdu: p.needsProviderConfirmation === true,
				rezervacijaId: p.bookingId ?? null,
			}
		}),
	}
}

// ChatProposalResponse(id, action, bookingId, bookingStatus). Ponovljena potvrda vrati istu rezervaciju.
export async function potvrdiPrijedlog(prijedlogId) {
	const putanja = `/api/chat/proposals/${prijedlogId}/confirm`
	const r = provjeriPolja(await apiFetch(putanja, { metoda: 'POST' }), ['action', 'bookingId', 'bookingStatus'], putanja)
	return { akcija: r.action, rezervacijaId: r.bookingId, status: r.bookingStatus }
}

export async function odbaciPrijedlog(prijedlogId) {
	await apiFetch(`/api/chat/proposals/${prijedlogId}/dismiss`, { metoda: 'POST' })
}

// Uputstvo kaže modelu da piše običan tekst, ali gpt-oss-120b u mjerenju i dalje podebljava s **.
// Widget prikazuje tekst kakav jeste, pa bi zvjezdice ostale na ekranu.
function bezMarkdowna(tekst) {
	return tekst.replaceAll('**', '')
}

