import { PrelazLink } from './PrelazLink.jsx'

// Put do ekrana: "Organizacije / Čitaonica PMF". Zadnja stavka je ekran na kojem si, pa nije link.
// stavke: [{ naziv, to }], a zadnja nema to
export function Mrvice({ stavke }) {
	return (
		<nav className="mrvice" aria-label="Put do ove stranice">
			<ol>
				{stavke.map(({ naziv, to }) => (
					<li key={naziv}>{to ? <PrelazLink to={to}>{naziv}</PrelazLink> : <span aria-current="page">{naziv}</span>}</li>
				))}
			</ol>
		</nav>
	)
}
