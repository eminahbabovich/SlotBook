import { useQuery } from '@tanstack/react-query'
import { mojeBlokade } from '../api/organizacije.js'
import { opisTrenutka } from '../vrijeme/opis.js'
import { KLJUC_BLOKADE } from './kljucevi.js'

// Traka iznad mojih rezervacija kad sam negdje blokiran zbog nedolazaka. Ako se blokade ne učitaju,
// traka se ne prikazuje: backend ionako odbije rezervaciju i kaže zašto, pa ovo nije jedino mjesto.
export function Blokade() {
	const { data } = useQuery({ queryKey: KLJUC_BLOKADE, queryFn: mojeBlokade })
	if (!data?.length) return null
	return (
		<ul className="blokade" aria-label="Blokade zbog nedolazaka">
			{data.map((b) => (
				<li key={b.organizacijaId}>
					<strong>{b.naziv}:</strong> zbog nedolazaka ({b.nedolazaka}) ne možeš rezervisati do {opisTrenutka(b.do, b.zona)}. Blokada prestaje
					sama, a ako je greška, javi se adminu.
				</li>
			))}
		</ul>
	)
}
