import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { pratiTermine } from '../api/termini.js'

const PRVA_PAUZA = 1000
const NAJDUZA_PAUZA = 30_000

function cekaj(ms, signal) {
	return new Promise((gotovo) => {
		if (signal.aborted) return gotovo()
		const sat = setTimeout(gotovo, ms)
		signal.addEventListener('abort', () => {
			clearTimeout(sat)
			gotovo()
		})
	})
}

// Drži otvoren SSE tok za resurs dok je kalendar otvoren. Na signal ponovo učita termine kroz
// /slots. Kad veza pukne, spaja se ponovo, s pauzom koja raste do 30 s, da server koji ne radi
// ne dobija zahtjev svake sekunde. Vraća tekst za čitač ekrana.
export function useZivKalendar(resursId) {
	const queryClient = useQueryClient()
	const [najava, setNajava] = useState('')

	useEffect(() => {
		const prekid = new AbortController()
		const osvjezi = () => queryClient.invalidateQueries({ queryKey: ['kalendar', resursId] })

		async function drziVezu() {
			let pauza = PRVA_PAUZA
			let prvaVeza = true
			while (!prekid.signal.aborted) {
				try {
					await pratiTermine(resursId, {
						signal: prekid.signal,
						naSpajanje: () => {
							// Dok veze nije bilo, promjene nisu stizale, pa se termini učitaju ponovo
							if (!prvaVeza) osvjezi()
							prvaVeza = false
							pauza = PRVA_PAUZA
						},
						naPromjenu: () => {
							osvjezi()
							setNajava('Termini su osvježeni.')
						},
					})
				} catch {
					pauza = Math.min(pauza * 2, NAJDUZA_PAUZA)
				}
				await cekaj(pauza, prekid.signal)
			}
		}

		drziVezu()
		return () => prekid.abort()
	}, [resursId, queryClient])

	return najava
}
