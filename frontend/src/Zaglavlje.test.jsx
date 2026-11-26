// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from './api/apiFetch.js'
import { HANA, PRIJAVLJENA, backend, json, otvori } from './test/lazniBackend.js'

const pravaMedia = window.matchMedia

// Telefon: upit za usku širinu kaže "da", sve ostalo kao u postavci
function naTelefonu() {
	window.matchMedia = (upit) => ({ ...pravaMedia(upit), matches: upit === '(max-width: 640px)' })
}

beforeEach(() => postaviToken(null))
afterEach(() => {
	cleanup()
	window.matchMedia = pravaMedia
	vi.unstubAllGlobals()
})

describe('zaglavlje za prijavljene', () => {
	test('na telefonu je nalog krug s inicijalima, a linkovi i odjava su u meniju', async () => {
		naTelefonu()
		backend(PRIJAVLJENA)
		otvori('/')

		const avatar = await screen.findByRole('button', { name: 'Meni naloga: Hana Šehić' })
		expect(avatar.textContent).toBe('HŠ')
		const meni = document.getElementById('meni-naloga')
		expect(avatar.getAttribute('aria-controls')).toBe('meni-naloga')
		// Meni je zatvoren popover, koji jsdom vodi kao skriven; otvaranje radi browser
		const skriven = { hidden: true }
		expect(within(meni).getByRole('link', { name: 'Moje rezervacije', ...skriven })).toBeTruthy()
		expect(within(meni).getByRole('link', { name: 'Nalog', ...skriven })).toBeTruthy()
		expect(within(meni).getByRole('button', { name: 'Odjavi se', ...skriven })).toBeTruthy()
		expect(within(meni).getByRole('switch', { name: 'Tamna tema', ...skriven })).toBeTruthy()
		// Prekidač teme je samo u meniju, ne i u redu zaglavlja
		expect(screen.getAllByRole('switch', { name: 'Tamna tema', ...skriven })).toHaveLength(1)
	})

	test('na desktopu su linkovi u redu, bez kruga', async () => {
		backend(PRIJAVLJENA)
		otvori('/')

		expect(await screen.findByRole('button', { name: 'Odjavi se' })).toBeTruthy()
		expect(screen.queryByRole('button', { name: /Meni naloga/ })).toBeNull()
	})

	test('demo nalog dobije traku s linkom na registraciju, a običan ne', async () => {
		backend({ ...PRIJAVLJENA, '/api/me': () => json({ ...HANA, email: 'klijent@slotbook.test' }) })
		otvori('/')

		expect(await screen.findByText(/Demo nalog: podaci se vraćaju svaki dan/)).toBeTruthy()
		expect(screen.getByRole('link', { name: 'Napravi svoju organizaciju' }).getAttribute('href')).toBe('/registracija')
		cleanup()

		backend(PRIJAVLJENA)
		otvori('/')
		await screen.findByRole('button', { name: 'Odjavi se' })
		expect(screen.queryByText(/Demo nalog/)).toBeNull()
	})
})
