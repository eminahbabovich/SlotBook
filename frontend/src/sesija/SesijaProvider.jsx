import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { naIstekSesije, obnoviSesiju } from '../api/apiFetch.js'
import { mojNalog, odjava, prijava } from '../api/auth.js'
import { obrisiNalog as obrisi } from '../api/nalog.js'
import { SesijaContext } from './sesija.js'

const PROVJERAVA = { status: 'provjerava', korisnik: null }
const ODJAVLJEN = { status: 'odjavljen', korisnik: null }
// Odjava na zahtjev korisnika: sljedeća osoba za istim računarom počinje od svoje liste,
// a ne od adrese na kojoj je prethodna stala.
const ODJAVIO_SE = { ...ODJAVLJEN, odjavioSe: true }
// Prijava poslije brisanja kaže šta se desilo, da prazna forma ne izgleda kao istekla sesija
const OBRISAN = { ...ODJAVIO_SE, obrisan: true }
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
	// Istek javlja i prva provjera kad sesije nikad nije ni bilo (tablet s ekranom na vratima, link za
	// pridruživanje). Tada keš ne pripada nikome, a brisanje bi usred učitavanja odbacilo upit javne
	// stranice, koja bi onda ostala na "Učitava se". Briše se samo kad istekne sesija prijavljene osobe.
	const status = useRef(stanje.status)
	useEffect(() => {
		status.current = stanje.status
	}, [stanje.status])

	useEffect(() => {
		naIstekSesije(() => (status.current === 'prijavljen' ? zavrsiSesiju(ODJAVLJEN) : setStanje(ODJAVLJEN)))
		provjeri()
	}, [provjeri, zavrsiSesiju])

	async function prijavi(email, lozinka) {
		await prijava(email, lozinka)
		setStanje({ status: 'prijavljen', korisnik: await mojNalog() })
	}

	async function odjavi() {
		await odjava()
		zavrsiSesiju(ODJAVIO_SE)
	}

	async function obrisiNalog(lozinka) {
		await obrisi(lozinka)
		zavrsiSesiju(OBRISAN)
	}

	function pokusajPonovo() {
		setStanje(PROVJERAVA)
		provjeri()
	}

	return (
		<SesijaContext.Provider value={{ ...stanje, prijavi, odjavi, obrisiNalog, pokusajPonovo }}>{children}</SesijaContext.Provider>
	)
}
