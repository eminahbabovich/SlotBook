import './stanja.css'

// Greška učitavanja: šta se nije učitalo i zašto, uz dugme za ponovni pokušaj. Ploča lima s
// trakom u boji greške, da se ne izgubi na praznom ekranu.
export function NijeUcitano({ poruka, ponovo }) {
	return (
		<div className="nije-ucitano">
			<p role="alert" className="greska-forme">
				{poruka}
			</p>
			<button className="dugme" type="button" onClick={ponovo}>
				Pokušaj ponovo
			</button>
		</div>
	)
}
