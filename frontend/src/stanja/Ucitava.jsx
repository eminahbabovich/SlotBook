import './stanja.css'

// Učitavanje bilo čega osim termina (kalendar ima svoj kostur): tri ploče koje polako "dišu", u
// obliku liste kakva će stići, a čitač ekrana čuje šta se učitava.
export function Ucitava({ tekst }) {
	return (
		<div className="ucitava">
			<p className="skriveno" aria-live="polite">
				{tekst}
			</p>
			<div className="kostur" aria-hidden="true">
				<span />
				<span />
				<span />
			</div>
		</div>
	)
}
