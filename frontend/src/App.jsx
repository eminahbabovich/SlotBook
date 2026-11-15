import { useState } from 'react'
import { Nalog } from './nalog/Nalog.jsx'
import { Prijava } from './prijava/Prijava.jsx'
import { Registracija } from './prijava/Registracija.jsx'
import { useSesija } from './sesija/sesija.js'
import { SesijaProvider } from './sesija/SesijaProvider.jsx'

export default function App() {
	return (
		<SesijaProvider>
			<header className="zaglavlje">
				<p className="znak">SlotBook</p>
			</header>
			<main>
				<Ekran />
			</main>
		</SesijaProvider>
	)
}

// Bez routera do koraka 5: ekran zavisi samo od stanja sesije.
function Ekran() {
	const { status, pokusajPonovo } = useSesija()
	const [registracija, setRegistracija] = useState(false)

	if (status === 'provjerava') {
		return <p aria-live="polite">Provjerava se prijava</p>
	}
	if (status === 'nedostupan') {
		return (
			<div className="forma">
				<p role="alert" className="greska-forme">
					Prijava se ne može provjeriti, server ne odgovara. Pokušaj ponovo za minutu.
				</p>
				<button className="dugme" type="button" onClick={pokusajPonovo}>
					Pokušaj ponovo
				</button>
			</div>
		)
	}
	if (status === 'prijavljen') {
		return <Nalog />
	}
	return registracija ? <Registracija naPrijavu={() => setRegistracija(false)} /> : <Prijava naRegistraciju={() => setRegistracija(true)} />
}
