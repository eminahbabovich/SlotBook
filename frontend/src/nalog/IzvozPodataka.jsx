import { useMutation } from '@tanstack/react-query'
import { izvozPodataka } from '../api/nalog.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { sacuvajFajl } from '../sacuvajFajl.js'
import { danUZoni } from '../vrijeme/vrijeme.js'
import '../forma/forma.css'

const PORUKE = {
	serverNeOdgovara: 'Fajl nije preuzet, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Fajl nije preuzet zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Datum u imenu po zoni browsera: fajl je korisnikov, ne pripada nijednoj organizaciji
function imeFajla() {
	const dan = danUZoni(Date.now(), Intl.DateTimeFormat().resolvedOptions().timeZone)
	return `slotbook-moji-podaci-${dan}.json`
}

export function IzvozPodataka() {
	const preuzimanje = useMutation({
		mutationFn: izvozPodataka,
		onSuccess: (fajl) => sacuvajFajl(fajl, imeFajla()),
	})

	return (
		<section className="forma nalog-dio">
			<h2>Moji podaci</h2>
			<p>
				Jedan JSON fajl sa svim što SlotBook čuva o tebi: profil, članstva, rezervacije s historijom promjena, resursi koje
				vodiš, obavijesti bez teksta mailova, sesije, pozivnice, neuspjele prijave i tvoje radnje iz audit loga. Hashevi
				lozinke i tokena nisu u njemu.
			</p>
			{preuzimanje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(preuzimanje.error, PORUKE)}
				</p>
			)}
			<button className="dugme" type="button" onClick={() => preuzimanje.mutate()} disabled={preuzimanje.isPending}>
				{preuzimanje.isPending ? 'Fajl se priprema' : 'Preuzmi moje podatke (JSON)'}
			</button>
		</section>
	)
}
