import { porukaGreske } from '../forma/porukaGreske.js'
import { useSesija } from '../sesija/sesija.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { emajlPoRedu, inicijali } from '../resursi/emajl.js'
import { NovaOrganizacija } from './NovaOrganizacija.jsx'
import { ULOGE, useMojeOrganizacije } from './upiti.js'
import './organizacije.css'
import { PrelazLink } from '../tema/PrelazLink.jsx'

const PORUKE = {
	serverNeOdgovara: 'Organizacije se nisu učitale, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Organizacije se nisu učitale zbog greške na serveru. Pokušaj ponovo za minutu.',
}

export function Organizacije() {
	const { korisnik } = useSesija()
	const { data: organizacije, isPending, isError, error, refetch } = useMojeOrganizacije()

	return (
		<section>
			<h1>Organizacije</h1>
			{!korisnik.emailPotvrden && (
				<p className="napomena-blok">Email još nije potvrđen. Link za potvrdu je poslan na {korisnik.email} poslije registracije.</p>
			)}
			{isPending && <Ucitava tekst="Učitavaju se tvoje organizacije" />}
			{isError && <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />}
			{organizacije?.length === 0 && (
				<>
					<p className="prazno">Još nisi član nijedne organizacije. U postojeću te poziva njen admin, a link stiže mailom. Možeš napraviti i svoju.</p>
					<NovaOrganizacija />
				</>
			)}
			{organizacije?.length > 0 && (
				<ul className="tabla">
					{organizacije.map((o, i) => (
						<li key={o.id} style={{ '--emajl': emajlPoRedu(i) }}>
							<PrelazLink to={`/o/${o.slug}`} className="tabla-red" data-znak={inicijali(o.naziv)}>
								<span className="tabla-naziv" style={{ viewTransitionName: `org-${o.id}` }}>
									{o.naziv}
								</span>
								<span className="tabla-uloga">{ULOGE[o.uloga] ?? o.uloga}</span>
							</PrelazLink>
						</li>
					))}
				</ul>
			)}
		</section>
	)
}
