import { afterEach, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { clanoviOrganizacije, posaljiPozivnicu } from './clanovi.js'

const json = (tijelo, status = 200) => new Response(JSON.stringify(tijelo), { status, headers: { 'Content-Type': 'application/json' } })

afterEach(() => {
	vi.unstubAllGlobals()
	postaviToken(null)
})

// Oblik kao MemberResponse u OrganizationMembersApiTest
test('članovi dolaze po stranicama, s imenom, emailom i ulogom', async () => {
	const fetch = vi.fn(async () =>
		json({
			content: [{ userId: 'u1', displayName: 'Hana Šehić', email: 'hana@primjer.ba', role: 'ADMIN', joinedAt: '2035-09-01T08:00:00Z' }],
			page: 1,
			size: 50,
			totalElements: 51,
			totalPages: 2,
		}),
	)
	vi.stubGlobal('fetch', fetch)

	expect(await clanoviOrganizacije('o1', 1)).toEqual({
		clanovi: [{ id: 'u1', ime: 'Hana Šehić', email: 'hana@primjer.ba', uloga: 'ADMIN' }],
		ukupnoStranica: 2,
	})
	expect(fetch.mock.calls[0][0]).toBe('/api/organizations/o1/members?page=1&size=50')
})

test('član bez polja role je greška u API sloju, a ne prazna uloga na ekranu', async () => {
	vi.stubGlobal('fetch', vi.fn(async () => json({ content: [{ userId: 'u1', displayName: 'Hana', email: 'h@p.ba' }], page: 0, totalPages: 1 })))

	await expect(clanoviOrganizacije('o1', 0)).rejects.toThrow('role')
})

// Oblik kao InviteResponse u OrganizationInvitesApiTest: bez tokena, on ide samo emailom
test('pozivnica šalje email i ulogu, a vraća rok do kad važi', async () => {
	const fetch = vi.fn(async () => json({ id: 'i1', email: 'emir@primjer.ba', role: 'PROVIDER', expiresAt: '2035-11-26T09:30:00Z' }, 201))
	vi.stubGlobal('fetch', fetch)

	expect(await posaljiPozivnicu('o1', { email: 'Emir@primjer.ba', uloga: 'PROVIDER' })).toEqual({
		email: 'emir@primjer.ba',
		uloga: 'PROVIDER',
		vaziDo: '2035-11-26T09:30:00Z',
	})
	const [putanja, opcije] = fetch.mock.calls[0]
	expect(`${opcije.method} ${putanja}`).toBe('POST /api/organizations/o1/invites')
	expect(JSON.parse(opcije.body)).toEqual({ email: 'Emir@primjer.ba', role: 'PROVIDER' })
})
