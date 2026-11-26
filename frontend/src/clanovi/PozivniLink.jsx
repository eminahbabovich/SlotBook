import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { Link } from 'react-router'
import { napraviPozivniLink, pozivniLinkovi, povuciPozivniLink } from '../api/clanovi.js'
import { Polje } from '../forma/Polje.jsx'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import { QrSlika } from '../qr/QrSlika.jsx'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { opisTrenutka } from '../vrijeme/opis.js'
import { kljucPozivnihLinkova } from './kljucevi.js'
import '../forma/forma.css'
import './clanovi.css'

const PORUKE = {
	serverNeOdgovara: 'Pozivni linkovi se nisu učitali, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Pozivni linkovi se nisu učitali zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_IZMJENE = {
	serverNeOdgovara: 'Promjena nije sačuvana, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Promjena nije sačuvana zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Jedan link za plakat ili grupni chat: ko ga otvori, ulazi kao klijent. Za providera i admina
// ostaje pozivnica na email, jer bi link koji daje veća prava bio opasan kad ga neko proslijedi.
export function PozivniLink({ organizacija }) {
	const { data: linkovi, isPending, isError, error, refetch } = useQuery({
		queryKey: kljucPozivnihLinkova(organizacija.id),
		queryFn: () => pozivniLinkovi(organizacija.id),
	})

	if (isPending) return <Ucitava tekst="Učitavaju se pozivni linkovi" />
	if (isError) return <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />

	const aktivan = linkovi.find((l) => l.aktivan)
	const stari = linkovi.filter((l) => l !== aktivan)
	return (
		<section className="pozivni-link" aria-labelledby="pozivni-link-naslov">
			<h2 id="pozivni-link-naslov">Pozivni link</h2>
			{aktivan ? (
				<AktivanLink link={aktivan} organizacija={organizacija} />
			) : (
				<p className="napomena-blok">
					Nema aktivnog linka. Napravi ga, pa ga pošalji u grupu ili odštampaj plakat s QR kodom. Ko ga otvori, ulazi kao
					klijent.
				</p>
			)}
			<NoviLink organizacija={organizacija} imaAktivan={aktivan !== undefined} />
			{stari.length > 0 && (
				<details className="stari-linkovi">
					<summary>Raniji linkovi ({stari.length})</summary>
					<ul>
						{stari.map((l) => (
							<li key={l.id}>
								{stanjeLinka(l)}, ušlo {l.iskoristeno} od {l.mjesta}
							</li>
						))}
					</ul>
				</details>
			)}
		</section>
	)
}

function stanjeLinka(l) {
	if (l.aktivan) return 'Još radi'
	if (l.povucen) return 'Povučen'
	return l.iskoristeno >= l.mjesta ? 'Popunjen' : 'Istekao'
}

function AktivanLink({ link, organizacija }) {
	const polje = useId()
	const queryClient = useQueryClient()
	const [kopirano, setKopirano] = useState(null)
	const [pitaj, setPitaj] = useState(false)
	const povlacenje = useMutation({
		mutationFn: () => povuciPozivniLink(organizacija.id, link.id),
		onSettled: () => {
			setPitaj(false)
			queryClient.invalidateQueries({ queryKey: kljucPozivnihLinkova(organizacija.id) })
		},
	})

	async function kopiraj() {
		try {
			await navigator.clipboard.writeText(link.url)
			setKopirano('Link je kopiran.')
		} catch {
			// Clipboard API radi samo na HTTPS-u i uz dozvolu browsera
			setKopirano('Kopiranje nije uspjelo. Označi link u polju i kopiraj ga ručno.')
		}
	}

	return (
		<div className="aktivan-link">
			<figure className="qr-linka">
				<QrSlika tekst={link.url} opis={`QR kod za ulazak u ${organizacija.naziv}`} />
			</figure>
			<div className="aktivan-link-podaci">
				<p className="brojac-linka">
					<strong>{link.iskoristeno}</strong> od {link.mjesta} mjesta iskorišteno
				</p>
				<p className="napomena">Važi do {opisTrenutka(link.vaziDo, organizacija.zona)}.</p>
				<div className="polje">
					<label htmlFor={polje}>Link</label>
					<input id={polje} type="text" readOnly value={link.url} onFocus={(e) => e.target.select()} />
				</div>
				<div className="radnje-linka">
					<button className="dugme" type="button" onClick={kopiraj}>
						Kopiraj link
					</button>
					<Link to={`/o/${organizacija.slug}/plakat`}>Odštampaj plakat</Link>
					<span aria-live="polite">{kopirano}</span>
				</div>
				{povlacenje.isError && (
					<p role="alert" className="greska-forme">
						{porukaGreske(povlacenje.error, PORUKE_IZMJENE)}
					</p>
				)}
				{pitaj ? (
					<div className="potvrda-dugmad" role="group" aria-label="Povlačenje linka">
						<p className="potvrda-pitanje">Povući link? Plakat i poslani link prestaju raditi, a članovi ostaju.</p>
						<button className="dugme" type="button" onClick={() => povlacenje.mutate()} disabled={povlacenje.isPending}>
							{povlacenje.isPending ? 'Link se povlači' : 'Povuci link'}
						</button>
						<button className="dugme-tekst" type="button" onClick={() => setPitaj(false)}>
							Ne
						</button>
					</div>
				) : (
					<button className="dugme-tekst" type="button" onClick={() => setPitaj(true)}>
						Povuci link
					</button>
				)}
			</div>
		</div>
	)
}

function NoviLink({ organizacija, imaAktivan }) {
	const queryClient = useQueryClient()
	const [mjesta, setMjesta] = useState('50')
	const [dana, setDana] = useState('30')
	const pravljenje = useMutation({
		mutationFn: () => napraviPozivniLink(organizacija.id, { mjesta: Number(mjesta), dana: Number(dana) }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: kljucPozivnihLinkova(organizacija.id) }),
	})
	const poPoljima = greskePoPoljima(pravljenje.error)

	function napravi(dogadjaj) {
		dogadjaj.preventDefault()
		pravljenje.mutate()
	}

	return (
		<form className="forma forma-u-redu" onSubmit={napravi} noValidate>
			{pravljenje.isError && !poPoljima.maxUses && !poPoljima.days && (
				<p role="alert" className="greska-forme">
					{porukaGreske(pravljenje.error, PORUKE_IZMJENE)}
				</p>
			)}
			<Polje oznaka="Koliko ljudi može ući" ime="maxUses" tip="number" vrijednost={mjesta} promjena={setMjesta} greska={poPoljima.maxUses} napomena="Od 1 do 1000." />
			<Polje oznaka="Važi dana" ime="days" tip="number" vrijednost={dana} promjena={setDana} greska={poPoljima.days} napomena="Od 1 do 90." />
			<button className="dugme" type="submit" disabled={pravljenje.isPending}>
				{pravljenje.isPending ? 'Link se pravi' : imaAktivan ? 'Napravi novi link' : 'Napravi link'}
			</button>
			{imaAktivan && <p className="napomena">Stari link i dalje radi dok ga ne povučeš.</p>}
		</form>
	)
}
