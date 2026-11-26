import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { pratiTermine } from '../api/termini.js'
import { drziVezu } from '../api/tok.js'

// Drži otvoren SSE tok za resurs dok je kalendar otvoren. Na signal ponovo učita termine kroz
// /slots. Ponovno spajanje i pauze su u drziVezu. Vraća tekst za čitač ekrana.
export function useZivKalendar(resursId) {
	const queryClient = useQueryClient()
	const [najava, setNajava] = useState('')

	useEffect(() => {
		const prekid = new AbortController()
		const osvjezi = () => queryClient.invalidateQueries({ queryKey: ['kalendar', resursId] })
		drziVezu(
			(opcije) => pratiTermine(resursId, opcije),
			{
				naPonovnoSpajanje: osvjezi,
				naPromjenu: () => {
					osvjezi()
					setNajava('Termini su osvježeni.')
				},
			},
			prekid.signal,
		)
		return () => prekid.abort()
	}, [resursId, queryClient])

	return najava
}
