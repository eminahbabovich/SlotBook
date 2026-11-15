import { useCallback, useEffect, useState } from 'react'
import { naIstekSesije, obnoviSesiju } from '../api/apiFetch.js'
import { mojNalog, odjava, prijava, registracija } from '../api/auth.js'
import { SesijaContext } from './sesija.js'

const PROVJERAVA = { status: 'provjerava', korisnik: null }
const ODJAVLJEN = { status: 'odjavljen', korisnik: null }
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

	const provjeri = useCallback(() => {
		ucitajSesiju().then(setStanje, () => setStanje(NEDOSTUPAN))
	}, [])

	// StrictMode u dev modu pokrene efekat dvaput. Oba poziva dobiju isti refresh iz
	// obnoviSesiju, pa backend ne vidi dva refresha istim kolačićem.
	useEffect(() => {
		naIstekSesije(() => setStanje(ODJAVLJEN))
		provjeri()
	}, [provjeri])

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
		setStanje(ODJAVLJEN)
	}

	function pokusajPonovo() {
		setStanje(PROVJERAVA)
		provjeri()
	}

	return (
		<SesijaContext.Provider value={{ ...stanje, prijavi, registruj, odjavi, pokusajPonovo }}>{children}</SesijaContext.Provider>
	)
}
