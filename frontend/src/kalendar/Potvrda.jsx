import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useRef, useState } from 'react'
import { ApiError } from '../api/apiFetch.js'
import { rezervisi, rezervisiSeriju } from '../api/rezervacije.js'
import { porukaGreske, serverNeOdgovara } from '../forma/porukaGreske.js'
import { danUZoni, nazivDana } from '../vrijeme/vrijeme.js'
import { opisTermina, rokPonude } from '../vrijeme/opis.js'

const UPRAVO_ZAUZET = 'Neko je upravo rezervisao ovaj termin. Izaberi drugi slobodan termin.'

const PORUKE = {
	serverNeOdgovara:
		'Nije jasno da li je rezervacija stigla, server ne odgovara. Pokušaj ponovo: ako je stigla, neće se napraviti dvaput.',
	opsta: 'Rezervacija nije uspjela zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Potvrda ispod izabranog termina. kljuc (Idempotency-Key) je napravljen kad je termin izabran,
// pa ga nose i dupli klik i "Pokušaj ponovo". Drugi termin je nova namjera i dobija novi ključ.
export function Potvrda({ resursId, kljucKalendara, termin, kljuc, zona, termini, naOdustani, naIzbor, naUspjeh }) {
	const queryClient = useQueryClient()
	const naslov = useId()
	const poljeSedmica = useId()
	const dugme = useRef(null)
	const [sedmica, setSedmica] = useState(1)

	const slanje = useMutation({
		// Ključ veže i broj sedmica: druga serija istog termina je nova namjera, a ne ponovljen zahtjev
		mutationFn: () =>
			sedmica === 1
				? rezervisi(resursId, termin.pocetak, kljuc).then((rezervacija) => [rezervacija])
				: rezervisiSeriju(resursId, termin.pocetak, sedmica, `${kljuc}-${sedmica}`),
		onSuccess: (rezervacije) => {
			queryClient.invalidateQueries({ queryKey: ['kalendar', resursId] })
			// Rezervisan termin s liste čekanja više nije na listi
			queryClient.invalidateQueries({ queryKey: ['listaCekanja'] })
			naUspjeh(rezervacije)
		},
		onError: (greska) => {
			if (!zauzet(greska)) return
			// Termin je zauzet odmah, prije nego stigne nova lista: korisnik ne smije opet kliknuti na njega.
			queryClient.setQueryData(kljucKalendara, (stari) =>
				stari && { ...stari, termini: stari.termini.map((t) => (t.pocetak === termin.pocetak ? { ...t, stanje: 'zauzeto' } : t)) },
			)
			queryClient.invalidateQueries({ queryKey: ['kalendar', resursId] })
		},
	})

	useEffect(() => {
		dugme.current?.focus()
	}, [])

	// Zauzet je i kad je 409 stigao na klik, i kad je kalendar preko SSE saznao prije klika
	const upravoZauzet = !slanje.isError && !['slobodno', 'ponudjen'].includes(termin.stanje)
	const greska409 = zauzet(slanje.error) || upravoZauzet
	const nisuSlobodne = zauzeteSedmice(slanje.error)
	const najblizi = greska409 ? najbliziSlobodan(termini, termin) : null

	return (
		<div
			className="potvrda"
			role="group"
			aria-labelledby={naslov}
			onKeyDown={(d) => {
				if (d.key === 'Escape') naOdustani()
			}}
		>
			<p id={naslov} className="potvrda-pitanje">
				{greska409 ? `Termin ${opisTermina(termin, zona)} je zauzet.` : `Rezervisati ${opisTermina(termin, zona)}?`}
			</p>
			{termin.stanje === 'ponudjen' && !greska409 && (
				<p className="napomena">
					Termin se oslobodio i ponuđen je tebi s liste čekanja {rokPonude(termin.rokPonude, zona)}. Poslije toga ide sljedećem
					na listi.
				</p>
			)}
			{(slanje.isError || upravoZauzet) && (
				<p role="alert" className="greska-forme">
					{upravoZauzet ? UPRAVO_ZAUZET : porukaGreske(slanje.error, PORUKE)}
					{nisuSlobodne && ` Nisu slobodne: ${nisuSlobodne.map((t) => nazivDana(danUZoni(t, zona))).join('; ')}.`}
				</p>
			)}
			{!greska409 && (
				<div className="polje">
					<label htmlFor={poljeSedmica}>Ponavljanje</label>
					<select
						id={poljeSedmica}
						value={sedmica}
						onChange={(d) => {
							setSedmica(Number(d.target.value))
							// Stara greška se odnosila na drugi broj sedmica
							slanje.reset()
						}}
					>
						<option value={1}>Samo ovaj put</option>
						{[2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map((n) => (
							<option key={n} value={n}>
								Svake sedmice, {n} puta
							</option>
						))}
					</select>
				</div>
			)}
			<div className="potvrda-dugmad">
				{!greska409 && (
					<button ref={dugme} className="dugme" type="button" onClick={() => slanje.mutate()} disabled={slanje.isPending}>
						{slanje.isPending
							? 'Rezervacija se šalje'
							: serverNeOdgovara(slanje.error)
								? 'Pokušaj ponovo'
								: sedmica === 1
									? 'Rezerviši'
									: `Rezerviši ${sedmica} termina`}
					</button>
				)}
				{greska409 &&
					(najblizi ? (
						<button className="dugme" type="button" onClick={() => naIzbor(najblizi)}>
							Najbliži slobodan: {opisTermina(najblizi, zona, { bezDana: istiDan(najblizi, termin, zona) })}
						</button>
					) : (
						<p>U ovih 7 dana nema drugog slobodnog termina.</p>
					))}
				<button className="dugme-tekst" type="button" onClick={naOdustani}>
					{greska409 ? 'Zatvori' : 'Odustani'}
				</button>
			</div>
		</div>
	)
}

// 409 za jedan termin. Serija ima svoj 409 (zauzetiTermini): tada izabrani termin može biti slobodan.
function zauzet(greska) {
	return greska instanceof ApiError && greska.status === 409 && zauzeteSedmice(greska) === null
}

function zauzeteSedmice(greska) {
	const termini = greska instanceof ApiError && greska.status === 409 ? greska.problem?.zauzetiTermini : null
	return Array.isArray(termini) ? termini : null
}

// Najbliži po vremenu, prije ili poslije. Kod istog razmaka pobjeđuje kasniji, jer je raniji
// možda već prošao dok korisnik čita poruku.
function najbliziSlobodan(termini, zauzetTermin) {
	const odmak = (t) => Math.abs(Date.parse(t.pocetak) - Date.parse(zauzetTermin.pocetak))
	return (
		termini
			.filter((t) => ['slobodno', 'ponudjen'].includes(t.stanje) && t.pocetak !== zauzetTermin.pocetak)
			.sort((a, b) => odmak(a) - odmak(b) || b.pocetak.localeCompare(a.pocetak))[0] ?? null
	)
}

function istiDan(a, b, zona) {
	return danUZoni(a.pocetak, zona) === danUZoni(b.pocetak, zona)
}
