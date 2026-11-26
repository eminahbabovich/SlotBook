import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { slobodnoUOrganizaciji } from '../api/termini.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { satUZoni } from '../vrijeme/vrijeme.js'
import { IZBORI, brziIzbor, poSatu } from './brziIzbori.js'

const PORUKE = {
	serverNeOdgovara: 'Slobodni termini se nisu učitali, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Slobodni termini se nisu učitali zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// 1, 21 mjesto, a 2, 5, 11 mjesta
const mjesta = (broj) => `${broj} ${broj % 10 === 1 && broj % 100 !== 11 ? 'mjesto' : 'mjesta'}`

// Slobodno u svim resursima odjednom, za dio dana koji odgovara. Ništa se ne učitava dok se ne
// izabere, jer većina ljudi na ovu stranicu dođe po jedan resurs.
export function NadjiSlobodno({ organizacija }) {
	const [izbor, setIzbor] = useState(null)
	const { data, isPending, isError, error, refetch } = useQuery({
		queryKey: ['slobodno', organizacija.id, izbor?.dan, izbor?.odSata, izbor?.doSata],
		queryFn: () => slobodnoUOrganizaciji(organizacija.id, izbor),
		enabled: izbor !== null,
		staleTime: 30_000,
	})
	const zona = organizacija.zona
	const naziv = IZBORI.find((i) => i.id === izbor?.id)?.naziv

	return (
		<section className="slobodno" aria-labelledby="slobodno-naslov">
			<h2 id="slobodno-naslov">Nađi slobodno</h2>
			<p className="slobodno-uvod">Kad ti odgovara? Pokazat će se slobodni termini svih resursa odjednom.</p>
			<div className="slobodno-izbori" role="group" aria-label="Kad ti odgovara">
				{IZBORI.map((i) => (
					<button
						key={i.id}
						type="button"
						className="veza-pilula"
						aria-pressed={izbor?.id === i.id}
						// Vrijeme se uzima u trenutku klika: "Sad" u 16:59 i u 17:01 nije isto
						onClick={() => setIzbor(brziIzbor(i.id, new Date(), zona))}
					>
						{i.naziv}
					</button>
				))}
			</div>
			{izbor !== null && isPending && <Ucitava tekst="Traže se slobodni termini" />}
			{izbor !== null && isError && <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />}
			{data && data.termini.length === 0 && (
				<p className="prazno slobodno-prazno">
					{naziv} nema slobodnih termina ni u jednom resursu. Probaj drugi dio dana ili otvori kalendar resursa ispod.
				</p>
			)}
			{data && data.termini.length > 0 && (
				<ol className="slobodno-lista" aria-label={`Slobodno: ${naziv}`}>
					{poSatu(data.termini).map((red) => (
						<li key={red.pocetak} className="slobodno-red">
							<span className="slobodno-sat">{satUZoni(red.pocetak, zona)}</span>
							<ul className="slobodno-resursi">
								{red.termini.map((t) => (
									<li key={t.resursId}>
										<Link
											className="slobodno-termin"
											to={`/o/${organizacija.slug}/r/${t.resursId}?termin=${encodeURIComponent(t.pocetak)}`}
										>
											<span className="slobodno-resurs">{t.resurs}</span>
											<span className="slobodno-detalj">
												do {satUZoni(t.kraj, zona)}
												{t.mjesta !== null && `, još ${mjesta(t.mjesta)}`}
											</span>
										</Link>
									</li>
								))}
							</ul>
						</li>
					))}
				</ol>
			)}
		</section>
	)
}
