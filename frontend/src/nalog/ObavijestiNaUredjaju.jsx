import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../api/apiFetch.js'
import { obrisiPretplatu, probnaObavijest, pushNaServeru, sacuvajPretplatu } from '../api/push.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { dozvola, naIPhoneu, opisUredjaja, podrzava, pretplati, trenutnaPretplata } from '../obavijesti/uredjaj.js'

const PORUKE = {
	serverNeOdgovara: 'Postavke obavijesti se nisu učitale, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Postavke obavijesti se nisu učitale zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_RADNJE = {
	serverNeOdgovara: 'Server ne odgovara, pa nije jasno da li je promjena stigla. Pokušaj ponovo.',
	opsta: 'Nije uspjelo zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const IPHONE =
	'Na iPhoneu i iPadu obavijesti rade samo kad je SlotBook dodan na početni ekran: u Safariju Dijeli, pa Dodaj na početni ekran (iOS 16.4 ili noviji). Onda otvori SlotBook s početnog ekrana i uključi ih ovdje.'

const KLJUC = ['pushUredjaj']

// Obavijesti se uključuju po uređaju: telefon može imati, a računar na poslu ne
export function ObavijestiNaUredjaju() {
	return (
		<section className="forma nalog-dio">
			<h2>Obavijesti na ovom uređaju</h2>
			<p>
				Na telefon stižu ponuda s liste čekanja, potvrda ili odbijanje rezervacije i podsjetnik dan prije termina. Mailovi
				stižu kao i do sada.
			</p>
			{podrzava() ? (
				<Postavke />
			) : (
				<p className="napomena">{naIPhoneu() ? IPHONE : 'Ovaj browser ne podržava obavijesti. Mailovi stižu kao i do sada.'}</p>
			)}
		</section>
	)
}

function Postavke() {
	const queryClient = useQueryClient()
	const server = useQuery({ queryKey: ['pushNaServeru'], queryFn: pushNaServeru, staleTime: Infinity })
	// Ako browser već ima pretplatu, server je dobije ponovo pod nalogom koji je sad prijavljen. Tako
	// obavijesti osobe koja je prije koristila ovaj browser ovdje više ne stižu.
	const uredjaj = useQuery({
		queryKey: KLJUC,
		enabled: server.data?.ukljucen === true,
		queryFn: async () => {
			const pretplata = await trenutnaPretplata()
			if (pretplata) await sacuvajPretplatu(pretplata, opisUredjaja())
			return pretplata
		},
	})
	const [poruka, setPoruka] = useState(null)

	const ukljuci = useMutation({
		mutationFn: async () => {
			const pretplata = await pretplati(server.data.javniKljuc)
			if (pretplata) await sacuvajPretplatu(pretplata, opisUredjaja())
			return pretplata
		},
		onSuccess: (pretplata) => {
			setPoruka(pretplata ? 'Obavijesti su uključene na ovom uređaju.' : null)
			queryClient.setQueryData(KLJUC, pretplata)
		},
	})
	const iskljuci = useMutation({
		mutationFn: async () => {
			const pretplata = uredjaj.data
			await obrisiPretplatu(pretplata.endpoint)
			await pretplata.unsubscribe()
		},
		onSuccess: () => {
			setPoruka('Obavijesti su isključene na ovom uređaju.')
			queryClient.setQueryData(KLJUC, null)
		},
	})
	const proba = useMutation({
		mutationFn: () => probnaObavijest(uredjaj.data.endpoint),
		onSuccess: () => setPoruka('Probna obavijest je poslana. Ako je ne vidiš, provjeri da obavijesti za browser nisu utišane.'),
	})
	const radnja = [ukljuci, iskljuci, proba].find((m) => m.isError)
	const radi = ukljuci.isPending || iskljuci.isPending || proba.isPending

	if (server.isPending || (server.data?.ukljucen && uredjaj.isPending)) return <Ucitava tekst="Učitavaju se obavijesti" />
	const greska = server.isError ? server : uredjaj.isError ? uredjaj : null
	if (greska) return <NijeUcitano poruka={porukaGreske(greska.error, PORUKE)} ponovo={() => greska.refetch()} />
	if (!server.data.ukljucen) {
		return <p className="napomena">Obavijesti na telefonu trenutno nisu uključene na serveru. Mailovi stižu kao i do sada.</p>
	}

	const ukljucene = Boolean(uredjaj.data)
	const blokirane = !ukljucene && dozvola() === 'denied'
	return (
		<>
			{blokirane ? (
				<p className="napomena">Obavijesti za SlotBook su blokirane u postavkama browsera. Dozvoli ih tamo, pa se vrati ovdje.</p>
			) : (
				<p>{ukljucene ? 'Uključene su na ovom uređaju.' : 'Isključene su na ovom uređaju.'}</p>
			)}
			{ukljuci.isSuccess && ukljuci.data === null && (
				<p className="napomena">Browser nije dobio dozvolu za obavijesti, pa ostaju isključene.</p>
			)}
			{radnja && (
				<p role="alert" className="greska-forme">
					{/* Greška koja nije od servera dolazi od browsera ili njegovog push servisa */}
					{radnja.error instanceof ApiError ? porukaGreske(radnja.error, PORUKE_RADNJE) : 'Browser nije uspio uključiti obavijesti. Pokušaj ponovo.'}
				</p>
			)}
			<div className="radnje-linka">
				{!ukljucene && !blokirane && (
					<button className="dugme" type="button" onClick={() => ukljuci.mutate()} disabled={radi}>
						{ukljuci.isPending ? 'Uključuje se' : 'Uključi obavijesti'}
					</button>
				)}
				{ukljucene && (
					<>
						<button className="dugme" type="button" onClick={() => proba.mutate()} disabled={radi}>
							{proba.isPending ? 'Šalje se' : 'Pošalji probnu obavijest'}
						</button>
						<button className="dugme-tekst" type="button" onClick={() => iskljuci.mutate()} disabled={radi}>
							Isključi obavijesti
						</button>
					</>
				)}
			</div>
			{/* Uvijek u stranici, da čitač ekrana pročita poruku kad se pojavi */}
			<p role="status">{poruka}</p>
			{naIPhoneu() && <p className="napomena">{IPHONE}</p>}
		</>
	)
}
