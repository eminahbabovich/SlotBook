// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, expect, test } from 'vitest'
import { Brojke, Guzva } from './Pregled.jsx'

afterEach(cleanup)

const vrijednost = (naziv) => screen.getByText(naziv).nextElementSibling.textContent

test('brojke gore računaju popunjenost iz minuta svih resursa, a ne kao prosjek procenata', () => {
	render(
		<Brojke
			poStanju={{ CONFIRMED: 3, COMPLETED: 6, NO_SHOW: 2, CANCELLED: 1 }}
			// Sto: 1 h od 2 h (50 %), sala: 9 h od 10 h (90 %). Prosjek bi bio 70 %, a stvarno je 10 od 12 h.
			popunjenost={[
				{ resursId: 'a', naziv: 'Sto 4', zauzeto: 60, ponudjeno: 120 },
				{ resursId: 'b', naziv: 'Sala', zauzeto: 540, ponudjeno: 600 },
			]}
		/>,
	)

	expect(vrijednost('Rezervacije')).toBe('12')
	expect(vrijednost('Popunjenost')).toBe('83 %')
	expect(vrijednost('Održane i potvrđene')).toBe('9')
	expect(vrijednost('Nedolasci')).toBe('2')
	expect(screen.getByText('25 % od održanih termina')).toBeTruthy()
})

test('bez radnog vremena popunjenost kaže "nema", a ne 0 % ni NaN', () => {
	render(<Brojke poStanju={{}} popunjenost={[]} />)

	expect(vrijednost('Popunjenost')).toBe('nema')
	expect(vrijednost('Rezervacije')).toBe('0')
	expect(screen.getByText('još nema održanih termina')).toBeTruthy()
})

test('mreža gužve ima red za svaki dan i kolone od najranijeg do najkasnijeg sata, s brojem u ćeliji', () => {
	render(
		<Guzva
			poDanuISatu={[
				{ dan: 1, sat: 10, broj: 4 },
				{ dan: 3, sat: 8, broj: 1 },
				{ dan: 5, sat: 12, broj: 2 },
			]}
		/>,
	)

	const tabela = screen.getByRole('table', { name: 'Kad je gužva' })
	expect(within(tabela).getAllByRole('columnheader').map((th) => th.textContent)).toEqual(['08', '09', '10', '11', '12'])
	const redovi = within(tabela).getAllByRole('row').slice(1)
	expect(redovi).toHaveLength(7)
	const ponedjeljak = within(redovi[0]).getAllByRole('cell').map((td) => td.textContent)
	expect(ponedjeljak).toEqual(['0', '0', '4', '0', '0'])
})

test('bez ijedne rezervacije mreže gužve nema', () => {
	render(<Guzva poDanuISatu={[]} />)

	expect(screen.queryByRole('table')).toBeNull()
})
