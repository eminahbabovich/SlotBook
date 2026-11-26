import { Navigate, Outlet, useLocation } from 'react-router'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { useSesija } from './sesija.js'

// Rute za prijavljene ili samo za odjavljene. Ovo je samo za korisnika, da ne gleda ekran
// koji mu ne treba. Pristup podacima čuva backend, i bez ovog čuvara.
export function Cuvar({ za }) {
	const { status, odjavioSe, pokusajPonovo } = useSesija()
	const lokacija = useLocation()

	if (status === 'provjerava') {
		return <Ucitava tekst="Provjerava se prijava" />
	}
	if (status === 'nedostupan') {
		return <NijeUcitano poruka="Prijava se ne može provjeriti, server ne odgovara. Pokušaj ponovo za minutu." ponovo={pokusajPonovo} />
	}
	if (za === 'prijavljene' && status === 'odjavljen') {
		// Adresa se pamti, da poslije prijave korisnik završi tamo gdje je krenuo. I dio iza #,
		// jer je u njemu token pozivnice.
		return <Navigate to="/prijava" replace state={odjavioSe ? null : { odakle: lokacija.pathname + lokacija.hash }} />
	}
	if (za === 'odjavljene' && status === 'prijavljen') {
		return <Navigate to={lokacija.state?.odakle ?? '/'} replace />
	}
	return <Outlet />
}
