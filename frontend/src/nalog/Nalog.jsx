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
				<div>
					<dt>Ime</dt>
					<dd>{korisnik.ime}</dd>
				</div>
				<div>
					<dt>Email</dt>
					<dd>{korisnik.email}</dd>
				</div>
			</dl>
			<PretplataNaKalendar />
			<IzvozPodataka />
			<BrisanjeNaloga />
		</section>
	)
}
