import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/apiFetch.js'
import { pregledPozivnogLinka, pridruziSe } from '../api/clanovi.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { KLJUC_MOJE_ORGANIZACIJE } from '../organizacije/upiti.js'
import { useSesija } from '../sesija/sesija.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import '../forma/forma.css'
import '../clanovi/clanovi.css'

const PORUKE_PREGLEDA = {
	serverNeOdgovara: 'Pozivni link se nije učitao, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Pozivni link se nije učitao zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE = {
	serverNeOdgovara: 'Ulazak nije uspio, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Ulazak nije uspio zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Ne postoji, povučen, istekao, popunjen: ponovni klik bi dao isti odgovor. 403 (nepotvrđen email)
// nije ovdje: kad osoba potvrdi email, isto dugme proradi.
const KONACNE = [404, 409, 410]

// /pridruzi/{token} s plakata ili iz grupnog chata. Van čuvara: ime organizacije vidi i ko još nema
// nalog, pa zna gdje ulazi prije nego što ga napravi. Poslije prijave se vraća ovdje.
export function Pridruzivanje() {
	const { token } = useParams()
	const { pathname } = useLocation()
	const { status } = useSesija()
	const pregled = useQuery({
		queryKey: ['pozivniLink', token],
		queryFn: () => pregledPozivnogLinka(token),
		retry: (broj, greska) => !(greska instanceof ApiError && greska.status === 404) && broj < 2,
	})

	if (pregled.isPending || status === 'provjerava') return <Ucitava tekst="Učitava se pozivni link" />
	if (pregled.isError) {
		if (pregled.error instanceof ApiError && pregled.error.status === 404) {
			return (
				<section>
					<h1>Link ne postoji</h1>
					<p>Provjeri da li je kopiran cijeli link ili skeniraj kod ponovo. Ako i dalje ne radi, zamoli admina za novi.</p>
					<Link to="/">Naslovna</Link>
				</section>
			)
		}
		return <NijeUcitano poruka={porukaGreske(pregled.error, PORUKE_PREGLEDA)} ponovo={() => pregled.refetch()} />
	}

	const { naziv, upotrebljiv } = pregled.data
	if (!upotrebljiv) {
		return (
			<section>
				<h1>{naziv}</h1>
				<p>Ovaj pozivni link više ne radi: povučen je, istekao ili je popunjen. Zamoli admina organizacije za novi.</p>
				<Link to="/">Naslovna</Link>
			</section>
		)
	}

	return (
		<section>
			<h1>{naziv}</h1>
			<p>Ova organizacija te poziva da rezervišeš termine. Ulaziš kao klijent: vidiš resurse i biraš slobodne termine.</p>
			{status === 'prijavljen' ? (
				<Potvrda token={token} />
			) : (
				<div className="forma">
					<p className="napomena">Za ulazak treba nalog s potvrđenim emailom. Poslije prijave se vraćaš ovdje.</p>
					<div className="radnje-linka">
						<Link className="dugme" to="/prijava" state={{ odakle: pathname }}>
							Prijavi se
						</Link>
						<Link to="/registracija" state={{ odakle: pathname }}>
							Napravi nalog
						</Link>
					</div>
				</div>
			)}
		</section>
	)
}

function Potvrda({ token }) {
	const queryClient = useQueryClient()
	const navigate = useNavigate()
	const { korisnik } = useSesija()
	// Na klik, ne pri otvaranju: link s plakata otvori i neko ko samo gleda gdje vodi
	const ulazak = useMutation({
		mutationFn: () => pridruziSe(token),
		onSuccess: async ({ slug, vecClan }) => {
			await queryClient.invalidateQueries({ queryKey: KLJUC_MOJE_ORGANIZACIJE, refetchType: 'all' })
			if (!vecClan) navigate(`/o/${slug}`, { replace: true })
		},
	})

	if (ulazak.isSuccess && ulazak.data.vecClan) {
		return (
			<div className="forma">
				<p role="status">Već si član ove organizacije, ništa se nije promijenilo.</p>
				<Link className="dugme" to={`/o/${ulazak.data.slug}`}>
					Otvori {ulazak.data.naziv}
				</Link>
			</div>
		)
	}

	const konacna = ulazak.error instanceof ApiError && KONACNE.includes(ulazak.error.status)
	return (
		<div className="forma">
			<p className="napomena">Ulaziš kao {korisnik.email}.</p>
			{ulazak.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(ulazak.error, PORUKE)}
				</p>
			)}
			{konacna ? (
				<Link to="/">Tvoje organizacije</Link>
			) : (
				<button className="dugme" type="button" onClick={() => ulazak.mutate()} disabled={ulazak.isPending}>
					{ulazak.isPending ? 'Ulaziš' : 'Uđi u organizaciju'}
				</button>
			)}
		</div>
	)
}
