import { useSesija } from '../sesija/sesija.js'
import { BrisanjeNaloga } from './BrisanjeNaloga.jsx'
import { IzvozPodataka } from './IzvozPodataka.jsx'
import { PretplataNaKalendar } from './PretplataNaKalendar.jsx'
import './nalog.css'

export function Nalog() {
	const { korisnik } = useSesija()

	return (
		<section>
			<h1>Moj nalog</h1>
			<dl className="podaci-naloga">
				<dt>Ime</dt>
				<dd>{korisnik.ime}</dd>
				<dt>Email</dt>
				<dd>{korisnik.email}</dd>
			</dl>
			<PretplataNaKalendar />
			<IzvozPodataka />
			<BrisanjeNaloga />
		</section>
	)
}
