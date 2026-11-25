import { useState } from 'react'
import { postaviTemu, sacuvanaTema, sistemJeTaman } from './tema.js'
import './tema.css'

// Prekidač (role="switch"): uključen znači tamna tema. Prvi klik uvijek izabere suprotno od onoga
// što se sad vidi, pa i kad tema dolazi od sistema.
export function PrekidacTeme() {
	const [tamna, setTamna] = useState(() => (sacuvanaTema() ?? (sistemJeTaman() ? 'tamna' : 'svijetla')) === 'tamna')

	function prebaci() {
		const nova = !tamna
		const promijeni = () => {
			postaviTemu(nova ? 'tamna' : 'svijetla')
			setTamna(nova)
		}
		// Promjena teme preko View Transitions: cijeli ekran pređe odjednom, umjesto da svaka boja
		// klizi svojim tempom
		if (document.startViewTransition && !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
			document.startViewTransition(promijeni)
		} else {
			promijeni()
		}
	}

	return (
		<button type="button" role="switch" aria-checked={tamna} className="prekidac-teme" onClick={prebaci}>
			<span className="skriveno">Tamna tema</span>
			<span className="prekidac-staza" aria-hidden="true">
				<span className="prekidac-zeton">
					<svg viewBox="0 0 24 24" className="prekidac-ikona">
						{tamna ? (
							<path d="M15.5 3.5a8.5 8.5 0 1 0 5 15 7 7 0 0 1-5-15Z" />
						) : (
							<>
								<circle cx="12" cy="12" r="4.5" />
								<path d="M12 1.5v3M12 19.5v3M1.5 12h3M19.5 12h3M4.6 4.6l2.1 2.1M17.3 17.3l2.1 2.1M4.6 19.4l2.1-2.1M17.3 6.7l2.1-2.1" />
							</>
						)}
					</svg>
				</span>
			</span>
		</button>
	)
}
