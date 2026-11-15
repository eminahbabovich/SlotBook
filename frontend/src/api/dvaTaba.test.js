import { afterEach, expect, test, vi } from 'vitest'

// Dva taba = dvije zasebne kopije modula apiFetch.js (svaka ima svoj token u memoriji),
// a dijele kolačiće i bravu, kao tabovi u istom browseru. Brava je pravi navigator.locks.
async function otvoriTab() {
	vi.resetModules()
	return import('./apiFetch.js')
}

// Lažni backend s rotacijom: refresh sa starim kolačićem je ponovna upotreba (krađa),
// pa vrati 401, isto kao RefreshTokenService. Kolačić se čita kad zahtjev krene.
function backendSRotacijom() {
	let kolacicUBrowseru = 'r1'
	let vazeciNaServeru = 'r1'
	let broj = 1
	vi.stubGlobal(
		'fetch',
		vi.fn(async () => {
			const poslan = kolacicUBrowseru
			await new Promise((r) => setTimeout(r, 20))
			if (poslan !== vazeciNaServeru) {
				return new Response(JSON.stringify({ status: 401 }), { status: 401 })
			}
			vazeciNaServeru = kolacicUBrowseru = `r${++broj}`
			return new Response(JSON.stringify({ accessToken: `a${broj}` }), { status: 200 })
		}),
	)
}

afterEach(() => vi.unstubAllGlobals())

test('dva taba koja obnove sesiju u istom trenutku ostanu prijavljena oba', async () => {
	backendSRotacijom()
	const tabA = await otvoriTab()
	const tabB = await otvoriTab()
	const istekA = vi.fn()
	const istekB = vi.fn()
	tabA.naIstekSesije(istekA)
	tabB.naIstekSesije(istekB)

	expect(await Promise.all([tabA.obnoviSesiju(), tabB.obnoviSesiju()])).toEqual([true, true])
	expect(istekA).not.toHaveBeenCalled()
	expect(istekB).not.toHaveBeenCalled()
})
