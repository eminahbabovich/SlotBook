import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { ekraniOrganizacije, napraviEkran, ugasiEkran } from '../api/ekran.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { useResursi } from '../organizacije/upiti.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { opisTrenutka } from '../vrijeme/opis.js'
import '../clanovi/clanovi.css'

const PORUKE = {
	serverNeOdgovara: 'Ekrani se nisu učitali, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Ekrani se nisu učitali zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_IZMJENE = {
	serverNeOdgovara: 'Promjena nije sačuvana, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Promjena nije sačuvana zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const CIJELA = ''

// Tablet ili TV na vratima čitaonice koji pokazuje današnje termine bez prijave. Link je tajna:
// ko ga ima, vidi termine (bez imena), pa se gasi kad tablet ode ili link procuri.
export function EkranNaVratima({ organizacija }) {
	const queryClient = useQueryClient()
	const kljuc = ['ekrani', organizacija.id]
	const ekrani = useQuery({ queryKey: kljuc, queryFn: () => ekraniOrganizacije(organizacija.id) })
	const resursi = useResursi(organizacija)
	const [mjesto, setMjesto] = useState(CIJELA)
	const izbor = useId()
	const pravljenje = useMutation({
		mutationFn: () => napraviEkran(organizacija.id, mjesto || null),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: kljuc }),
	})
	const aktivni = (resursi.data ?? []).filter((r) => r.aktivan)
	const zamjenjuje = ekrani.data?.some((e) => (e.resursId ?? CIJELA) === mjesto)

	return (
		<section className="forma" aria-labelledby={`${izbor}-naslov`}>
			<h2 id={`${izbor}-naslov`}>Ekran na vratima</h2>
			<p className="napomena">
				Otvori link na tabletu ili TV-u pored vrata: pokazuje sat, termin koji je u toku, sljedeći i ostatak dana, i osvježava se sam. Imena se ne vide. Ako
				postoji pozivni link, na ekranu je i njegov QR kod.
			</p>
			{ekrani.isError ? (
				<NijeUcitano poruka={porukaGreske(ekrani.error, PORUKE)} ponovo={() => ekrani.refetch()} />
			) : ekrani.isPending ? (
				<p role="status">Učitavaju se ekrani</p>
			) : ekrani.data.length === 0 ? (
				<p>Još nema ekrana.</p>
			) : (
				<ul className="lista-ekrana" aria-label="Ekrani koji rade">
					{ekrani.data.map((e) => (
						<Ekran key={e.id} ekran={e} organizacija={organizacija} kljuc={kljuc} />
					))}
				</ul>
			)}
			<div className="polje">
				<label htmlFor={izbor}>Šta ekran pokazuje</label>
				<select id={izbor} value={mjesto} onChange={(d) => setMjesto(d.target.value)}>
					<option value={CIJELA}>Sve resurse organizacije</option>
					{aktivni.map((r) => (
						<option key={r.id} value={r.id}>
							Samo {r.naziv}
						</option>
					))}
				</select>
				{zamjenjuje && <p className="napomena">Za ovo već postoji ekran. Novi link ga zamjenjuje, a stari prestaje raditi.</p>}
			</div>
			{pravljenje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(pravljenje.error, PORUKE_IZMJENE)}
				</p>
			)}
			<div>
				<button className="dugme" type="button" onClick={() => pravljenje.mutate()} disabled={pravljenje.isPending}>
					{pravljenje.isPending ? 'Ekran se pravi' : zamjenjuje ? 'Napravi novi link' : 'Napravi ekran'}
				</button>
			</div>
		</section>
	)
}

function Ekran({ ekran, organizacija, kljuc }) {
	const queryClient = useQueryClient()
	const polje = useId()
	const [pitaj, setPitaj] = useState(false)
	const [kopirano, setKopirano] = useState(null)
	const gasenje = useMutation({
		mutationFn: () => ugasiEkran(organizacija.id, ekran.id),
		onSettled: () => {
			setPitaj(false)
			queryClient.invalidateQueries({ queryKey: kljuc })
		},
	})
	const naziv = ekran.resurs ?? 'Svi resursi'

	async function kopiraj() {
		try {
			await navigator.clipboard.writeText(ekran.adresa)
			setKopirano('Link je kopiran.')
		} catch {
			setKopirano('Kopiranje nije uspjelo. Označi link u polju i kopiraj ga ručno.')
		}
	}

	return (
		<li className="ekran-stavka">
			<p className="potvrda-pitanje">{naziv}</p>
			<p className="napomena">Napravljen {opisTrenutka(ekran.napravljen, organizacija.zona)}.</p>
			<div className="polje">
				<label htmlFor={polje}>Link ekrana: {naziv}</label>
				<input id={polje} type="text" readOnly value={ekran.adresa} onFocus={(d) => d.target.select()} />
			</div>
			<div className="radnje-linka">
				<a className="dugme" href={ekran.adresa} target="_blank" rel="noreferrer">
					Otvori ekran
				</a>
				<button className="dugme-tekst" type="button" onClick={kopiraj}>
					Kopiraj link
				</button>
				<span aria-live="polite">{kopirano}</span>
			</div>
			{gasenje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(gasenje.error, PORUKE_IZMJENE)}
				</p>
			)}
			{pitaj ? (
				<div className="potvrda-dugmad" role="group" aria-label={`Gašenje ekrana: ${naziv}`}>
					<p className="potvrda-pitanje">Ugasiti ekran? Tablet s ovim linkom odmah prestaje prikazivati termine.</p>
					<button className="dugme" type="button" onClick={() => gasenje.mutate()} disabled={gasenje.isPending}>
						{gasenje.isPending ? 'Ekran se gasi' : 'Ugasi ekran'}
					</button>
					<button className="dugme-tekst" type="button" onClick={() => setPitaj(false)}>
						Ne
					</button>
				</div>
			) : (
				<button className="dugme-tekst" type="button" onClick={() => setPitaj(true)}>
					Ugasi
				</button>
			)}
		</li>
	)
}
