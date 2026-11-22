import { afterEach, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { ispisiSaListe, mojaListaCekanja, upisiNaListu } from './listaCekanja.js'
import { kalendarResursa } from './termini.js'

const json = (tijelo, status = 200) => new Response(JSON.stringify(tijelo), { status, headers: { 'Content-Type': 'application/json' } })

afterEach(() => {
	vi.unstubAllGlobals()
	postaviToken(null)
})

// Oblik kao u WaitlistApiTest.mojaListaImaSamoMojeZiveUpiseSLinkomNaKalendar
const UPIS = {
	id: 'w1',
	resourceId: 'r1',
	resourceName: 'Sala za grupni trening',
	organizationName: 'Teretana Koševo',
	organizationSlug: 'teretana-kosevo',
	timezone: 'Europe/Sarajevo',
	startTime: '2035-11-19T08:00:00Z',
	endTime: '2035-11-19T09:00:00Z',
	status: 'OFFERED',
	offeredUntil: '2035-11-19T07:30:00Z',
}

test('moja lista čekanja daje sve što treba za red i link na kalendar', async () => {
	const fetch = vi.fn(async () => json({ content: [UPIS, { ...UPIS, id: 'w2', status: 'WAITING', offeredUntil: null }], page: 0, size: 20, totalElements: 2, totalPages: 1 }))
	vi.stubGlobal('fetch', fetch)

	const lista = await mojaListaCekanja(0)

	expect(fetch.mock.calls[0][0]).toBe('/api/waitlist/mine?page=0&size=20')
	expect(lista.upisi[0]).toEqual({
		id: 'w1',
		resursId: 'r1',
		resurs: 'Sala za grupni trening',
		organizacija: 'Teretana Koševo',
		slug: 'teretana-kosevo',
		zona: 'Europe/Sarajevo',
		pocetak: '2035-11-19T08:00:00Z',
		kraj: '2035-11-19T09:00:00Z',
		status: 'OFFERED',
		rokPonude: '2035-11-19T07:30:00Z',
	})
	expect(lista.upisi[1].rokPonude).toBeNull()
})

test('upis bez sluga glasno pada, umjesto da link na kalendar vodi na /o/undefined', async () => {
	const { organizationSlug, ...bezSluga } = UPIS
	vi.stubGlobal('fetch', vi.fn(async () => json({ content: [bezSluga], page: 0, size: 20, totalElements: 1, totalPages: 1 })))

	await expect(mojaListaCekanja(0)).rejects.toThrow(/organizationSlug/)
	expect(organizationSlug).toBe('teretana-kosevo')
})

test('upis šalje početak termina, a ispis DELETE na upis', async () => {
	const fetch = vi.fn(async (putanja, opcije) =>
		opcije.method === 'DELETE' ? new Response(null, { status: 204 }) : json({ id: 'w1', status: 'WAITING' }, 201),
	)
	vi.stubGlobal('fetch', fetch)

	expect(await upisiNaListu('r1', '2035-11-19T08:00:00Z')).toEqual({ id: 'w1', status: 'WAITING' })
	await ispisiSaListe('w1')

	expect(fetch.mock.calls[0][0]).toBe('/api/resources/r1/waitlist')
	expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ startTime: '2035-11-19T08:00:00Z' })
	expect(fetch.mock.calls[1][0]).toBe('/api/waitlist/w1')
	expect(fetch.mock.calls[1][1].method).toBe('DELETE')
})

test('kalendar zna koji je termin ponuđen meni i na kojem sam na listi', async () => {
	vi.stubGlobal(
		'fetch',
		vi.fn(async () =>
			json({
				timezone: 'Europe/Sarajevo',
				slots: [{ start: '2035-11-19T07:00:00Z', end: '2035-11-19T08:00:00Z' }],
				taken: [{ start: '2035-11-19T08:00:00Z', end: '2035-11-19T09:00:00Z', myStatus: null, myWaitlistId: 'w2' }],
				offeredToMe: [{ start: '2035-11-19T07:00:00Z', end: '2035-11-19T08:00:00Z', offeredUntil: '2035-11-19T06:30:00Z', waitlistId: 'w1' }],
			}),
		),
	)

	const { termini } = await kalendarResursa('r1', '2035-11-19', '2035-11-26')

	expect(termini[0]).toEqual({ pocetak: '2035-11-19T07:00:00Z', kraj: '2035-11-19T08:00:00Z', stanje: 'ponudjen', rokPonude: '2035-11-19T06:30:00Z', upisId: 'w1' })
	expect(termini[1]).toMatchObject({ stanje: 'zauzeto', upisId: 'w2' })
})
