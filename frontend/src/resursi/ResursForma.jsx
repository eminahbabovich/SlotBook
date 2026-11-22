import { useState } from 'react'
import { Polje } from '../forma/Polje.jsx'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
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
	}))
	const promijeni = (polje) => (vrijednost) => setR((staro) => ({ ...staro, [polje]: vrijednost }))
	const poPoljima = greskePoPoljima(mutacija.error)

	function posalji(dogadjaj) {
		dogadjaj.preventDefault()
		mutacija.mutate(r)
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
