import { danUSedmici } from '../vrijeme/vrijeme.js'
import { brojRezervacija, procenat } from './brojke.js'

const dvaMjesta = (sat) => String(sat % 24).padStart(2, '0')
const DANI = [1, 2, 3, 4, 5, 6, 7]

// Četiri broja koja admin pogleda prvo. Sve se računa iz istog odgovora kao tabele ispod, bez novog
// poziva, pa broj gore i broj u tabeli ne mogu reći dvije različite stvari.
export function Brojke({ poStanju, popunjenost }) {
	const broj = (stanje) => poStanju[stanje] ?? 0
	const ukupno = Object.values(poStanju).reduce((zbir, b) => zbir + b, 0)
	const zauzeto = popunjenost.reduce((zbir, r) => zbir + r.zauzeto, 0)
	const ponudjeno = popunjenost.reduce((zbir, r) => zbir + r.ponudjeno, 0)
	const popunjeno = procenat(zauzeto, ponudjeno)
	const odrzane = broj('COMPLETED') + broj('CONFIRMED')
	const nisuDosli = broj('NO_SHOW')
	const udioNedolazaka = procenat(nisuDosli, broj('COMPLETED') + nisuDosli)

	return (
		<dl className="brojke">
			<div className="brojka">
				<dt>Rezervacije</dt>
				<dd>{ukupno}</dd>
				<dd className="brojka-opis">{brojRezervacija(ukupno)} u svim stanjima</dd>
			</div>
			<div className="brojka brojka-mjed">
				<dt>Popunjenost</dt>
				<dd>{popunjeno === null ? 'nema' : `${popunjeno} %`}</dd>
				<dd className="brojka-opis">{popunjeno === null ? 'nema radnog vremena' : 'zauzeto od ponuđenog vremena'}</dd>
			</div>
			<div className="brojka">
				<dt>Održane i potvrđene</dt>
				<dd>{odrzane}</dd>
				<dd className="brojka-opis">završene i one koje tek dolaze</dd>
			</div>
			<div className="brojka">
				<dt>Nedolasci</dt>
				<dd>{nisuDosli}</dd>
				<dd className="brojka-opis">{udioNedolazaka === null ? 'još nema održanih termina' : `${udioNedolazaka} % od održanih termina`}</dd>
			</div>
		</dl>
	)
}

// Mreža dan × sat: jedna boja (mjed), svjetlija za manje, tamnija za više rezervacija. Pravljena je
// kao tabela, pa je čitač ekrana čita red po red s brojem u svakoj ćeliji, a boja samo pomaže oku.
// Sati idu od najranijeg do najkasnijeg koji se javlja, da mreža nema praznih kolona noću.
export function Guzva({ poDanuISatu }) {
	if (poDanuISatu.length === 0) return null
	const satiUPodacima = poDanuISatu.map((t) => t.sat)
	const sati = []
	for (let s = Math.min(...satiUPodacima); s <= Math.max(...satiUPodacima); s++) sati.push(s)
	const najvise = Math.max(...poDanuISatu.map((t) => t.broj))
	const broj = (dan, sat) => poDanuISatu.find((t) => t.dan === dan && t.sat === sat)?.broj ?? 0

	return (
		<section className="statistika-dio guzva">
			<h2 id="guzva">Kad je gužva</h2>
			<div className="guzva-okvir">
				<table className="guzva-tabela" aria-labelledby="guzva" style={{ '--kolona': sati.length }}>
					<thead>
						<tr>
							<td />
							{sati.map((s) => (
								<th key={s} scope="col">
									{dvaMjesta(s)}
								</th>
							))}
						</tr>
					</thead>
					<tbody>
						{DANI.map((dan, red) => (
							<tr key={dan}>
								<th scope="row">
									<span aria-hidden="true">{danUSedmici(dan).slice(0, 3)}</span>
									<span className="skriveno">{danUSedmici(dan)}</span>
								</th>
								{sati.map((sat, kolona) => {
									const b = broj(dan, sat)
									return (
										<td
											key={sat}
											className={b === 0 ? 'guzva-prazno' : undefined}
											style={{ '--udio': b / najvise, '--red': red, '--kolona-i': kolona }}
											data-opis={`${danUSedmici(dan)}, ${dvaMjesta(sat)}:00: ${brojRezervacija(b)}`}
										>
											<span className="skriveno">{b}</span>
										</td>
									)
								})}
							</tr>
						))}
					</tbody>
				</table>
			</div>
			<p className="guzva-legenda" aria-hidden="true">
				<span>manje</span>
				<span className="guzva-skala" />
				<span>više rezervacija</span>
			</p>
		</section>
	)
}
