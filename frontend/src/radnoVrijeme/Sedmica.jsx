import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { sacuvajRadnoVrijeme } from '../api/radnoVrijeme.js'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'

const PORUKE = {
	serverNeOdgovara: 'Radno vrijeme nije sačuvano, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Radno vrijeme nije sačuvano zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Redoslijed kao java.time.DayOfWeek, od ponedjeljka
const DANI_SEDMICE = [
	['MONDAY', 'ponedjeljak'],
	['TUESDAY', 'utorak'],
	['WEDNESDAY', 'srijeda'],
	['THURSDAY', 'četvrtak'],
	['FRIDAY', 'petak'],
	['SATURDAY', 'subota'],
	['SUNDAY', 'nedjelja'],
]

// Svaki interval ima svoj ključ, da React ne pomiješa polja kad se jedan ukloni iz sredine
const sKljucem = (od, doKada) => ({ kljuc: crypto.randomUUID(), od, do: doKada })

function poDanima(intervali) {
	return Object.fromEntries(DANI_SEDMICE.map(([dan]) => [dan, intervali.filter((i) => i.dan === dan).map((i) => sKljucem(i.od, i.do))]))
}

// Cijela sedmica se šalje odjednom (PUT). Backend javlja greške po indeksu (rules[3].end), a
// mutacija pamti s čim je pozvana (variables), pa se po tom redu greška veže za pravi interval.
export function Sedmica({ resursId, intervali }) {
	const [dani, setDani] = useState(() => poDanima(intervali))
	const [prazni, setPrazni] = useState({})
	const queryClient = useQueryClient()

	const cuvanje = useMutation({
		mutationFn: (ravno) => sacuvajRadnoVrijeme(resursId, ravno),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['radnoVrijeme', resursId] })
			// Slobodni termini se računaju iz radnog vremena, pa i kalendar mora ponovo
			queryClient.invalidateQueries({ queryKey: ['kalendar'] })
		},
	})

	const poslano = (cuvanje.variables ?? []).map((i) => i.kljuc)
	const greske = { ...poIntervalima(greskePoPoljima(cuvanje.error), poslano), ...prazni }

	function promijeni(dan, kljuc, polje, vrijednost) {
		setDani((stari) => ({ ...stari, [dan]: stari[dan].map((i) => (i.kljuc === kljuc ? { ...i, [polje]: vrijednost } : i)) }))
	}

	function posalji(dogadjaj) {
		dogadjaj.preventDefault()
		const ravno = DANI_SEDMICE.flatMap(([dan]) => dani[dan].map((i) => ({ ...i, dan })))
		const bezSata = ravno.filter((i) => !i.od || !i.do)
		setPrazni(Object.fromEntries(bezSata.map((i) => [i.kljuc, 'Unesi početak i kraj.'])))
		if (bezSata.length > 0) {
			cuvanje.reset()
			return
		}
		cuvanje.mutate(ravno)
	}

	return (
		<form className="forma" onSubmit={posalji} noValidate>
			<h2>Radno vrijeme</h2>
			<p role="status">{cuvanje.isSuccess && 'Radno vrijeme je sačuvano. Kalendar od sada nudi termine po njemu, a rezervacije koje već postoje ostaju.'}</p>
			{cuvanje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(cuvanje.error, PORUKE)}
				</p>
			)}
			{DANI_SEDMICE.map(([dan, naziv]) => (
				<fieldset key={dan} className="dan-rada">
					<legend>{naziv}</legend>
					{dani[dan].length === 0 && <p className="napomena">Ne radi.</p>}
					{dani[dan].map((interval, n) => (
						<Interval
							key={interval.kljuc}
							opis={`${naziv}, ${n + 1}. interval`}
							interval={interval}
							greska={greske[interval.kljuc]}
							promjena={(polje, vrijednost) => promijeni(dan, interval.kljuc, polje, vrijednost)}
							ukloni={() => setDani((stari) => ({ ...stari, [dan]: stari[dan].filter((i) => i.kljuc !== interval.kljuc) }))}
						/>
					))}
					<button
						className="dugme-tekst"
						type="button"
						aria-label={`Dodaj interval: ${naziv}`}
						onClick={() => setDani((stari) => ({ ...stari, [dan]: [...stari[dan], sKljucem('08:00', '16:00')] }))}
					>
						Dodaj interval
					</button>
				</fieldset>
			))}
			<p className="napomena">Rad preko ponoći upiši kao dva intervala, do 23:59 i od 00:00 sljedećeg dana.</p>
			<button className="dugme" type="submit" disabled={cuvanje.isPending}>
				{cuvanje.isPending ? 'Radno vrijeme se čuva' : 'Sačuvaj radno vrijeme'}
			</button>
		</form>
	)
}

// "rules[3].end" i "rules[3]" -> ključ četvrtog poslanog intervala
function poIntervalima(poPoljima, kljucevi) {
	const rezultat = {}
	for (const [polje, poruka] of Object.entries(poPoljima)) {
		const indeks = polje.match(/^rules\[(\d+)\]/)?.[1]
		const kljuc = indeks === undefined ? undefined : kljucevi[Number(indeks)]
		if (kljuc !== undefined && rezultat[kljuc] === undefined) rezultat[kljuc] = poruka
	}
	return rezultat
}

function Interval({ opis, interval, greska, promjena, ukloni }) {
	const id = useId()
	return (
		<div role="group" aria-label={opis} className="interval">
			<Sat id={`${id}-od`} oznaka="od" opis={opis} vrijednost={interval.od} promjena={(v) => promjena('od', v)} greska={greska} />
			<Sat id={`${id}-do`} oznaka="do" opis={opis} vrijednost={interval.do} promjena={(v) => promjena('do', v)} greska={greska} />
			<button className="dugme-tekst" type="button" aria-label={`Ukloni ${opis}`} onClick={ukloni}>
				Ukloni
			</button>
			{greska && <p className="greska-polja interval-greska">{greska}</p>}
		</div>
	)
}

// Na ekranu piše samo "od", a čitač ekrana čuje i dan i interval
function Sat({ id, oznaka, opis, vrijednost, promjena, greska }) {
	return (
		<div className="polje">
			<label htmlFor={id}>
				<span className="skriveno">{opis}, </span>
				{oznaka}
			</label>
			<input id={id} type="time" value={vrijednost} onChange={(d) => promjena(d.target.value)} aria-invalid={greska ? 'true' : 'false'} />
		</div>
	)
}
