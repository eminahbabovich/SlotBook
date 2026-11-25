// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, expect, test, vi } from 'vitest'
import { PrelazLink } from './PrelazLink.jsx'

afterEach(() => {
	cleanup()
	delete document.startViewTransition
	delete document.documentElement.dataset.prelaz
})

function prikazi() {
	render(
		<MemoryRouter initialEntries={['/o/citaonica-pmf']}>
			<Routes>
				<Route path="/o/citaonica-pmf" element={<PrelazLink to="/o/citaonica-pmf/r/sto-4">Sto 4, tihi dio</PrelazLink>} />
				<Route path="/o/citaonica-pmf/r/sto-4" element={<h1>Kalendar za Sto 4</h1>} />
			</Routes>
		</MemoryRouter>,
	)
}

// jsdom nema View Transitions, pa se ovdje glumi: browser pozove promjenu i vrati obećanje kraja
function glumiPrelaz() {
	const prelaz = vi.fn((promjena) => {
		promjena()
		return { finished: new Promise(() => {}) }
	})
	document.startViewTransition = prelaz
	return prelaz
}

test('klik vodi na novi ekran kroz View Transition označen kao ekran', () => {
	const prelaz = glumiPrelaz()
	prikazi()

	fireEvent.click(screen.getByRole('link', { name: 'Sto 4, tihi dio' }))

	expect(prelaz).toHaveBeenCalledOnce()
	expect(document.documentElement.dataset.prelaz).toBe('ekran')
	expect(screen.getByRole('heading', { name: 'Kalendar za Sto 4' })).toBeTruthy()
})

test('ctrl-klik ostaje browseru (novi tab), bez prelaza i bez navigacije u ovom tabu', () => {
	const prelaz = glumiPrelaz()
	prikazi()

	fireEvent.click(screen.getByRole('link', { name: 'Sto 4, tihi dio' }), { ctrlKey: true })

	expect(prelaz).not.toHaveBeenCalled()
	expect(screen.queryByRole('heading', { name: 'Kalendar za Sto 4' })).toBeNull()
})

test('bez podrške u browseru je običan link', () => {
	prikazi()

	fireEvent.click(screen.getByRole('link', { name: 'Sto 4, tihi dio' }))

	expect(screen.getByRole('heading', { name: 'Kalendar za Sto 4' })).toBeTruthy()
})
