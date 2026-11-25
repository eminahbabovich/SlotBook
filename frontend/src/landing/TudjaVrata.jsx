// Izolacija organizacija kao kartica: dvije ploče i zid između njih. Ranije je ovdje bila animacija
// s API rutama i statusom 403, a to je za README, ne za vlasnika teretane.
export function TudjaVrata() {
	return (
		<article className="oslonac-kartica kartica">
			<div className="vrata" aria-hidden="true">
				<span className="vrata-org vrata-teretana">Teretana Ilidža</span>
				<span className="vrata-zid" />
				<span className="vrata-org vrata-citaonica">Čitaonica PMF</span>
			</div>
			<h3>Svako vidi samo svoje</h3>
			<p>
				Teretana <strong>ne može vidjeti ni dirati</strong> rezervacije čitaonice, i obrnuto.
			</p>
		</article>
	)
}
