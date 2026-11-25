import { useRef } from 'react'
import { danUZoni, nazivDana, satUZoni } from '../vrijeme/vrijeme.js'
import { brojKarte } from './brojKarte.js'
import './karta.css'

// Karta: lijevo put od početka do kraja termina, desno kupon odvojen perforacijom. HTML, ne slika,
// da je čita i čitač ekrana. Bez ID-ja je karta u nastajanju (potvrda), pa je div.
// nagib: karta se nagne prema kursoru ili prstu, a folija prelije svjetlo po nagibu.
export function Karta({ id, pocetak, kraj, zona, resurs, mjesto, zig, kupon, nagib = false, className = '', children }) {
	const Oznaka = id ? 'article' : 'div'
	const nagibProps = useNagib(nagib)
	return (
		<Oznaka className={`karta ${nagib ? 'karta-nagib' : ''} ${className}`} aria-label={id ? `Karta ${brojKarte(id)}` : undefined} {...nagibProps}>
			<div className="karta-tijelo">
				<div className="karta-glavno">
					{/* Bez imena aplikacije: na listi od šest karata bilo bi šest puta isto, a ime je u zaglavlju */}
					<p className="karta-zaglavlje">{id ? `Karta № ${brojKarte(id)}` : 'Nova karta'}</p>
					<p className="karta-dan">{nazivDana(danUZoni(pocetak, zona))}</p>
					<p className="karta-put">
						<time dateTime={pocetak}>{satUZoni(pocetak, zona)}</time>
						<span className="karta-linija" aria-hidden="true" />
						<span className="skriveno"> do </span>
						<time dateTime={kraj}>{satUZoni(kraj, zona)}</time>
					</p>
					{resurs && <p className="karta-resurs">{resurs}</p>}
					{mjesto && <p className="karta-mjesto">{mjesto}</p>}
					{children}
				</div>
				{(zig || kupon) && (
					<div className="karta-kupon">
						{zig && <p className="zig">{zig}</p>}
						{kupon}
					</div>
				)}
				<span className="karta-folija" aria-hidden="true" />
			</div>
		</Oznaka>
	)
}

// Nagib preko CSS varijabli, bez React stanja: pomjeranje miša ne smije crtati komponentu ispočetka.
// Na telefonu radi na dodir (pointer događaji), bez dozvole za žiroskop, koju iOS traži posebno.
function useNagib(ukljucen) {
	const okvir = useRef(null)
	if (!ukljucen) return {}

	function nagni(dogadjaj) {
		const el = dogadjaj.currentTarget
		const r = el.getBoundingClientRect()
		const x = (dogadjaj.clientX - r.left) / r.width - 0.5
		const y = (dogadjaj.clientY - r.top) / r.height - 0.5
		el.style.setProperty('--nx', x.toFixed(3))
		el.style.setProperty('--ny', y.toFixed(3))
		el.dataset.nagnuta = ''
	}

	function vrati(dogadjaj) {
		const el = dogadjaj.currentTarget
		el.style.setProperty('--nx', '0')
		el.style.setProperty('--ny', '0')
		delete el.dataset.nagnuta
	}

	return { ref: okvir, onPointerMove: nagni, onPointerDown: nagni, onPointerLeave: vrati, onPointerCancel: vrati, onPointerUp: (d) => d.pointerType !== 'mouse' && vrati(d) }
}
