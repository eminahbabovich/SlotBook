import { danUZoni, nazivDana, satUZoni } from '../vrijeme/vrijeme.js'
import { brojKarte } from './brojKarte.js'
import './karta.css'

// Karta za voz: lijevo put od početka do kraja termina, desno kupon odvojen perforacijom.
// HTML, ne slika, da je čita i čitač ekrana. Bez ID-ja je karta u nastajanju (potvrda), pa je div.
export function Karta({ id, pocetak, kraj, zona, resurs, mjesto, zig, kupon, className = '', children }) {
	const Oznaka = id ? 'article' : 'div'
	return (
		<Oznaka className={`karta ${className}`} aria-label={id ? `Karta ${brojKarte(id)}` : undefined}>
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
		</Oznaka>
	)
}
