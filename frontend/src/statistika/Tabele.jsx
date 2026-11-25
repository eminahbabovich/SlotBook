import { STATUS } from '../rezervacije/status.js'
import { danUSedmici } from '../vrijeme/vrijeme.js'
import { brojRezervacija, procenat, sati } from './brojke.js'

// Traka je samo slika broja koji već piše u redu, pa je čitač ekrana preskače.
// Duža od cijele ne može biti, i kad je popunjenost preko 100 %.
function Traka({ udio }) {
	return (
		<span className="traka" aria-hidden="true">
			<span className="traka-puna" style={{ width: `${Math.min(udio, 1) * 100}%` }} />
		</span>
	)
}

export function PoStanju({ poStanju, mjesec }) {
	const redovi = Object.keys(STATUS).map((stanje) => [stanje, poStanju[stanje] ?? 0])
	const ukupno = redovi.reduce((zbir, [, broj]) => zbir + broj, 0)
	const najvise = Math.max(...redovi.map(([, broj]) => broj))

	return (
		<section className="statistika-dio">
			<h2 id="po-stanju">Rezervacije po stanju</h2>
			{ukupno === 0 ? (
				<p className="prazno">Za {mjesec} nema rezervacija.</p>
			) : (
				<>
					<p>Ukupno {brojRezervacija(ukupno)}.</p>
					<table className="statistika-tabela" aria-labelledby="po-stanju">
						<thead>
							<tr>
								<th scope="col">Stanje</th>
								<th scope="col">Broj</th>
							</tr>
						</thead>
						<tbody>
							{redovi.map(([stanje, broj]) => (
								<tr key={stanje} className={`stanje-${stanje.toLowerCase()}`}>
									<th scope="row">
										{STATUS[stanje]}
										<Traka udio={broj / najvise} />
									</th>
									<td className="broj">{broj}</td>
								</tr>
							))}
						</tbody>
					</table>
				</>
			)}
		</section>
	)
}

// Sati idu u drugi red ispod naziva, jer tri kolone na telefonu ne stanu.
// Ukupno se računa iz minuta, ne kao prosjek procenata: sto koji radi dva sata ne smije
// vrijediti koliko sala koja radi cijeli dan.
export function Popunjenost({ popunjenost, nemaResursa, csv }) {
	const ukupno = popunjenost.reduce((zbir, r) => ({ zauzeto: zbir.zauzeto + r.zauzeto, ponudjeno: zbir.ponudjeno + r.ponudjeno }), { zauzeto: 0, ponudjeno: 0 })
	const prekoSto = popunjenost.some((r) => r.zauzeto > r.ponudjeno && r.ponudjeno > 0)

	return (
		<section className="statistika-dio">
			<h2 id="popunjenost">Popunjenost po resursu</h2>
			{popunjenost.length === 0 ? (
				<p>{nemaResursa}</p>
			) : (
				<>
					<table className="statistika-tabela" aria-labelledby="popunjenost">
						<thead>
							<tr>
								<th scope="col">Resurs</th>
								<th scope="col">Popunjenost</th>
							</tr>
						</thead>
						<tbody>
							{popunjenost.map((r) => (
								<RedPopunjenosti key={r.resursId} naziv={r.naziv} zauzeto={r.zauzeto} ponudjeno={r.ponudjeno} />
							))}
						</tbody>
						{popunjenost.length > 1 && (
							<tfoot>
								<RedPopunjenosti naziv="Ukupno" zauzeto={ukupno.zauzeto} ponudjeno={ukupno.ponudjeno} />
							</tfoot>
						)}
					</table>
					<p className="napomena">
						Zauzeto su potvrđene i završene rezervacije i one na koje osoba nije došla. Ponuđeno je radno vrijeme bez
						izuzetaka, i za prošle mjesece po sadašnjem, jer se staro radno vrijeme ne čuva.
						{prekoSto && ' Preko 100 % znači da je radno vrijeme skraćeno poslije rezervacija, a rezervacije su ostale.'}
					</p>
					{csv}
				</>
			)}
		</section>
	)
}

function RedPopunjenosti({ naziv, zauzeto, ponudjeno }) {
	const p = procenat(zauzeto, ponudjeno)
	return (
		<tr>
			<th scope="row">
				{naziv}
				{p !== null && <Traka udio={zauzeto / ponudjeno} />}
				<span className="statistika-sati">{p === null ? 'nema radnog vremena' : `${sati(zauzeto)} od ${sati(ponudjeno)}`}</span>
			</th>
			<td className="broj">{p === null ? 'nema' : `${p} %`}</td>
		</tr>
	)
}

const dvaMjesta = (sat) => String(sat % 24).padStart(2, '0')

// Najviše pet, jer je ovo odgovor na "kad je gužva", a ne cijela tabela
export function Najtrazeniji({ poDanuISatu }) {
	const prvih = [...poDanuISatu].sort((a, b) => b.broj - a.broj || a.dan - b.dan || a.sat - b.sat).slice(0, 5)
	if (prvih.length === 0) return null
	const najvise = prvih[0].broj

	return (
		<section className="statistika-dio">
			<h2 id="najtrazeniji">Najtraženiji termini</h2>
			<table className="statistika-tabela" aria-labelledby="najtrazeniji">
				<thead>
					<tr>
						<th scope="col">Termin</th>
						<th scope="col">Broj rezervacija</th>
					</tr>
				</thead>
				<tbody>
					{prvih.map((t) => (
						<tr key={`${t.dan}-${t.sat}`}>
							<th scope="row">
								{`${danUSedmici(t.dan)}, ${dvaMjesta(t.sat)}:00 do ${dvaMjesta(t.sat + 1)}:00`}
								<Traka udio={t.broj / najvise} />
							</th>
							<td className="broj">{t.broj}</td>
						</tr>
					))}
				</tbody>
			</table>
			<p className="napomena">Broje se sve rezervacije, i otkazane i odbijene, jer pokazuju kad ljudi žele doći.</p>
		</section>
	)
}
