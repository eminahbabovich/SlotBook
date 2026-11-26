import { useRef, useState } from 'react'
import { sacuvajFajl } from '../sacuvajFajl.js'
import { brojKarte } from './brojKarte.js'

// Karta se može sačuvati kao PNG, da se pokaže na ulazu i bez interneta. html-to-image se učitava tek
// na klik: većina ljudi kartu nikad ne sačuva, pa ne treba da je plaća svako ko otvori kalendar.
export function SacuvajKartu({ id, children }) {
	const okvir = useRef(null)
	const { stanje, sacuvaj } = useSlikaKarte(id)

	return (
		<div className="sacuvaj-kartu" ref={okvir}>
			{children}
			<button className="dugme-tekst" type="button" onClick={() => sacuvaj(okvir.current.querySelector('.karta'))} disabled={stanje === 'pravi'}>
				{stanje === 'pravi' ? 'Pravi se slika...' : 'Sačuvaj kartu kao sliku'}
			</button>
			{stanje === 'greska' && <GreskaSlike />}
		</div>
	)
}

// Isto, ali kao ikona u redu radnji unutar karte (Moje rezervacije). Karta je predak dugmeta, a red
// radnji se ne vidi na slici (karta.css, data-slika).
export function SacuvajKartuIkona({ id }) {
	const { stanje, sacuvaj } = useSlikaKarte(id)
	const naziv = stanje === 'pravi' ? 'Pravi se slika...' : 'Sačuvaj kartu kao sliku'
	return (
		<>
			<button
				className="dugme-ikona"
				type="button"
				aria-label={naziv}
				title={naziv}
				onClick={(d) => sacuvaj(d.currentTarget.closest('.karta'))}
				disabled={stanje === 'pravi'}
			>
				<svg viewBox="0 0 24 24" aria-hidden="true">
					<path d="M12 4v11M7 10.5l5 5 5-5M5 19.5h14" />
				</svg>
			</button>
			{stanje === 'greska' && <GreskaSlike />}
		</>
	)
}

function GreskaSlike() {
	return (
		<p role="alert" className="greska-forme">
			Slika se nije napravila. Napravi snimak ekrana, karta na njemu vrijedi isto.
		</p>
	)
}

function useSlikaKarte(id) {
	const [stanje, setStanje] = useState('miruje')

	async function sacuvaj(karta) {
		setStanje('pravi')
		// html-to-image kopira izračunate stilove: bez ovoga bi se u kopiji ponovo pokrenula animacija
		// štampe (karta odsječena), a pozadina sa zrnom se ne prenese (tijelo prozirno). Vidi karta.css.
		karta.dataset.slika = ''
		try {
			const { toBlob } = await import('html-to-image')
			// Dvostruka gustina, da je karta oštra i na ekranu telefona
			const slika = await toBlob(karta, { pixelRatio: 2 })
			if (!slika) throw new Error('prazna slika')
			sacuvajFajl(slika, `karta-${brojKarte(id)}.png`)
			setStanje('miruje')
		} catch {
			setStanje('greska')
		} finally {
			delete karta.dataset.slika
		}
	}

	return { stanje, sacuvaj }
}
