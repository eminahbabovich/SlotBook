// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { SacuvajKartu, SacuvajKartuIkona } from './SacuvajKartu.jsx'
import { brojKarte } from './brojKarte.js'

const toBlob = vi.fn()
vi.mock('html-to-image', () => ({ toBlob: (...a) => toBlob(...a) }))
const sacuvajFajl = vi.fn()
vi.mock('../sacuvajFajl.js', () => ({ sacuvajFajl: (...a) => sacuvajFajl(...a) }))

const ID = '7d1f3a52-0c4b-4e3a-8f61-2b9c5d7e1a40'

afterEach(() => {
	cleanup()
	vi.clearAllMocks()
})

function nacrtaj() {
	render(
		<SacuvajKartu id={ID}>
			<article className="karta">Karta</article>
		</SacuvajKartu>,
	)
}

test('slika se pravi od same karte i čuva pod brojem karte', async () => {
	const slika = new Blob(['png'], { type: 'image/png' })
	toBlob.mockResolvedValue(slika)
	nacrtaj()

	await userEvent.setup().click(screen.getByRole('button', { name: 'Sačuvaj kartu kao sliku' }))

	await vi.waitFor(() => expect(sacuvajFajl).toHaveBeenCalledWith(slika, `karta-${brojKarte(ID)}.png`))
	expect(toBlob.mock.calls[0][0].textContent).toBe('Karta')
	expect(screen.queryByRole('alert')).toBeNull()
})

test('kad slika ne uspije, poruka kaže šta uraditi umjesto toga, a ništa se ne čuva', async () => {
	toBlob.mockRejectedValue(new Error('canvas'))
	nacrtaj()

	await userEvent.setup().click(screen.getByRole('button', { name: 'Sačuvaj kartu kao sliku' }))

	expect((await screen.findByRole('alert')).textContent).toMatch(/snimak ekrana/)
	expect(sacuvajFajl).not.toHaveBeenCalled()
	expect(screen.getByRole('button', { name: 'Sačuvaj kartu kao sliku' }).disabled).toBe(false)
})

test('ikona u redu radnji pravi sliku od karte u kojoj stoji, i ima naziv za čitač i za miša', async () => {
	toBlob.mockResolvedValue(new Blob(['png'], { type: 'image/png' }))
	render(
		<article className="karta" data-testid="karta">
			<div className="karta-radnje">
				<SacuvajKartuIkona id={ID} />
			</div>
		</article>,
	)

	const dugme = screen.getByRole('button', { name: 'Sačuvaj kartu kao sliku' })
	expect(dugme.getAttribute('title')).toBe('Sačuvaj kartu kao sliku')
	await userEvent.setup().click(dugme)

	expect(toBlob.mock.calls[0][0]).toBe(screen.getByTestId('karta'))
	expect(sacuvajFajl).toHaveBeenCalledWith(expect.any(Blob), `karta-${brojKarte(ID)}.png`)
})
