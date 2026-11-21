// Fajl koji je napravio backend se samo preda browseru (CSV popunjenosti, izvoz mojih podataka).
export function sacuvajFajl(fajl, ime) {
	const adresa = URL.createObjectURL(fajl)
	const veza = document.createElement('a')
	veza.href = adresa
	veza.download = ime
	document.body.append(veza)
	veza.click()
	veza.remove()
	// Odmah poslije klika neki browseri još čitaju fajl, pa se adresa pusti tek u sljedećem krugu
	setTimeout(() => URL.revokeObjectURL(adresa), 0)
}
