import { afterEach, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { deaktivirajResurs, izmijeniResurs, noviResurs, promijeniVoditelja, resursiOrganizacije } from './resursi.js'

const json = (tijelo, status = 200) => new Response(JSON.stringify(tijelo), { status, headers: { 'Content-Type': 'application/json' } })

// Oblik kao ResourceResponse u ResourceApiTest
const STO = { id: 'r1', organizationId: 'o1', providerId: 'u1', name: 'Sto 4, tihi dio', description: null, slotMinutes: 60, autoConfirm: false, cancelNoticeHours: 24, capacity: 1, priceCents: 0, active: true, hasImage: false }
const STO_ZA_UI = { id: 'r1', naziv: 'Sto 4, tihi dio', opis: null, trajanjeMinuta: 60, vodi: 'u1', autoPotvrda: false, rokOtkazivanjaSati: 24, brojMjesta: 1, cijenaFeninga: 0, aktivan: true }

afterEach(() => {
	vi.unstubAllGlobals()
	postaviToken(null)
})

const poziv = (fetch, i = 0) => {
	const [putanja, opcije] = fetch.mock.calls[i]
	return { zahtjev: `${opcije.method} ${putanja}`, tijelo: opcije.body === undefined ? undefined : JSON.parse(opcije.body) }
}

test('resursi organizacije dolaze sa svim što forma i lista trebaju, i s oznakom da li su aktivni', async () => {
	const fetch = vi.fn(async () => json({ content: [STO], page: 0, size: 100, totalElements: 1, totalPages: 1 }))
	vi.stubGlobal('fetch', fetch)

	expect(await resursiOrganizacije('o1')).toEqual([STO_ZA_UI])
	expect(fetch.mock.calls[0][0]).toBe('/api/organizations/o1/resources?size=100')
})

test('resurs bez polja capacity je greška u API sloju, jer bi izmjena vratila salu na jedno mjesto', async () => {
	const { capacity: _, ...bezMjesta } = STO
	vi.stubGlobal('fetch', vi.fn(async () => json({ content: [bezMjesta], page: 0, totalPages: 1 })))

	await expect(resursiOrganizacije('o1')).rejects.toThrow('capacity')
})

test('resurs bez polja priceCents je greška u API sloju, jer bi izmjena plaćen termin učinila besplatnim', async () => {
	const { priceCents: _, ...bezCijene } = STO
	vi.stubGlobal('fetch', vi.fn(async () => json({ content: [bezCijene], page: 0, totalPages: 1 })))

	await expect(resursiOrganizacije('o1')).rejects.toThrow('priceCents')
})

test('resurs bez polja active je greška u API sloju, jer bi deaktiviran izgledao kao aktivan', async () => {
	const { active: _, ...bezActive } = STO
	vi.stubGlobal('fetch', vi.fn(async () => json({ content: [bezActive], page: 0, totalPages: 1 })))

	await expect(resursiOrganizacije('o1')).rejects.toThrow('active')
})

test('novi resurs šalje brojeve kao brojeve, a prazan opis kao null', async () => {
	const fetch = vi.fn(async () => json(STO, 201))
	vi.stubGlobal('fetch', fetch)

	const resurs = await noviResurs('o1', { naziv: 'Sto 4, tihi dio', opis: '', trajanjeMinuta: '60', autoPotvrda: false, rokOtkazivanjaSati: '24', brojMjesta: '1', cijenaFeninga: 0 })

	expect(resurs).toEqual(STO_ZA_UI)
	expect(poziv(fetch)).toEqual({
		zahtjev: 'POST /api/organizations/o1/resources',
		tijelo: { name: 'Sto 4, tihi dio', description: null, slotMinutes: 60, autoConfirm: false, cancelNoticeHours: 24, capacity: 1, priceCents: 0 },
	})
})

// PUT bez autoConfirm, cancelNoticeHours, capacity ili priceCents bi ih vratio na podrazumijevane (ResourceRequest)
test('izmjena šalje sva polja, i ona koja se nisu mijenjala', async () => {
	const fetch = vi.fn(async () => json(STO))
	vi.stubGlobal('fetch', fetch)

	await izmijeniResurs('o1', 'r1', { naziv: 'Sto 4', opis: 'Uz prozor.', trajanjeMinuta: 90, autoPotvrda: false, rokOtkazivanjaSati: 24, brojMjesta: 5, cijenaFeninga: 750 })

	expect(poziv(fetch)).toEqual({
		zahtjev: 'PUT /api/organizations/o1/resources/r1',
		tijelo: { name: 'Sto 4', description: 'Uz prozor.', slotMinutes: 90, autoConfirm: false, cancelNoticeHours: 24, capacity: 5, priceCents: 750 },
	})
})

test('prazno trajanje ide kao null, pa backend vrati 400 s porukom za to polje', async () => {
	const fetch = vi.fn(async () => json(STO, 201))
	vi.stubGlobal('fetch', fetch)

	await noviResurs('o1', { naziv: 'Sala 1', opis: '', trajanjeMinuta: '', autoPotvrda: true, rokOtkazivanjaSati: '' })

	expect(poziv(fetch).tijelo).toMatchObject({ slotMinutes: null, cancelNoticeHours: null })
})

test('deaktivacija je DELETE bez tijela, a promjena voditelja šalje samo providerId', async () => {
	const fetch = vi.fn(async (putanja) => (putanja.endsWith('/provider') ? json({ ...STO, providerId: 'u7' }) : new Response(null, { status: 204 })))
	vi.stubGlobal('fetch', fetch)

	await deaktivirajResurs('o1', 'r1')
	expect((await promijeniVoditelja('o1', 'r1', 'u7')).vodi).toBe('u7')

	expect(poziv(fetch, 0)).toEqual({ zahtjev: 'DELETE /api/organizations/o1/resources/r1', tijelo: undefined })
	expect(poziv(fetch, 1)).toEqual({ zahtjev: 'PUT /api/organizations/o1/resources/r1/provider', tijelo: { providerId: 'u7' } })
})
