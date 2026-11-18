import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/apiFetch.js'
import { prihvatiPozivnicu } from '../api/clanovi.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { KLJUC_MOJE_ORGANIZACIJE } from '../organizacije/upiti.js'
import { useSesija } from '../sesija/sesija.js'
import '../forma/forma.css'

const PORUKE = {
	serverNeOdgovara: 'Pozivnica nije prihvaćena, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Pozivnica nije prihvaćena zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Ne postoji, drugi email, već iskorištena, istekla: ponovni klik bi dao isti odgovor.
const KONACNE = [403, 404, 409, 410]

// Stranica /pozivnica#token=... iz maila. Token je iza #, pa ga browser ne šalje serveru
// koji servira frontend i ne ulazi u Referer.
export function PrihvatanjePozivnice() {
	const { hash } = useLocation()
	const token = new URLSearchParams(hash.slice(1)).get('token')
	const { korisnik } = useSesija()
	const queryClient = useQueryClient()
	const navigate = useNavigate()

	// Prihvata se tek na klik. Kad bi se prihvatalo pri otvaranju, StrictMode bi u razvoju
	// poslao dva zahtjeva, a drugi bi dobio 409 "već iskorištena".
	const prihvatanje = useMutation({
		mutationFn: () => prihvatiPozivnicu(token),
		// Lista se osvježi prije prelaska, za slučaj da je već u kešu bez nove organizacije.
		// refetchType 'all', jer je na ovoj stranici lista neaktivna i inače se ne bi učitala.
		// Link iz maila otvara aplikaciju od nule, pa je keš prazan i test ovo ne može uhvatiti.
		onSuccess: async ({ slug }) => {
			await queryClient.invalidateQueries({ queryKey: KLJUC_MOJE_ORGANIZACIJE, refetchType: 'all' })
			// replace: token ne ostaje u historiji, pa ga dugme "nazad" ne vraća
			navigate(`/o/${slug}`, { replace: true })
		},
	})

	if (!token) {
		return (
			<section>
				<h1>Pozivnica</h1>
				<p>Link nije potpun, nedostaje mu dio iza znaka #. Otvori ga ponovo iz maila ili ga kopiraj cijelog.</p>
				<Link to="/">Tvoje organizacije</Link>
			</section>
		)
	}

	const konacna = prihvatanje.error instanceof ApiError && KONACNE.includes(prihvatanje.error.status)
	return (
		<section>
			<h1>Pozivnica</h1>
			<p>
				Kad prihvatiš pozivnicu, postaješ član organizacije koja te pozvala, s ulogom iz pozivnice. Važi samo za nalog s
				emailom na koji je poslana. Prijavljen je nalog {korisnik.email}.
			</p>
			<div className="forma">
				{prihvatanje.isError && (
					<p role="alert" className="greska-forme">
						{porukaGreske(prihvatanje.error, PORUKE)}
					</p>
				)}
				{konacna ? (
					<Link to="/">Tvoje organizacije</Link>
				) : (
					<button className="dugme" type="button" onClick={() => prihvatanje.mutate()} disabled={prihvatanje.isPending}>
						{prihvatanje.isPending ? 'Pozivnica se prihvata' : 'Prihvati pozivnicu'}
					</button>
				)}
			</div>
		</section>
	)
}
