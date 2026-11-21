import { useMutation } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router'
import { ApiError } from '../api/apiFetch.js'
import { potvrdiEmail } from '../api/auth.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { NoviLinkZaPotvrdu } from './NoviLinkZaPotvrdu.jsx'
import '../forma/forma.css'

const PORUKE = {
	serverNeOdgovara: 'Email nije potvrđen, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Email nije potvrđen zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Ne postoji, već iskorišten, istekao: isti link bi opet dao isti odgovor, pa ostaje samo novi link
const KONACNE = [404, 409, 410]

// Stranica /potvrda-emaila#token=... iz maila. Van čuvara, jer je otvara neko ko se još ne
// može prijaviti. Potvrda tek na klik: StrictMode bi pri otvaranju poslao dva zahtjeva, a drugi
// bi dobio 409 "već iskorišten".
export function PotvrdaEmaila() {
	const { hash } = useLocation()
	const token = new URLSearchParams(hash.slice(1)).get('token')
	const potvrda = useMutation({ mutationFn: () => potvrdiEmail(token) })

	if (!token) {
		return (
			<section>
				<h1>Potvrda emaila</h1>
				<p>Link nije potpun, nedostaje mu dio iza znaka #. Otvori ga ponovo iz maila ili zatraži novi.</p>
				<NoviLinkZaPotvrdu />
			</section>
		)
	}

	if (potvrda.isSuccess) {
		return (
			<section>
				<h1>Potvrda emaila</h1>
				<div className="forma">
					<p role="status">Email je potvrđen. Sada se možeš prijaviti.</p>
					<Link to="/prijava">Prijavi se</Link>
				</div>
			</section>
		)
	}

	const konacna = potvrda.error instanceof ApiError && KONACNE.includes(potvrda.error.status)
	return (
		<section>
			<h1>Potvrda emaila</h1>
			<p>Potvrdom pokazuješ da je email tvoj. Poslije toga se možeš prijaviti.</p>
			<div className="forma">
				{potvrda.isError && (
					<p role="alert" className="greska-forme">
						{porukaGreske(potvrda.error, PORUKE)}
					</p>
				)}
				{!konacna && (
					<button className="dugme" type="button" onClick={() => potvrda.mutate()} disabled={potvrda.isPending}>
						{potvrda.isPending ? 'Potvrda u toku' : 'Potvrdi email'}
					</button>
				)}
			</div>
			{konacna && <NoviLinkZaPotvrdu />}
		</section>
	)
}
