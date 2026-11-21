import { useMutation } from '@tanstack/react-query'
import { csvStatistike } from '../api/statistika.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { sacuvajFajl } from '../sacuvajFajl.js'

const PORUKE = {
	serverNeOdgovara: 'Fajl nije preuzet, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Fajl nije preuzet zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Fajl pravi backend (tačka-zarez, BOM, zaštita od formula), a ovdje se samo preda browseru.
// Ime je isto kao u Content-Disposition, da fajl ne zavisi od toga da li browser čita zaglavlje.

export function CsvIzvoz({ orgId, od, doDana }) {
	const preuzimanje = useMutation({
		mutationFn: () => csvStatistike(orgId, od, doDana),
		onSuccess: (fajl) => sacuvajFajl(fajl, `popunjenost-${od}-${doDana}.csv`),
	})

	return (
		<div className="forma">
			{preuzimanje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(preuzimanje.error, PORUKE)}
				</p>
			)}
			<button className="dugme" type="button" onClick={() => preuzimanje.mutate()} disabled={preuzimanje.isPending}>
				{preuzimanje.isPending ? 'Fajl se priprema' : 'Preuzmi popunjenost (CSV)'}
			</button>
		</div>
	)
}
