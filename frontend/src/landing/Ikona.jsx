// Ikone mogućnosti: linije od 1,75 px u boji teksta, na mjedenoj pločici. Crtane rukom na mreži
// 24 × 24, bez biblioteke, jer ih je malo i nijedna se ne koristi van landinga.
const PUTANJE = {
	uzivo: (
		<>
			<rect x="3.5" y="5" width="17" height="15" rx="2.5" />
			<path d="M3.5 9.5h17M8 3v4M16 3v4" />
			<circle cx="17.5" cy="17" r="3.2" fill="currentColor" stroke="none" />
		</>
	),
	// Red ljudi na listi: prvi je označen strelicom, jer se termin nudi njemu
	cekanje: (
		<>
			<path d="M4 4.5l3.2 2.2L4 8.9" />
			<path d="M10.5 6.7h9.5M10.5 12h9.5M10.5 17.3h6" />
			<circle cx="5.4" cy="12" r="1.3" fill="currentColor" stroke="none" />
			<circle cx="5.4" cy="17.3" r="1.3" fill="currentColor" stroke="none" />
		</>
	),
	qr: (
		<>
			<rect x="4" y="4" width="6" height="6" rx="1" />
			<rect x="14" y="4" width="6" height="6" rx="1" />
			<rect x="4" y="14" width="6" height="6" rx="1" />
			<path d="M14 14h2.5v2.5M20 14v6h-6v-3M17 20v0" />
		</>
	),
	statistika: (
		<>
			<path d="M4 20h16M7 16v-4M12 16V7M17 16v-7" />
		</>
	),
	clanovi: (
		<>
			<circle cx="9" cy="8.5" r="3" />
			<path d="M3.5 19c.6-3 2.8-4.8 5.5-4.8s4.9 1.8 5.5 4.8" />
			<circle cx="17" cy="9.5" r="2.4" />
			<path d="M16 14.4c2.3 0 4 1.5 4.5 4.1" />
		</>
	),
	kalendar: (
		<>
			<rect x="3.5" y="5" width="17" height="15" rx="2.5" />
			<path d="M3.5 9.5h17M8 3v4M16 3v4M8.5 14.5l2.2 2.2 4.8-4.7" />
		</>
	),
	// Dvije karike lanca: pozivni link
	link: (
		<>
			<path d="M10 14a4 4 0 0 1 0-5.6l2.4-2.4a4 4 0 0 1 5.6 5.6l-1.2 1.2" />
			<path d="M14 10a4 4 0 0 1 0 5.6l-2.4 2.4a4 4 0 0 1-5.6-5.6l1.2-1.2" />
		</>
	),
}

export function Ikona({ ime }) {
	return (
		<span className="ikona" aria-hidden="true">
			<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
				{PUTANJE[ime]}
			</svg>
		</span>
	)
}
