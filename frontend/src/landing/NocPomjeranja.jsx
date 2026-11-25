// Noć pomjeranja sata kao kartica: traka sati s rupom na 02, jer taj sat te noći ne postoji, a
// termin u 10:00 ostaje u 10:00. Ranije je ovdje bio klizač s UTC-om, a to vlasnika čitaonice ne zanima.
const SATI = ['00', '01', '02', '03', '04']

export function NocPomjeranja() {
	return (
		<article className="oslonac-kartica">
			<div className="noc-traka" aria-hidden="true">
				{SATI.map((sat) => (
					<span key={sat} className={sat === '02' ? 'noc-rupa' : undefined}>
						{sat}
					</span>
				))}
			</div>
			<h3>Vrijeme je uvijek tačno</h3>
			<p>I kad se sat pomjera na ljetno računanje vremena, termin u 10:00 ostaje u 10:00.</p>
		</article>
	)
}
