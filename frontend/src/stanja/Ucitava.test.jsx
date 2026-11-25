// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, expect, test } from 'vitest'
import { Ucitava } from './Ucitava.jsx'

afterEach(cleanup)

test('učitavanje kaže čitaču ekrana šta se učitava, a kostur je samo za oko', () => {
	const { container } = render(<Ucitava tekst="Učitavaju se tvoje rezervacije" />)

	expect(screen.getByText('Učitavaju se tvoje rezervacije').getAttribute('aria-live')).toBe('polite')
	const kostur = container.querySelector('[aria-hidden="true"]')
	expect(kostur.children.length).toBeGreaterThan(0)
})
