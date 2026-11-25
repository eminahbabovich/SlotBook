import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { adresaKalendara, linkZaKalendar, noviLinkZaKalendar, ugasiLinkZaKalendar } from '../api/nalog.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { opisTrenutka } from '../vrijeme/opis.js'
import '../forma/forma.css'

const PORUKE = {
	serverNeOdgovara: 'Link za kalendar se nije učitao, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Link za kalendar se nije učitao zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_RADNJE = {
	serverNeOdgovara: 'Server ne odgovara, pa nije jasno da li je promjena stigla. Pokušaj ponovo.',
	opsta: 'Nije uspjelo zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Link pripada korisniku, ne organizaciji, pa je datum po zoni browsera, kao kod izvoza
const ZONA = Intl.DateTimeFormat().resolvedOptions().timeZone

export function PretplataNaKalendar() {
	const queryClient = useQueryClient()
	const stanje = useQuery({ queryKey: ['linkZaKalendar'], queryFn: linkZaKalendar })
	// Token stiže samo u odgovoru na pravljenje. Poslije osvježavanja stranice ga nema ni ovdje ni u bazi.
	const [token, setToken] = useState(null)

	const novi = useMutation({
		mutationFn: noviLinkZaKalendar,
		onSuccess: (link) => {
			setToken(link.token)
			queryClient.setQueryData(['linkZaKalendar'], { aktivan: true, napravljen: link.napravljen })
		},
	})
	const gasenje = useMutation({
		mutationFn: ugasiLinkZaKalendar,
		onSuccess: () => {
			setToken(null)
			queryClient.setQueryData(['linkZaKalendar'], { aktivan: false, napravljen: null })
		},
	})
	const radnja = novi.isError ? novi : gasenje.isError ? gasenje : null
	const radi = novi.isPending || gasenje.isPending

	let sadrzaj
	if (stanje.isPending) {
		sadrzaj = <Ucitava tekst="Učitava se link za kalendar" />
	} else if (stanje.isError) {
		sadrzaj = <NijeUcitano poruka={porukaGreske(stanje.error, PORUKE)} ponovo={() => stanje.refetch()} />
	} else if (token !== null) {
		sadrzaj = <NoviLink adresa={adresaKalendara(token, window.location)} />
	} else if (stanje.data.aktivan) {
		sadrzaj = (
			<p>
				Link je napravljen: {opisTrenutka(stanje.data.napravljen, ZONA)}. Ne možemo ga ponovo prikazati. Ako ti treba
				opet ili ga je vidio neko drugi, napravi novi, a stari tada prestaje raditi.
			</p>
		)
	} else {
		sadrzaj = null
	}

	return (
		<section className="forma nalog-dio">
			<h2>Kalendar na telefonu</h2>
			<p>
				Tvoje rezervacije se mogu same pojaviti u Google kalendaru, na iPhoneu ili u Outlooku. Napraviš link i dodaš ga u
				kalendar kao pretplatu. Kalendar ga onda sam osvježava, a otkazan termin nestane pri sljedećem osvježavanju.
				Koliko često osvježava, odlučuje aplikacija za kalendar, ne SlotBook.
			</p>
			{sadrzaj}
			{radnja && (
				<p role="alert" className="greska-forme">
					{porukaGreske(radnja.error, PORUKE_RADNJE)}
				</p>
			)}
			{stanje.isSuccess && (
				<div className="radnje-linka">
					{/* Kad link postoji, novi je sporedna radnja: glavna je kopiranje ili ništa */}
					<button className={stanje.data.aktivan ? 'dugme-tekst' : 'dugme'} type="button" onClick={() => novi.mutate()} disabled={radi}>
						{novi.isPending ? 'Link se pravi' : stanje.data.aktivan ? 'Napravi novi link' : 'Napravi link'}
					</button>
					{stanje.data.aktivan && (
						<button className="dugme-tekst" type="button" onClick={() => gasenje.mutate()} disabled={radi}>
							Ugasi link
						</button>
					)}
				</div>
			)}
		</section>
	)
}

function NoviLink({ adresa }) {
	const polje = useId()
	const [kopirano, setKopirano] = useState(null)

	async function kopiraj() {
		try {
			await navigator.clipboard.writeText(adresa)
			setKopirano('Link je kopiran.')
		} catch {
			// Clipboard API radi samo na HTTPS-u i uz dozvolu browsera
			setKopirano('Kopiranje nije uspjelo. Označi link u polju i kopiraj ga ručno.')
		}
	}

	return (
		<>
			<div className="polje">
				<label htmlFor={polje}>Tvoj link za kalendar</label>
				<input id={polje} type="text" readOnly value={adresa} onFocus={(e) => e.target.select()} />
				<p className="napomena">
					Link vidiš samo sada. Ko ga ima, vidi tvoje termine, zato ga ne dijeli. U Google kalendaru: Ostali kalendari,
					pa Dodaj s URL-a.
				</p>
			</div>
			<div className="radnje-linka">
				<button className="dugme" type="button" onClick={kopiraj}>
					Kopiraj link
				</button>
				<a href={adresa.replace(/^https?:/, 'webcal:')}>Otvori u aplikaciji za kalendar</a>
				{/* U istom redu kao dugme, a ne kao prazan pasus koji bi u gridu dodao razmak */}
				<span aria-live="polite">{kopirano}</span>
			</div>
		</>
	)
}
