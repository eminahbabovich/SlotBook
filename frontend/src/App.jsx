import { useEffect, useState } from 'react'

// Privremena stranica za prvi zadatak Faze 7: da li sva tri fonta iz docs/DIZAJN.md
// stvarno crtaju naša slova u browseru. Zamijenit će je prvi pravi ekran.

const PROBNI_TEKST = 'Čitaonica PMF, đački termin, ž'
const NASA_SLOVA = 'Č č Ć ć Š š Đ đ Ž ž'

const FONTOVI = [
	{ naziv: 'Big Shoulders Display', familija: 'Big Shoulders Display Variable', velicina: '--velicina-naslov-2', tezina: 700 },
	{ naziv: 'IBM Plex Sans', familija: 'IBM Plex Sans Variable', velicina: '--velicina-tekst', tezina: 400 },
	{ naziv: 'IBM Plex Mono', familija: 'IBM Plex Mono', velicina: '--velicina-vrijeme', tezina: 400 },
]

// Slovo koje font nema browser crta rezervnim fontom. Zato se svako slovo izmjeri dvaput,
// jednom s rezervnim monospace, jednom sa serif. Ako font ima slovo, širina je ista. Ako
// nema, širine su različite. unicode-range iz CSS-a nije dovoljan dokaz: fajl bez njega
// se prijavi za sva slova, a u sebi ih nema.
async function slovaBezFonta(font) {
	// Canvas ne razumije CSS varijable, a za poređenje širina je veličina svejedno.
	const opis = `${font.tezina} 32px "${font.familija}"`
	await document.fonts.load(opis, NASA_SLOVA)
	const platno = document.createElement('canvas').getContext('2d')
	if (platno === null) return ['canvas nije dostupan']

	const sirina = (slovo, rezervni) => {
		platno.font = `${opis}, ${rezervni}`
		return platno.measureText(slovo).width
	}
	return [...NASA_SLOVA.replaceAll(' ', '')].filter((slovo) => sirina(slovo, 'monospace') !== sirina(slovo, 'serif'))
}

function ProbaFonta({ font }) {
	const [rezultat, setRezultat] = useState('provjerava se')

	useEffect(() => {
		let ugasen = false
		slovaBezFonta(font).then((fale) => {
			if (!ugasen) setRezultat(fale.length === 0 ? 'sva slova iz fonta' : `fale: ${fale.join(' ')}`)
		})
		return () => {
			ugasen = true
		}
	}, [font])

	const stil = { fontFamily: `"${font.familija}", monospace`, fontSize: `var(${font.velicina})`, fontWeight: font.tezina }

	return (
		<section>
			<h2>{font.naziv}</h2>
			<p style={stil}>{PROBNI_TEKST}</p>
			<p style={stil}>{NASA_SLOVA}</p>
			<p data-font={font.naziv}>{rezultat}</p>
		</section>
	)
}

export default function App() {
	return (
		<main>
			<h1>Proba fontova</h1>
			{FONTOVI.map((font) => (
				<ProbaFonta key={font.naziv} font={font} />
			))}
		</main>
	)
}
