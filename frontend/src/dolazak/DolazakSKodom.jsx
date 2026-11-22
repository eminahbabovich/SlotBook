import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../api/apiFetch.js'
import { upisiDolazakSKodom } from '../api/rezervacije.js'
import { greskePoPoljima, porukaGreske, serverNeOdgovara } from '../forma/porukaGreske.js'
import { STATUS } from '../rezervacije/status.js'
import '../forma/forma.css'

const PORUKE = {
	serverNeOdgovara: 'Nije jasno da li je dolazak upisan, server ne odgovara. Pokušaj ponovo: drugi put se neće upisati dvaput.',
	opsta: 'Dolazak nije upisan zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Drugi klik poslije pada mreže dobije 409 sa trenutniStatus COMPLETED. Prvi je tada prošao.
async function upisiJednom(rezervacijaId, kod) {
	try {
		return await upisiDolazakSKodom(rezervacijaId, kod)
	} catch (greska) {
		if (greska instanceof ApiError && greska.status === 409 && greska.problem?.trenutniStatus === 'COMPLETED') {
			return { id: rezervacijaId, status: 'COMPLETED' }
		}
		throw greska
	}
}

function poruka(greska) {
	if (!(greska instanceof ApiError)) return PORUKE.opsta
	if (greska.status === 400) return greskePoPoljima(greska).token ?? PORUKE.opsta
	if (greska.status === 403) return 'Ovu rezervaciju ne vodiš ti, pa dolazak može upisati samo osoba koja vodi termin.'
	if (greska.status === 409 && greska.problem?.trenutniStatus) {
		return `Dolazak se ne može upisati, rezervacija je ${STATUS[greska.problem.trenutniStatus] ?? greska.problem.trenutniStatus}.`
	}
	return porukaGreske(greska, PORUKE)
}

// Stranica /check-in/{id}#k=... iz QR koda klijenta. Kamera telefona otvori link, a dolazak
// se upisuje tek na klik: link koji sam nešto mijenja bi okinuo i pregled linka u chatu.
export function DolazakSKodom() {
	const { rezervacijaId } = useParams()
	const { hash } = useLocation()
	const kod = new URLSearchParams(hash.slice(1)).get('k')
	const queryClient = useQueryClient()

	const upis = useMutation({
		mutationFn: () => upisiJednom(rezervacijaId, kod),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ['raspored'] }),
	})

	if (!kod) {
		return (
			<section>
				<h1>Dolazak</h1>
				<p>Link nije potpun, nedostaje mu kod iza znaka #. Skeniraj QR kod ponovo.</p>
				<Link to="/">Tvoje organizacije</Link>
			</section>
		)
	}

	if (upis.isSuccess) {
		return (
			<section>
				<h1>Dolazak</h1>
				<p role="status">
					Dolazak je upisan, rezervacija je završena.
				</p>
				<Link to="/">Tvoje organizacije</Link>
			</section>
		)
	}

	const konacna = upis.error instanceof ApiError && [400, 403, 404, 409].includes(upis.error.status)
	return (
		<section>
			<h1>Dolazak</h1>
			<p>Klijent je pokazao QR kod svoje rezervacije. Upiši dolazak kad je osoba tu.</p>
			<div className="forma">
				{upis.isError && (
					<p role="alert" className="greska-forme">
						{poruka(upis.error)}
					</p>
				)}
				{!konacna && (
					<button className="dugme" type="button" onClick={() => upis.mutate()} disabled={upis.isPending}>
						{upis.isPending ? 'Upis u toku' : serverNeOdgovara(upis.error) ? 'Pokušaj ponovo' : 'Upiši dolazak'}
					</button>
				)}
			</div>
		</section>
	)
}
