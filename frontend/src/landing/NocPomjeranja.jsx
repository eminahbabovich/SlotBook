import { useState } from 'react'
import { satUZoni, uTrenutakSPomakom } from '../vrijeme/vrijeme.js'

const ZONA = 'Europe/Sarajevo'
// Noć 27. na 28. mart 2027: u 01:00 UTC sat u Sarajevu skoči s 02:00 na 03:00.
// Klizač ide po stvarnom vremenu (UTC), u koracima od pola sata, od 22:00 do 07:00 UTC.
const POCETAK = Date.UTC(2027, 2, 27, 22, 0)
const KORAK = 30 * 60 * 1000
const KORACI = 18
const SATI_NA_TRACI = ['23', '00', '01', '02', '03', '04', '05', '06', '07', '08', '09']

const formatPomaka = new Intl.DateTimeFormat('en-US', { timeZone: ZONA, timeZoneName: 'shortOffset' })
function pomak(trenutak) {
	return formatPomaka.formatToParts(trenutak).find((d) => d.type === 'timeZoneName').value.replace('GMT', 'UTC')
}

// Traka sati u Sarajevu, s rupom na 02. Funkcija uTrenutakSPomakom je ista koju koristi kalendar
// kad pravi termin: za 02:30 te noći vrati null, jer taj sat ne postoji.
export function NocPomjeranja() {
	const [korak, setKorak] = useState(4)
	const trenutak = new Date(POCETAK + korak * KORAK)
	const lokalno = satUZoni(trenutak, ZONA)
	const utc = trenutak.toISOString().slice(11, 16)
	// Položaj na traci: lokalni sat kao broj, s tim da 03 dolazi odmah poslije 01:59
	const [h, m] = lokalno.split(':').map(Number)
	const satNaTraci = (h < 12 ? h + 24 : h) - 23 + m / 60
	const nePostoji = uTrenutakSPomakom('2027-03-28', '02:30', ZONA) === null

	return (
		<section className="sekcija sekcija-obrnuta" aria-labelledby="noc-naslov">
			<div className="sekcija-tekst">
				<h2 id="noc-naslov" className="sekcija-naslov">
					Sat koji preskoči
				</h2>
				<p>
					U noći 28. marta 2027. sat u Sarajevu sa 01:59 skoči na 03:00. Sat od dva do tri te noći ne postoji, pa ga SlotBook i ne
					nudi. Termin u osam ujutro ostaje u osam, iako je u UTC-u sat ranije nego dan prije.
				</p>
				<p>
					Sahat-kula na Baščaršiji i danas pokazuje vrijeme po zalasku sunca. SlotBook je manje romantičan: termini se pamte kao
					trenutak u UTC-u, a radno vrijeme se računa u zoni organizacije, <code>Europe/Sarajevo</code>.
				</p>
				<p className="dokaz">
					Dokaz: <code>SlotCalculatorTest</code>. Od ponoći do 04:00 te noći su tri sata, i kalendar nudi tri termina, ne četiri.
					{nePostoji && ' Klizač ispod računa istim kodom kao kalendar, pa ni za njega 02:30 te noći ne postoji.'}
				</p>
			</div>

			<div className="noc">
				<div className="noc-sat" aria-live="polite">
					<p className="noc-lokalno">
						<time>{lokalno}</time>
					</p>
					<p className="noc-pomak">
						u Sarajevu, {pomak(trenutak)}. U UTC-u je <time>{utc}</time>.
					</p>
				</div>
				<div className="noc-traka" aria-hidden="true">
					{SATI_NA_TRACI.map((sat) => (
						<span key={sat} className={`noc-oznaka ${sat === '02' ? 'noc-rupa' : ''}`}>
							{sat}
						</span>
					))}
					<span className="noc-kazaljka" style={{ '--polozaj': satNaTraci / (SATI_NA_TRACI.length - 1) }} />
				</div>
				<label className="noc-klizac">
					<span className="skriveno">Vrijeme te noći</span>
					<input type="range" min="0" max={KORACI} step="1" value={korak} onChange={(d) => setKorak(Number(d.target.value))} />
				</label>
			</div>
		</section>
	)
}
