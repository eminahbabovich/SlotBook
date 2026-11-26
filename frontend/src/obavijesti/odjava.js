import { obrisiPretplatu } from '../api/push.js'
import { podrzava, pretplataAkoPostoji } from './uredjaj.js'

// Na zajedničkom računaru sljedeća osoba ne smije dobijati obavijesti prethodne, pa odjava ugasi i
// pretplatu ovog browsera. Greška tu ne smije zaustaviti samu odjavu.
export async function ugasiObavijestiNaOdjavi() {
	try {
		if (!podrzava()) return
		const pretplata = await pretplataAkoPostoji()
		if (!pretplata) return
		await obrisiPretplatu(pretplata.endpoint)
		await pretplata.unsubscribe()
	} catch {
		// Pretplata ostaje, a server je briše čim push servis javi 410 ili se ovdje prijavi neko drugi
	}
}
