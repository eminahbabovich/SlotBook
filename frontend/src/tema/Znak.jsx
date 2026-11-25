// Znak SlotBooka: mjeden žeton s prorezom, kao žeton za garderobu ili automat. Boje dolaze iz
// tokena preko CSS-a (stop-color), pa znak prati temu.
export function Znak({ className = 'znak-zeton' }) {
	return (
		<svg viewBox="0 0 32 32" className={className} aria-hidden="true">
			<defs>
				<linearGradient id="znak-mjed" x1="0" y1="0" x2="1" y2="1">
					<stop offset="0" className="znak-sjaj" />
					<stop offset="0.45" className="znak-mjed" />
					<stop offset="1" className="znak-sjena" />
				</linearGradient>
			</defs>
			<circle cx="16" cy="16" r="15" fill="url(#znak-mjed)" />
			<circle cx="16" cy="16" r="11" fill="none" className="znak-rub" strokeWidth="1.5" />
			<rect x="9" y="14" width="14" height="4" rx="2" className="znak-prorez" />
		</svg>
	)
}
