// Greška učitavanja: šta se nije učitalo i zašto, uz dugme za ponovni pokušaj.
export function NijeUcitano({ poruka, ponovo }) {
	return (
		<div className="forma">
			<p role="alert" className="greska-forme">
				{poruka}
			</p>
			<button className="dugme" type="button" onClick={ponovo}>
				Pokušaj ponovo
			</button>
		</div>
	)
}
