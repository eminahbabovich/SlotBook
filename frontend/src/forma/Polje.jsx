import { useId } from 'react'
import './forma.css'

// Oznaka, polje i tekst ispod njega. aria-describedby veže napomenu i grešku za polje,
// pa ih čitač ekrana pročita kad korisnik uđe u polje.
export function Polje({ oznaka, ime, tip = 'text', vrijednost, promjena, greska, napomena, autoComplete }) {
	const id = useId()
	const napomenaId = `${id}-napomena`
	const greskaId = `${id}-greska`
	const opis = [napomena && napomenaId, greska && greskaId].filter(Boolean).join(' ')

	return (
		<div className="polje">
			<label htmlFor={id}>{oznaka}</label>
			<input
				id={id}
				name={ime}
				type={tip}
				value={vrijednost}
				onChange={(dogadjaj) => promjena(dogadjaj.target.value)}
				autoComplete={autoComplete}
				aria-invalid={greska ? 'true' : 'false'}
				aria-describedby={opis || undefined}
			/>
			{napomena && (
				<p id={napomenaId} className="napomena">
					{napomena}
				</p>
			)}
			{greska && (
				<p id={greskaId} className="greska-polja">
					{greska}
				</p>
			)}
		</div>
	)
}
