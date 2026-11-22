import { useState } from 'react'
import { Polje } from '../forma/Polje.jsx'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import { feningeUKm, kmUFeninge } from './cijena.js'
import '../forma/forma.css'

// Ista forma za novi resurs i izmjenu. Šalje sve vrijednosti, i one koje nisu mijenjane,
// jer PUT na backendu postavlja cijeli resurs.
export function ResursForma({ pocetno, mutacija, dugme, dugmeSalje, poruke, uspjeh }) {
	const [r, setR] = useState(() => ({
		naziv: pocetno.naziv,
		opis: pocetno.opis ?? '',
		trajanjeMinuta: String(pocetno.trajanjeMinuta),
		autoPotvrda: pocetno.autoPotvrda,
		rokOtkazivanjaSati: String(pocetno.rokOtkazivanjaSati),
		brojMjesta: String(pocetno.brojMjesta),
		// Besplatan termin ima prazno polje, ne "0"
		cijenaKM: pocetno.cijenaFeninga === 0 ? '' : feningeUKm(pocetno.cijenaFeninga),
	}))
	const [losaCijena, setLosaCijena] = useState(false)
	const promijeni = (polje) => (vrijednost) => setR((staro) => ({ ...staro, [polje]: vrijednost }))
	const poPoljima = greskePoPoljima(mutacija.error)

	function posalji(dogadjaj) {
		dogadjaj.preventDefault()
		const { cijenaKM, ...ostalo } = r
		const cijenaFeninga = kmUFeninge(cijenaKM)
		setLosaCijena(cijenaFeninga === null)
		if (cijenaFeninga === null) return
		mutacija.mutate({ ...ostalo, cijenaFeninga })
	}

	return (
		<form className="forma" onSubmit={posalji} noValidate>
			<p role="status">{mutacija.isSuccess && uspjeh}</p>
			{mutacija.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(mutacija.error, poruke)}
				</p>
			)}
			<Polje oznaka="Naziv" ime="name" vrijednost={r.naziv} promjena={promijeni('naziv')} greska={poPoljima.name} napomena="Npr. Sto 4, tihi dio ili Termin konsultacija." />
			<Polje oznaka="Opis" ime="description" tip="textarea" vrijednost={r.opis} promjena={promijeni('opis')} greska={poPoljima.description} napomena="Nije obavezan. Klijent ga vidi iznad kalendara." />
			<Polje
				oznaka="Trajanje termina u minutama"
				ime="slotMinutes"
				tip="number"
				vrijednost={r.trajanjeMinuta}
				promjena={promijeni('trajanjeMinuta')}
				greska={poPoljima.slotMinutes}
				napomena="Od 5 do 480. Kalendar nudi termine ove dužine."
			/>
			<Polje
				oznaka="Broj mjesta u terminu"
				ime="capacity"
				tip="number"
				vrijednost={r.brojMjesta}
				promjena={promijeni('brojMjesta')}
				greska={poPoljima.capacity}
				napomena="1 za sto ili konsultacije, više za grupni trening ili grupnu sobu. Najviše 100."
			/>
			<Polje
				oznaka="Cijena termina u KM"
				ime="priceCents"
				vrijednost={r.cijenaKM}
				promjena={promijeni('cijenaKM')}
				inputMode="decimal"
				greska={losaCijena ? 'Upiši cijenu kao broj, npr. 15 ili 7,50.' : poPoljima.priceCents}
				napomena="Prazno je besplatno. Najviše 1000 KM."
			/>
			<div className="polje">
				<label htmlFor="resurs-potvrda">Potvrda rezervacije</label>
				<select id="resurs-potvrda" name="autoConfirm" value={r.autoPotvrda ? 'sama' : 'rucno'} onChange={(d) => promijeni('autoPotvrda')(d.target.value === 'sama')}>
					<option value="sama">odmah, sama od sebe</option>
					<option value="rucno">ručno, potvrđuje ko vodi resurs</option>
				</select>
				{poPoljima.autoConfirm && <p className="greska-polja">{poPoljima.autoConfirm}</p>}
			</div>
			<Polje
				oznaka="Rok za otkazivanje u satima"
				ime="cancelNoticeHours"
				tip="number"
				vrijednost={r.rokOtkazivanjaSati}
				promjena={promijeni('rokOtkazivanjaSati')}
				greska={poPoljima.cancelNoticeHours}
				napomena="Koliko sati prije termina klijent još može sam otkazati. 0 znači do samog početka."
			/>
			<button className="dugme" type="submit" disabled={mutacija.isPending}>
				{mutacija.isPending ? dugmeSalje : dugme}
			</button>
		</form>
	)
}
