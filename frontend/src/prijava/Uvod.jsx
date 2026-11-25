import { useState } from 'react'
import { Karta } from '../karta/Karta.jsx'
import { danUZoni, dodajDane, uTrenutakSPomakom } from '../vrijeme/vrijeme.js'
import './prijava.css'

const ZONA = 'Europe/Sarajevo'

// Prijava je prvi ekran za odjavljene (/ vodi ovamo), pa kaže šta je SlotBook i pokazuje šta se
// dobije. Primjer je uvijek za sutra u 10, da karta ne izgleda kao zaboravljena stara rezervacija.
export function Uvod() {
	const [termin] = useState(() => {
		const sutra = dodajDane(danUZoni(new Date(), ZONA), 1)
		return { pocetak: uTrenutakSPomakom(sutra, '10:00', ZONA), kraj: uTrenutakSPomakom(sutra, '11:00', ZONA) }
	})

	return (
		<div className="uvod">
			{/* Isti naslov kao na landingu, ovdje mali: prijava je nastavak iste priče, ne nova stranica */}
			<p className="uvod-naslov" aria-hidden="true">
				<span>Termin</span> <span className="uvod-naslov-mjed">je tvoj.</span>
			</p>
			<p className="uvod-recenica">
				Sto u čitaonici, konsultacije, sala za trening. Termin rezervišeš za pola minute, a kad dvoje klikne u istoj sekundi, dobije ga
				tačno jedno.
			</p>
			<figure className="uvod-primjer">
				<Karta
					id="c16c3710"
					pocetak={termin.pocetak}
					kraj={termin.kraj}
					zona={ZONA}
					resurs="Sto 4, tihi dio"
					mjesto="Čitaonica PMF"
					zig="potvrđena"
					nagib
				/>
				<figcaption>Primjer karte. Ovakvu dobiješ poslije rezervacije.</figcaption>
			</figure>
		</div>
	)
}
