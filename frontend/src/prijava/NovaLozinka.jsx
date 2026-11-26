import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { ApiError } from '../api/apiFetch.js'
import { postaviNovuLozinku } from '../api/auth.js'
import { Polje } from '../forma/Polje.jsx'
import { NAPOMENA_LOZINKE, problemLozinke } from '../forma/lozinka.js'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import '../forma/forma.css'

const PORUKE = {
	serverNeOdgovara: 'Lozinka nije promijenjena, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Lozinka nije promijenjena zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Link ne postoji, već je iskorišten ili je istekao: isti link opet ne bi prošao
const KONACNE = [404, 409, 410]

// Stranica /nova-lozinka#token=... iz maila. Van čuvara, jer je otvara i neko ko je prijavljen na
// drugom uređaju. Token je iza #, pa ne ide serveru u adresi ni u log.
export function NovaLozinka() {
	const { hash } = useLocation()
	const token = new URLSearchParams(hash.slice(1)).get('token')
	const [lozinka, setLozinka] = useState('')
	const [slaba, setSlaba] = useState(null)
	const promjena = useMutation({ mutationFn: () => postaviNovuLozinku(token, lozinka) })
	const poPoljima = greskePoPoljima(promjena.error)

	if (!token) {
		return (
			<section>
				<h1>Nova lozinka</h1>
				<p>Link nije potpun, nedostaje mu dio iza znaka #. Otvori ga ponovo iz maila ili zatraži novi.</p>
				<Link to="/zaboravljena-lozinka">Zatraži novi link</Link>
			</section>
		)
	}

	if (promjena.isSuccess) {
		return (
			<section>
				<h1>Nova lozinka</h1>
				<div className="forma">
					<p role="status">Lozinka je promijenjena. Prijave na drugim uređajima su odjavljene.</p>
					<Link to="/prijava">Prijavi se</Link>
				</div>
			</section>
		)
	}

	const konacna = promjena.error instanceof ApiError && KONACNE.includes(promjena.error.status)
	return (
		<section>
			<h1>Nova lozinka</h1>
			<form
				className="forma"
				noValidate
				onSubmit={(d) => {
					d.preventDefault()
					const problem = problemLozinke(lozinka)
					setSlaba(problem)
					if (!problem) promjena.mutate()
				}}
			>
				{promjena.isError && !poPoljima.password && (
					<p role="alert" className="greska-forme">
						{porukaGreske(promjena.error, PORUKE)}
					</p>
				)}
				{konacna ? (
					<Link to="/zaboravljena-lozinka">Zatraži novi link</Link>
				) : (
					<>
						<Polje
							oznaka="Nova lozinka"
							ime="password"
							tip="password"
							autoComplete="new-password"
							vrijednost={lozinka}
							promjena={setLozinka}
							greska={slaba ?? poPoljima.password}
							napomena={NAPOMENA_LOZINKE}
						/>
						<button className="dugme" type="submit" disabled={promjena.isPending}>
							{promjena.isPending ? 'Mijenja se' : 'Postavi lozinku'}
						</button>
					</>
				)}
			</form>
		</section>
	)
}
