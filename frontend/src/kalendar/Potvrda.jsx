import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useRef } from 'react'
import { ApiError } from '../api/apiFetch.js'
import { rezervisi } from '../api/rezervacije.js'
import { porukaGreske, serverNeOdgovara } from '../forma/porukaGreske.js'
import { danUZoni } from '../vrijeme/vrijeme.js'
import { opisTermina, rokPonude } from '../vrijeme/opis.js'

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
	const dugme = useRef(null)

	const slanje = useMutation({
		mutationFn: () => rezervisi(resursId, termin.pocetak, kljuc),
		onSuccess: (rezervacija) => {
			queryClient.invalidateQueries({ queryKey: ['kalendar', resursId] })
			// Rezervisan termin s liste čekanja više nije na listi
			queryClient.invalidateQueries({ queryKey: ['listaCekanja'] })
			naUspjeh(rezervacija)
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

	const greska409 = zauzet(slanje.error)
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
			{slanje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(slanje.error, PORUKE)}
				</p>
			)}
			<div className="potvrda-dugmad">
				{!greska409 && (
					<button ref={dugme} className="dugme" type="button" onClick={() => slanje.mutate()} disabled={slanje.isPending}>
						{slanje.isPending ? 'Rezervacija se šalje' : serverNeOdgovara(slanje.error) ? 'Pokušaj ponovo' : 'Rezerviši'}
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

function zauzet(greska) {
	return greska instanceof ApiError && greska.status === 409
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
