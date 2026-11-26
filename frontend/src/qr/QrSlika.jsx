import { create } from 'qrcode'

// Moduli se crtaju kao SVG kvadrati u boji teksta, pa boja dolazi iz tokena, a ne iz biblioteke.
// Okvir od 4 modula oko koda traži standard, inače ga neki čitači ne nađu.
export function QrSlika({ tekst, opis }) {
	const { modules } = create(tekst, { errorCorrectionLevel: 'M' })
	const okvir = 4
	const strana = modules.size + 2 * okvir
	let putanja = ''
	for (let red = 0; red < modules.size; red++) {
		for (let kolona = 0; kolona < modules.size; kolona++) {
			if (modules.get(red, kolona)) putanja += `M${kolona + okvir} ${red + okvir}h1v1h-1z`
		}
	}
	return (
		<svg role="img" aria-label={opis} viewBox={`0 0 ${strana} ${strana}`} shapeRendering="crispEdges">
			<path d={putanja} fill="currentColor" />
		</svg>
	)
}
