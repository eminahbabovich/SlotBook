import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useState } from 'react'
import { naIstekSesije, obnoviSesiju } from '../api/apiFetch.js'
import { mojNalog, odjava, prijava, registracija } from '../api/auth.js'
import { SesijaContext } from './sesija.js'

const PROVJERAVA = { status: 'provjerava', korisnik: null }
const ODJAVLJEN = { status: 'odjavljen', korisnik: null }
// Odjava na zahtjev korisnika: sljedeća osoba za istim računarom počinje od svoje liste,
// a ne od adrese na kojoj je prethodna stala.
const ODJAVIO_SE = { ...ODJAVLJEN, odjavioSe: true }
const NEDOSTUPAN = { status: 'nedostupan', korisnik: null }

// Pri otvaranju stranice token u memoriji ne postoji, pa se sesija vraća preko kolačića.
async function ucitajSesiju() {
	if (!(await obnoviSesiju())) {
		return ODJAVLJEN
	}
	return { status: 'prijavljen', korisnik: await mojNalog() }
}

export function SesijaProvider({ children }) {
	const [stanje, setStanje] = useState(PROVJERAVA)
	const queryClient = useQueryClient()

	// Keš pripada osobi koja je bila prijavljena. Bez brisanja bi sljedeća osoba na istom
	// računaru vidjela tuđe organizacije dok se njene ne učitaju.
	const zavrsiSesiju = useCallback(
		(novoStanje) => {
			queryClient.clear()
			setStanje(novoStanje)
		},
		[queryClient],
	)

	const provjeri = useCallback(() => {
		ucitajSesiju().then(setStanje, () => setStanje(NEDOSTUPAN))
	}, [])

	// StrictMode u dev modu pokrene efekat dvaput. Oba poziva dobiju isti refresh iz
	// obnoviSesiju, pa backend ne vidi dva refresha istim kolačićem.
	useEffect(() => {
		naIstekSesije(() => zavrsiSesiju(ODJAVLJEN))
		provjeri()
	}, [provjeri, zavrsiSesiju])

	async function prijavi(email, lozinka) {
		await prijava(email, lozinka)
		setStanje({ status: 'prijavljen', korisnik: await mojNalog() })
	}

	async function registruj(ime, email, lozinka) {
		await registracija(ime, email, lozinka)
		setStanje({ status: 'prijavljen', korisnik: await mojNalog() })
	}

	async function odjavi() {
		await odjava()
		zavrsiSesiju(ODJAVIO_SE)
	}

	function pokusajPonovo() {
		setStanje(PROVJERAVA)
		provjeri()
	}

	return (
		<SesijaContext.Provider value={{ ...stanje, prijavi, registruj, odjavi, pokusajPonovo }}>{children}</SesijaContext.Provider>
	)
}
