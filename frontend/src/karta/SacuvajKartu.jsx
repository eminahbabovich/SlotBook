import { useRef, useState } from 'react'
import { sacuvajFajl } from '../sacuvajFajl.js'
import { brojKarte } from './brojKarte.js'

// Karta se može sačuvati kao PNG, da se pokaže na ulazu i bez interneta. html-to-image se učitava tek
// na klik: većina ljudi kartu nikad ne sačuva, pa ne treba da je plaća svako ko otvori kalendar.
export function SacuvajKartu({ id, children }) {
	const okvir = useRef(null)
	const [stanje, setStanje] = useState('miruje')

	async function sacuvaj() {
		setStanje('pravi')
		const karta = okvir.current.querySelector('.karta')
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

	return (
		<div className="sacuvaj-kartu" ref={okvir}>
			{children}
			<button className="dugme-tekst" type="button" onClick={sacuvaj} disabled={stanje === 'pravi'}>
				{stanje === 'pravi' ? 'Pravi se slika...' : 'Sačuvaj kartu kao sliku'}
			</button>
			{stanje === 'greska' && (
				<p role="alert" className="greska-forme">
					Slika se nije napravila. Napravi snimak ekrana, karta na njemu vrijedi isto.
				</p>
			)}
		</div>
	)
}
